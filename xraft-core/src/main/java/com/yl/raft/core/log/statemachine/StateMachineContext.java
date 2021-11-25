package com.yl.raft.core.log.statemachine;

import com.yl.raft.core.node.NodeEndpoint;

import java.io.OutputStream;
import java.util.Set;

/**
 * StateMachineContext
 */
public interface StateMachineContext {

    /**
     * 生成日志快照
     */
    @Deprecated
    void generateSnapshot(int lastIncludedIndex);

    OutputStream getOutputForGeneratingSnapshot(int lastIncludedIndex, int lastIncludedTerm, Set<NodeEndpoint> groupConfig) throws Exception;

    void doneGeneratingSnapshot(int lastIncludedIndex) throws Exception;
}
