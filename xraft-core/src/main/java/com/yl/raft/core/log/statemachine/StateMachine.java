package com.yl.raft.core.log.statemachine;

/**
 * StateMachine
 */
public interface StateMachine {

    /**
     * 获取 lastApplied
     */
    int getLastApplied();

    /**
     * 应用日志
     */
    void applyLog(StateMachineContext context, int index, byte[] commandBytes, int firstLogIndex);

    void shutdown();

}
