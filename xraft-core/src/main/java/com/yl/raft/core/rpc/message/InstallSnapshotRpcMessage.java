package com.yl.raft.core.rpc.message;

import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.Channel;

import javax.annotation.Nullable;

/**
 * InstallSnapshotRpcMessage
 */
public class InstallSnapshotRpcMessage extends AbstractRpcMessage<InstallSnapshotRpc> {

    public InstallSnapshotRpcMessage(InstallSnapshotRpc rpc, NodeId sourceNodeId, @Nullable Channel channel) {
        super(rpc, sourceNodeId, channel);
    }
}
