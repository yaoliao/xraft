package com.yl.raft.core.log.statemachine;

/**
 * StateMachineContext
 */
public interface StateMachineContext {

    /**
     * 生成日志快照
     */
    void generateSnapshot(int lastIncludedIndex);
}
