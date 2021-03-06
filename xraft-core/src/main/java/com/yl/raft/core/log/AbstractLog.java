package com.yl.raft.core.log;

import com.google.common.eventbus.EventBus;
import com.yl.raft.core.log.entry.*;
import com.yl.raft.core.log.sequence.EntrySequence;
import com.yl.raft.core.log.sequence.GroupConfigEntryList;
import com.yl.raft.core.log.snapshot.*;
import com.yl.raft.core.log.statemachine.StateMachine;
import com.yl.raft.core.log.statemachine.StateMachineContext;
import com.yl.raft.core.node.NodeEndpoint;
import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.message.AppendEntriesRpc;
import com.yl.raft.core.rpc.message.InstallSnapshotRpc;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;

/**
 * AbstractLog
 */
@Slf4j
public abstract class AbstractLog implements Log {

    protected EntrySequence entrySequence;

    protected int commitIndex;

    protected StateMachine stateMachine;

    protected StateMachineContext stateMachineContext;

    protected Snapshot snapshot;

    protected SnapshotBuilder<? extends Snapshot> snapshotBuilder = new NullSnapshotBuilder();

    protected final EventBus eventBus;

    protected GroupConfigEntryList groupConfigEntryList;

    AbstractLog(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public int getNextIndex() {
        return entrySequence.getNextLogIndex();
    }

    @Override
    public int getCommitIndex() {
        return commitIndex;
    }

    @Override
    public EntryMeta getLastEntryMeta() {
        if (entrySequence.isEmpty()) {
            return new EntryMeta(Entry.KIND_NO_OP, snapshot.getLastIncludedIndex(), snapshot.getLastIncludedTerm());
        }
        return entrySequence.getLastEntry().getMeta();
    }

    @Override
    public AppendEntriesRpc createAppendEntriesRpc(int term, NodeId selfId, int nextIndex, int maxEntries) {
        int nextLogIndex = entrySequence.getNextLogIndex();
        if (nextIndex > nextLogIndex) {
            throw new IllegalArgumentException("illegal next index " + nextIndex);
        }

        if (nextIndex <= snapshot.getLastIncludedIndex()) {
            throw new EntryInSnapshotException(nextIndex);
        }

        AppendEntriesRpc rpc = new AppendEntriesRpc();
        // TODO messageId
        rpc.setMessageId(UUID.randomUUID().toString());
        rpc.setTerm(term);
        rpc.setLeaderId(selfId);
        rpc.setLeaderCommit(commitIndex);

        if (nextIndex == snapshot.getLastIncludedIndex() + 1) {
            rpc.setPrevLogIndex(snapshot.getLastIncludedIndex());
            rpc.setPrevLogTerm(snapshot.getLastIncludedTerm());
        } else {
            // ???????????????????????????
            Entry preEntry = entrySequence.getEntry(nextIndex - 1);
            if (preEntry != null) {
                rpc.setPrevLogIndex(preEntry.getIndex());
                rpc.setPrevLogTerm(preEntry.getTerm());
            }
        }
        if (!entrySequence.isEmpty()) {
            int maxIndex = (maxEntries == ALL_ENTRIES ? nextLogIndex : Math.min(nextLogIndex, nextIndex + maxEntries));
            rpc.setEntries(entrySequence.subList(nextIndex, maxIndex));
        }
        return rpc;
    }

    @Override
    public boolean isNewerThan(int lastLogIndex, int lastLogTerm) {
        EntryMeta lastEntryMeta = getLastEntryMeta();
        log.debug("last entry ({}, {}), candidate ({}, {})",
                lastEntryMeta.getIndex(), lastEntryMeta.getTerm(), lastLogIndex, lastLogTerm);
        // ????????? term ?????????????????????
        return lastEntryMeta.getTerm() > lastLogTerm || lastEntryMeta.getIndex() > lastLogIndex;
    }

    @Override
    public NoOpEntry appendEntry(int term) {
        NoOpEntry noOpEntry = new NoOpEntry(entrySequence.getNextLogIndex(), term);
        entrySequence.append(noOpEntry);
        return noOpEntry;
    }

    @Override
    public GeneralEntry appendEntry(int term, byte[] command) {
        GeneralEntry generalEntry = new GeneralEntry(entrySequence.getNextLogIndex(), term, command);
        entrySequence.append(generalEntry);
        return generalEntry;
    }

    @Override
    public AppendEntriesState appendEntriesFromLeader(int prevLogIndex, int prevLogTerm, List<Entry> leaderEntries) {

        if (!checkIfPreviousLogMatches(prevLogIndex, prevLogTerm)) {
            return AppendEntriesState.FAILED;
        }

        if (leaderEntries.isEmpty()) {
            return AppendEntriesState.SUCCESS;
        }

        // ??????????????????????????????????????????????????????
        // ?????? prevLogIndex ???????????????????????????????????????????????? preLogIndex ????????????????????????
        UnmatchedLogRemovedResult unmatchedLogRemovedResult = removeUnmatchedLog(new EntrySequenceView(leaderEntries));
        // ??????????????????
        GroupConfigEntry lastGroupConfigEntry = appendEntriesFromLeader(unmatchedLogRemovedResult.newEntries);

        // lastGroupConfigEntry != null ???????????????????????????????????????????????????
        // unmatchedLogRemovedResult.getGroup() ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        return new AppendEntriesState(
                lastGroupConfigEntry != null ?
                        lastGroupConfigEntry.getResultNodeEndpoints() :
                        unmatchedLogRemovedResult.getGroup()
        );
    }

