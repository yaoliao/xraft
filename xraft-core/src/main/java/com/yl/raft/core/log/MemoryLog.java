package com.yl.raft.core.log;

import com.google.common.eventbus.EventBus;
import com.yl.raft.core.log.entry.Entry;
import com.yl.raft.core.log.entry.EntryMeta;
import com.yl.raft.core.log.event.SnapshotGeneratedEvent;
import com.yl.raft.core.log.sequence.EntrySequence;
import com.yl.raft.core.log.sequence.GroupConfigEntryList;
import com.yl.raft.core.log.sequence.MemoryEntrySequence;
import com.yl.raft.core.log.snapshot.*;
import com.yl.raft.core.log.statemachine.StateMachineContext;
import com.yl.raft.core.node.NodeEndpoint;
import com.yl.raft.core.rpc.message.InstallSnapshotRpc;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * MemoryLog
 */
@Slf4j
public class MemoryLog extends AbstractLog {

    private final StateMachineContextImpl stateMachineContext = new StateMachineContextImpl();

    public MemoryLog() {
        this(new EventBus(), Collections.emptySet());
    }

    public MemoryLog(EventBus eventBus, Set<NodeEndpoint> initialGroup) {
        this(new EmptySnapshot(), new MemoryEntrySequence(), eventBus, initialGroup);
    }

    public MemoryLog(Snapshot snapshot, EntrySequence entrySequence, EventBus eventBus, Set<NodeEndpoint> initialGroup) {
        super(eventBus);
        setStateMachineContext(stateMachineContext);
        this.snapshot = snapshot;
        this.entrySequence = entrySequence;
        Set<NodeEndpoint> lastGroup = snapshot.getLastConfig();
        this.groupConfigEntryList = new GroupConfigEntryList((lastGroup.isEmpty() ? initialGroup : lastGroup));
    }

    @Override
    public void snapshotGenerated(int lastIncludedIndex) {
        if (lastIncludedIndex <= snapshot.getLastIncludedIndex()) {
            return;
        }
        replaceSnapshot(stateMachineContext.buildSnapshot());
    }

    @Override
    protected void replaceSnapshot(Snapshot newSnapshot) {
        int logIndexOffset = newSnapshot.getLastIncludedIndex() + 1;
        EntrySequence newEntrySequence = new MemoryEntrySequence(logIndexOffset);
        List<Entry> remainingEntries = entrySequence.subList(logIndexOffset);
        newEntrySequence.append(remainingEntries);
        log.debug("snapshot -> {}", newSnapshot);
        snapshot = newSnapshot;
        log.debug("entry sequence -> {}", newEntrySequence);
        entrySequence = newEntrySequence;
    }

    @Override
    protected SnapshotBuilder<MemorySnapshot> newSnapshotBuilder(InstallSnapshotRpc firstRpc) {
        return new MemorySnapshotBuilder(firstRpc);
    }

    @Override
    protected Snapshot generateSnapshot(EntryMeta lastAppliedEntryMeta, Set<NodeEndpoint> groupConfig) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            stateMachine.generateSnapshot(output);
        } catch (IOException e) {
            throw new LogException("failed to generate snapshot", e);
        }
        return new MemorySnapshot(lastAppliedEntryMeta.getIndex(), lastAppliedEntryMeta.getTerm(), output.toByteArray(), groupConfig);
    }

    private class StateMachineContextImpl implements StateMachineContext {

        private int lastIncludedIndex;
        private int lastIncludedTerm;
        private Set<NodeEndpoint> groupConfig;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        @Override
        public void generateSnapshot(int lastIncludedIndex) {
        }

        @Override
        public OutputStream getOutputForGeneratingSnapshot(int lastIncludedIndex, int lastIncludedTerm, Set<NodeEndpoint> groupConfig) throws Exception {
            this.lastIncludedIndex = lastIncludedIndex;
            this.lastIncludedTerm = lastIncludedTerm;
            this.groupConfig = groupConfig;
            output.reset();
            return output;
        }

        @Override
        public void doneGeneratingSnapshot(int lastIncludedIndex) throws Exception {
            eventBus.post(new SnapshotGeneratedEvent(lastIncludedIndex));
        }

        MemorySnapshot buildSnapshot() {
            return new MemorySnapshot(lastIncludedIndex, lastIncludedTerm, output.toByteArray(), groupConfig);
        }
    }
}
