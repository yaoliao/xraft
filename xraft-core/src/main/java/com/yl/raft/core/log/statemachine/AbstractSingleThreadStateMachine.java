package com.yl.raft.core.log.statemachine;

import com.yl.raft.core.support.SingleThreadTaskExecutor;
import com.yl.raft.core.support.TaskExecutor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;

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
    public void applyLog(StateMachineContext context, int index, byte[] commandBytes, int firstLogIndex) {
        this.taskExecutor.submit(() -> doApplyLog(context, index, commandBytes, firstLogIndex));
    }

    private void doApplyLog(StateMachineContext context, int index, byte[] commandBytes, int firstLogIndex) {
        if (index <= lastApplied) {
            return;
        }
        applyCommand(commandBytes);
        this.lastApplied = index;

        // TODO 快照
    }

    protected abstract void applyCommand(@Nonnull byte[] commandBytes);

    @Override
    public void shutdown() {
        try {
            taskExecutor.shutdown();
        } catch (InterruptedException e) {
            throw new StateMachineException(e);
        }
    }
}