    @Override
    public void advanceCommitIndex(int newCommitIndex, int currentTerm) {
        if (!validateNewCommitIndex(newCommitIndex, currentTerm)) {
            return;
        }
        log.debug("advance commit index from {} to {}", commitIndex, newCommitIndex);
        entrySequence.commit(newCommitIndex);
        commitIndex = newCommitIndex;

        // ?????? applyIndex
        advanceApplyIndex();
    }

    private void advanceApplyIndex() {
        int lastApplied = stateMachine.getLastApplied();
        int lastIncludedIndex = snapshot.getLastIncludedIndex();
        if (lastApplied == 0 && lastIncludedIndex > 0) {
            assert commitIndex >= lastIncludedIndex;
            applySnapshot(snapshot);
            lastApplied = lastIncludedIndex;
        }
        for (Entry entry : entrySequence.subList(lastApplied + 1, commitIndex + 1)) {
            applyEntry(entry);
        }
    }

    @Override
    public void setStateMachine(StateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    @Override
    public InstallSnapshotState installSnapshot(InstallSnapshotRpc rpc) {
        if (rpc.getLastIndex() <= snapshot.getLastIncludedIndex()) {
            log.debug("snapshot's last included index from rpc <= current one ({} <= {}), ignore",
                    rpc.getLastIndex(), snapshot.getLastIncludedIndex());
            return new InstallSnapshotState(InstallSnapshotState.StateName.ILLEGAL_INSTALL_SNAPSHOT_RPC);
        }

        // ???????????? 0?????????????????????
        if (rpc.getOffset() == 0) {
            assert rpc.getLastConfig() != null;
            snapshotBuilder.close();
            snapshotBuilder = newSnapshotBuilder(rpc);
        } else {
            // ????????????
            snapshotBuilder.append(rpc);
        }

        // ???????????????????????????
        if (!rpc.isDone()) {
            return new InstallSnapshotState(InstallSnapshotState.StateName.INSTALLING);
        }

        // ????????????????????????
        Snapshot newSnapshot = snapshotBuilder.build();
        applySnapshot(newSnapshot);
        replaceSnapshot(newSnapshot);
        int lastIncludedIndex = snapshot.getLastIncludedIndex();
        if (commitIndex < lastIncludedIndex) {
            commitIndex = lastIncludedIndex;
        }
        return new InstallSnapshotState(InstallSnapshotState.StateName.INSTALLED, newSnapshot.getLastConfig());
    }

    @Override
    public void generateSnapshot(int lastIncludedIndex, Set<NodeEndpoint> groupConfig) {
        log.info("generate snapshot, last included index {}", lastIncludedIndex);
        EntryMeta lastAppliedEntryMeta = entrySequence.getEntryMeta(lastIncludedIndex);
        replaceSnapshot(generateSnapshot(lastAppliedEntryMeta, groupConfig));
    }

    @Override
    public InstallSnapshotRpc createInstallSnapshotRpc(int term, NodeId selfId, int offset, int length) {
        InstallSnapshotRpc rpc = new InstallSnapshotRpc();
        rpc.setTerm(term);
        rpc.setLeaderId(selfId);
        rpc.setLastIndex(snapshot.getLastIncludedIndex());
        rpc.setLastTerm(snapshot.getLastIncludedTerm());
        if (offset == 0) {
            rpc.setLastConfig(snapshot.getLastConfig());
        }
        rpc.setOffset(offset);

        SnapshotChunk chunk = snapshot.readData(offset, length);
        rpc.setData(chunk.getBytes());
        rpc.setDone(chunk.isLastChunk());

        rpc.setMessageId(UUID.randomUUID().toString());
        return rpc;
    }

    @Override
    public Set<NodeEndpoint> getLastGroup() {
        return groupConfigEntryList.getLastGroup();
    }

    @Override
    public AddNodeEntry appendEntryForAddNode(int term, Set<NodeEndpoint> nodeEndpoints, NodeEndpoint newNodeEndpoint) {
        AddNodeEntry entry = new AddNodeEntry(entrySequence.getNextLogIndex(), term, nodeEndpoints, newNodeEndpoint);
        entrySequence.append(entry);
        groupConfigEntryList.add(entry);
        return entry;
    }

    @Override
    public RemoveNodeEntry appendEntryForRemoveNode(int term, Set<NodeEndpoint> nodeEndpoints, NodeId nodeToRemove) {
        RemoveNodeEntry entry = new RemoveNodeEntry(entrySequence.getNextLogIndex(), term, nodeEndpoints, nodeToRemove);
        entrySequence.append(entry);
        groupConfigEntryList.add(entry);
        return entry;
    }

    @Override
    public void close() {
        snapshot.close();
        entrySequence.close();
        snapshotBuilder.close();
        stateMachine.shutdown();
    }

    private void applyEntry(Entry entry) {
        // skip no-op entry and membership-change entry
        if (isApplicable(entry)) {
            Set<NodeEndpoint> lastGroup = groupConfigEntryList.getLastGroupBeforeOrDefault(entry.getIndex());
            stateMachine.applyLog(stateMachineContext, entry.getIndex(), entry.getTerm(), entry.getCommandBytes(), entrySequence.getFirstLogIndex(), lastGroup);
        } else {
            stateMachine.advanceLastApplied(entry.getIndex());
        }
    }

    private boolean isApplicable(Entry entry) {
        return entry.getKind() == Entry.KIND_GENERAL;
    }

    private boolean validateNewCommitIndex(int newCommitIndex, int currentTerm) {
        if (newCommitIndex < entrySequence.getCommitIndex()) {
            return false;
        }
        Entry entry = entrySequence.getEntry(newCommitIndex);
        if (entry == null) {
            log.debug("log of new commit index {} not found", newCommitIndex);
            return false;
        }
        return entry.getTerm() == currentTerm;
    }

    private GroupConfigEntry appendEntriesFromLeader(EntrySequenceView leaderEntries) {
        if (leaderEntries.isEmpty()) {
            return null;
        }
        log.debug("append entries from leader from {} to {}", leaderEntries.getFirstLogIndex(), leaderEntries.getLastLogIndex());
        GroupConfigEntry lastGroupConfigEntry = null;
        for (Entry leaderEntry : leaderEntries) {
            entrySequence.append(leaderEntry);
            if (leaderEntry instanceof GroupConfigEntry) {
                lastGroupConfigEntry = (GroupConfigEntry) leaderEntry;
            }
        }
        return lastGroupConfigEntry;
    }

    private UnmatchedLogRemovedResult removeUnmatchedLog(EntrySequenceView leaderEntries) {
        assert !leaderEntries.isEmpty();
        int firstUnmatched = findFirstUnmatchedLog(leaderEntries);
        if (firstUnmatched < 0) {
            return new UnmatchedLogRemovedResult(EntrySequenceView.EMPTY, null);
        }

        // ?????????????????????????????????
        GroupConfigEntry firstRemovedEntry = removeEntriesAfter(firstUnmatched - 1);

        // ???????????????????????????
        return new UnmatchedLogRemovedResult(leaderEntries.subView(firstUnmatched), firstRemovedEntry);
    }

    private GroupConfigEntry removeEntriesAfter(int index) {
        //  ??? checkIfPreviousLogMatches ?????????????????????????????????????????????????????????????????????????????????
        if (entrySequence.isEmpty() || index >= entrySequence.getLastLogIndex()) {
            return null;
        }
        int lastApplied = stateMachine.getLastApplied();
        if (index < lastApplied && entrySequence.subList(index + 1, lastApplied + 1).stream().anyMatch(this::isApplicable)) {
            log.warn("applied log removed, reapply from start");
            applySnapshot(snapshot);
            log.debug("apply log from {} to {}", entrySequence.getFirstLogIndex(), index);
            entrySequence.subList(entrySequence.getFirstLogIndex(), index + 1).forEach(this::applyEntry);
        }
        log.debug("remove entries after {}", index);
        entrySequence.removeAfter(index);
        if (index < commitIndex) {
            commitIndex = index;
        }
        return groupConfigEntryList.removeAfter(index);
    }


    private int findFirstUnmatchedLog(EntrySequenceView leaderEntries) {
        for (Entry leaderEntry : leaderEntries) {
            int logIndex = leaderEntry.getIndex();
            // ????????????????????????????????????????????????
            EntryMeta followerEntryMeta = entrySequence.getEntryMeta(logIndex);
            // ????????????????????????????????????????????????????????????????????????????????????
            if (followerEntryMeta == null || followerEntryMeta.getTerm() != leaderEntry.getTerm()) {
                return logIndex;
            }
        }
        return -1;
    }

    private boolean checkIfPreviousLogMatches(int prevLogIndex, int prevLogTerm) {

        int lastIncludedIndex = snapshot.getLastIncludedIndex();
        if (prevLogIndex < lastIncludedIndex) {
            log.debug("previous log index {} < snapshot's last included index {}", prevLogIndex, lastIncludedIndex);
            return false;
        }
        if (prevLogIndex == lastIncludedIndex) {
            int lastIncludedTerm = snapshot.getLastIncludedTerm();
            if (prevLogTerm != lastIncludedTerm) {
                log.debug("previous log index matches snapshot's last included index, " +
                        "but term not (expected {}, actual {})", lastIncludedTerm, prevLogTerm);
                return false;
            }
            return true;
        }

        // TODO ?????? leader ??????????????????????????????????????????????????????prevLogIndex ?????????????????????,
        //  ?????????????????? prevLogIndex == 0 ?????????
        if (prevLogIndex == 0) {
            return true;
        }

        EntryMeta entryMeta = entrySequence.getEntryMeta(prevLogIndex);
        if (entryMeta == null) {
            return false;
        }
        int term = entryMeta.getTerm();
        return term == prevLogTerm;
    }

    private void applySnapshot(Snapshot snapshot) {
        log.debug("apply snapshot, last included index {}", snapshot.getLastIncludedIndex());
        try {
            stateMachine.applySnapshot(snapshot);
        } catch (IOException e) {
            throw new LogException("failed to apply snapshot", e);
        }
    }

    protected abstract void replaceSnapshot(Snapshot newSnapshot);

    protected abstract SnapshotBuilder<? extends Snapshot> newSnapshotBuilder(InstallSnapshotRpc firstRpc);

    protected abstract Snapshot generateSnapshot(EntryMeta lastAppliedEntryMeta, Set<NodeEndpoint> groupConfig);

    void setStateMachineContext(StateMachineContext stateMachineContext) {
        this.stateMachineContext = stateMachineContext;
    }

    @Getter
    private static class EntrySequenceView implements Iterable<Entry> {

        static final EntrySequenceView EMPTY = new EntrySequenceView(Collections.emptyList());

        private final List<Entry> entries;
        private int firstLogIndex = -1;
        private int lastLogIndex = -1;

        EntrySequenceView(List<Entry> entries) {
            this.entries = entries;
            if (!entries.isEmpty()) {
                firstLogIndex = entries.get(0).getIndex();
                lastLogIndex = entries.get(entries.size() - 1).getIndex();
            }
        }

        Entry get(int index) {
            if (entries.isEmpty() || index < firstLogIndex || index > lastLogIndex) {
                return null;
            }
            return entries.get(index - firstLogIndex);
        }

        boolean isEmpty() {
            return entries.isEmpty();
        }

        EntrySequenceView subView(int fromIndex) {
            if (entries.isEmpty() || fromIndex > lastLogIndex) {
                return new EntrySequenceView(Collections.emptyList());
            }
            return new EntrySequenceView(entries.subList(fromIndex - firstLogIndex, entries.size()));
        }

        @Override
        public Iterator<Entry> iterator() {
            return entries.iterator();
        }
    }

    private static class UnmatchedLogRemovedResult {
        private final EntrySequenceView newEntries;
        private final GroupConfigEntry firstRemovedEntry;

        UnmatchedLogRemovedResult(EntrySequenceView newEntries, GroupConfigEntry firstRemovedEntry) {
            this.newEntries = newEntries;
            this.firstRemovedEntry = firstRemovedEntry;
        }

        Set<NodeEndpoint> getGroup() {
            // ?????? getNodeEndpoints ????????????????????????????????????
            return firstRemovedEntry != null ? firstRemovedEntry.getNodeEndpoints() : null;
        }
    }

}
