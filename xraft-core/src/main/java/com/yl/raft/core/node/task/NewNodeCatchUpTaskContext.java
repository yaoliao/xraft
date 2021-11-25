package com.yl.raft.core.node.task;

import com.yl.raft.core.node.NodeEndpoint;

/**
 * NewNodeCatchUpTaskContext
 */
public interface NewNodeCatchUpTaskContext {

    /**
     * Replicate log to new node.
     * <p>
     * Process will be run in node task executor.
     * </p>
     *
     * @param endpoint endpoint
     */
    void replicateLog(NodeEndpoint endpoint);

    /**
     * Replicate log to endpoint.
     *
     * @param endpoint  endpoint
     * @param nextIndex next index
     */
    void doReplicateLog(NodeEndpoint endpoint, int nextIndex);

    void sendInstallSnapshot(NodeEndpoint endpoint, int offset);

    /**
     * Done and remove current task.
     *
     * @param task task
     */
    void done(NewNodeCatchUpTask task);

}
