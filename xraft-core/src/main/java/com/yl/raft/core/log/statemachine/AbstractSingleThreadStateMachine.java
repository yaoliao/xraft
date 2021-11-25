package com.yl.raft.core.log.statemachine;

import com.yl.raft.core.log.snapshot.Snapshot;
import com.yl.raft.core.node.NodeEndpoint;
import com.yl.raft.core.support.SingleThreadTaskExecutor;
import com.yl.raft.core.support.TaskExecutor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

/**
 * AbstractSingleThreadStateMachine
 */
@Slf4j
public abstract class AbstractSingleThreadStateMachine implements StateMachine {

    private volatile int lastApplied;
    private final TaskExecutor taskExecutor;

    public AbstractSingleThreadStateMachine() {
        this.taskExecutor = new SingleThreadTaskExecutor("state-machine");
    }

    @Override
    public int getLastApplied() {
        return lastApplied;
    }

    @Override
    public void applyLog(StateMachineContext context, int index, int term, @Nonnull byte[] commandBytes, int firstLogIndex, Set<NodeEndpoint> lastGroupConfig) {
        taskExecutor.submit(() -> doApplyLog(context, index, term, commandBytes, firstLogIndex, lastGroupConfig));
    }

    @Override
    public void advanceLastApplied(int index) {
        taskExecutor.submit(() -> {
            if (index <= lastApplied) {
                return;
            }
            lastApplied = index;
        });
    }

    private void doApplyLog(StateMachineContext context, int index, int term, @Nonnull byte[] commandBytes, int firstLogIndex, Set<NodeEndpoint> lastGroupConfig) {
        if (index <= lastApplied) {
            return;
        }
        log.debug("apply log {}", index);
        applyCommand(commandBytes);
        lastApplied = index;
        if (!shouldGenerateSnapshot(firstLogIndex, index)) {
            return;
        }
        try {
            OutputStream output = context.getOutputForGeneratingSnapshot(index, term, lastGroupConfig);
            generateSnapshot(output);
            context.doneGeneratingSnapshot(index);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected abstract void applyCommand(@Nonnull byte[] commandBytes);

    @Override
    public void applySnapshot(@Nonnull Snapshot snapshot) throws IOException {
        log.info("apply snapshot, last included index {}", snapshot.getLastIncludedIndex());
        doApplySnapshot(snapshot.getDataStream());
        lastApplied = snapshot.getLastIncludedIndex();
    }

    protected abstract void doApplySnapshot(@Nonnull InputStream input) throws IOException;

    @Override
    public void shutdown() {
        try {
            taskExecutor.shutdown();
        } catch (InterruptedException e) {
            throw new StateMachineException(e);
        }
    }
}
