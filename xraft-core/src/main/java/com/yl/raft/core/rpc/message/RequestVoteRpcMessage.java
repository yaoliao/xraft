package com.yl.raft.core.rpc.message;

import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.Channel;

/**
 * RequestVoteRpcMessage
 */
public class RequestVoteRpcMessage extends AbstractRpcMessage<RequestVoteRpc> {

    public RequestVoteRpcMessage(RequestVoteRpc rpc, NodeId sourceNodeId, Channel channel) {
        super(rpc, sourceNodeId, channel);
    }
}
