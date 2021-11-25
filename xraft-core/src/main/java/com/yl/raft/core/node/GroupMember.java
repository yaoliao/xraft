package com.yl.raft.core.node;

import lombok.Getter;

/**
 * GroupMember TODO
 */
@Getter
public class GroupMember {

    private final NodeEndpoint endpoint;
    private ReplicatingState replicatingState;

    /**
     * 非 major 对象不参与 commitIndex 计算和选举
     */
    private boolean major;

    /**
     * 普通节点
     */
    GroupMember(NodeEndpoint endpoint) {
        this(endpoint, null, true);
    }

    /**
     * 新节点
     */
    GroupMember(NodeEndpoint endpoint, ReplicatingState replicatingState, boolean major) {
        this.endpoint = endpoint;
        this.replicatingState = replicatingState;
        this.major = major;
    }

    NodeId getId() {
        return endpoint.getId();
    }

    boolean idEquals(NodeId id) {
        return endpoint.getId().equals(id);
    }

    void setReplicatingState(ReplicatingState replicatingState) {
        this.replicatingState = replicatingState;
    }

    boolean isReplicationStateSet() {
        return replicatingState != null;
    }

    private ReplicatingState ensureReplicatingState() {
        if (replicatingState == null) {
            throw new IllegalStateException("replication state not set");
        }
        return replicatingState;
    }

    int getNextIndex() {
        return ensureReplicatingState().getNextIndex();
    }

    int getMatchIndex() {
        return ensureReplicatingState().getMatchIndex();
    }

    public boolean advanceReplicatingState(int lastEntryIndex) {
        return ensureReplicatingState().advance(lastEntryIndex);
    }

    public boolean backOfNextIndex() {
        return ensureReplicatingState().backOffNextIndex();
    }

    // =================  快照相关 ==================

    void replicateNow() {
        replicateAt(System.currentTimeMillis());
    }

    void replicateAt(long replicatedAt) {
        ReplicatingState replicatingState = ensureReplicatingState();
        replicatingState.setReplicating(true);
        replicatingState.setLastReplicatedAt(replicatedAt);
    }

    boolean isReplicating() {
        return ensureReplicatingState().isReplicating();
    }

    void stopReplicating() {
        ensureReplicatingState().setReplicating(false);
    }

    /**
     * Test if should replicate.
     * <p>
     * Return true if
     * <ol>
     * <li>not replicating</li>
     * <li>replicated but no response in specified timeout</li>
     * </ol>
     * </p>
     *
     * @param readTimeout read timeout
     * @return true if should, otherwise false
     */
    boolean shouldReplicate(long readTimeout) {
        ReplicatingState replicatingState = ensureReplicatingState();
        return !replicatingState.isReplicating() ||
                System.currentTimeMillis() - replicatingState.getLastReplicatedAt() >= readTimeout;
    }
}
