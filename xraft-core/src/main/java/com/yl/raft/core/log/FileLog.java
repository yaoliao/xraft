package com.yl.raft.core.log;

import com.google.common.eventbus.EventBus;
import com.yl.raft.core.log.entry.Entry;
import com.yl.raft.core.log.entry.EntryMeta;
import com.yl.raft.core.log.event.SnapshotGeneratedEvent;
import com.yl.raft.core.log.sequence.FileEntrySequence;
import com.yl.raft.core.log.snapshot.*;
import com.yl.raft.core.log.statemachine.StateMachineContext;
import com.yl.raft.core.node.NodeEndpoint;
import com.yl.raft.core.rpc.message.InstallSnapshotRpc;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

/**
 * FileLog
 */
public class FileLog extends AbstractLog {

    private final RootDir rootDir;

    public FileLog(File baseDir, EventBus eventBus, Set<NodeEndpoint> baseGroup) {
        super(eventBus);
        setStateMachineContext(new StateMachineContextImpl());
        rootDir = new RootDir(baseDir);

        LogGeneration latestGeneration = rootDir.getLatestGeneration();
        snapshot = new EmptySnapshot();

        if (latestGeneration != null) {
            Set<NodeEndpoint> initialGroup = baseGroup;
            if (latestGeneration.getSnapshotFile().exists()) {
                snapshot = new FileSnapshot(latestGeneration);
                initialGroup = snapshot.getLastConfig();
            }
            FileEntrySequence fileEntrySequence = new FileEntrySequence(latestGeneration, snapshot.getLastIncludedIndex() + 1);
            commitIndex = fileEntrySequence.getCommitIndex();
            entrySequence = fileEntrySequence;
            groupConfigEntryList = entrySequence.buildGroupConfigEntryList(initialGroup);
        } else {
            LogGeneration firstGeneration = rootDir.createFirstGeneration();
            entrySequence = new FileEntrySequence(firstGeneration, 1);
        }
    }

    @Override
    public void snapshotGenerated(int lastIncludedIndex) {
        if (lastIncludedIndex <= snapshot.getLastIncludedIndex()) {
            return;
        }
        replaceSnapshot(new FileSnapshot(rootDir.getLogDirForGenerating()));
    }

    @Override
    protected void replaceSnapshot(Snapshot newSnapshot) {
        FileSnapshot fileSnapshot = (FileSnapshot) newSnapshot;
        int lastIncludedIndex = fileSnapshot.getLastIncludedIndex();
        int logIndexOffset = lastIncludedIndex + 1;

        // 获取快照之后的日志
        List<Entry> entries = entrySequence.subList(logIndexOffset);
        // 写入日志快照所在目录
        FileEntrySequence fileEntrySequence = new FileEntrySequence(fileSnapshot.getLogDir(), logIndexOffset);
        fileEntrySequence.append(entries);
        fileEntrySequence.commit(Math.max(commitIndex, lastIncludedIndex));
        fileEntrySequence.close();

        snapshot.close();
        entrySequence.close();
        newSnapshot.close();

        LogDir generation = rootDir.rename(fileSnapshot.getLogDir(), lastIncludedIndex);
        snapshot = new FileSnapshot(generation);
        entrySequence = new FileEntrySequence(generation, logIndexOffset);
        commitIndex = entrySequence.getCommitIndex();
        groupConfigEntryList = entrySequence.buildGroupConfigEntryList(snapshot.getLastConfig());
    }

    @Override
    protected SnapshotBuilder<FileSnapshot> newSnapshotBuilder(InstallSnapshotRpc firstRpc) {
        return new FileSnapshotBuilder(firstRpc, rootDir.getLogDirForInstalling());
    }

    @Override
    protected Snapshot generateSnapshot(EntryMeta lastAppliedEntryMeta, Set<NodeEndpoint> groupConfig) {
        LogDir logDir = rootDir.getLogDirForGenerating();
        try (FileSnapshotWriter snapshotWriter = new FileSnapshotWriter(
                logDir.getSnapshotFile(), lastAppliedEntryMeta.getIndex(), lastAppliedEntryMeta.getTerm(), groupConfig)) {
            stateMachine.generateSnapshot(snapshotWriter.getOutput());
        } catch (IOException e) {
            throw new LogException("failed to generate snapshot", e);
        }
        return new FileSnapshot(logDir);
    }

    private class StateMachineContextImpl implements StateMachineContext {

        private FileSnapshotWriter snapshotWriter = null;

        @Override
        public void generateSnapshot(int lastIncludedIndex) {
            throw new UnsupportedOperationException();
        }

        public OutputStream getOutputForGeneratingSnapshot(int lastIncludedIndex, int lastIncludedTerm, Set<NodeEndpoint> groupConfig) throws Exception {
            if (snapshotWriter != null) {
                snapshotWriter.close();
            }
            snapshotWriter = new FileSnapshotWriter(rootDir.getLogDirForGenerating().getSnapshotFile(), lastIncludedIndex, lastIncludedTerm, groupConfig);
            return snapshotWriter.getOutput();
        }

        public void doneGeneratingSnapshot(int lastIncludedIndex) throws Exception {
            if (snapshotWriter == null) {
                throw new IllegalStateException("snapshot not created");
            }
            snapshotWriter.close();
            eventBus.post(new SnapshotGeneratedEvent(lastIncludedIndex));
        }
    }
}
