package com.yl.raft.core.node.task;

import com.yl.raft.core.node.NodeEndpoint;
import com.yl.raft.core.node.NodeId;

/**
 * Task context for {@link GroupConfigChangeTask}.
 */
public interface GroupConfigChangeTaskContext {

    /**
     * Add node.
     * <p>
     * Process will be run in node task executor.
     * </p>
     * <ul>
     * <li>add node to group</li>
     * <li>append log entry</li>
     * <li>replicate</li>
     * </ul>
     *
     * @param endpoint   endpoint
     * @param nextIndex  next index
     * @param matchIndex match index
     */
    void addNode(NodeEndpoint endpoint, int nextIndex, int matchIndex);


    void downgradeSelf();

    /**
     * Remove node from group.
     * <p>
     * Process will be run in node task executor.
     * </p>
     * <p>
     * if node id is self id, step down.
     * </p>
     *
     * @param nodeId node id
     */
    void removeNode(NodeId nodeId);

    /**
     * Done and remove current group config change task.
     */
    void done();

}
