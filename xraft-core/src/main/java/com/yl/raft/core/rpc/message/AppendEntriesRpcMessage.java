package com.yl.raft.core.rpc.message;

import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.Channel;

/**
 * AppendEntriesRpcMessage
 */
public class AppendEntriesRpcMessage extends AbstractRpcMessage<AppendEntriesRpc> {

    public AppendEntriesRpcMessage(AppendEntriesRpc rpc, NodeId sourceNodeId, Channel channel) {
        super(rpc, sourceNodeId, channel);
    }
}
