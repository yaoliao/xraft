package com.yl.raft.core.rpc.message;

import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.Channel;
import lombok.Getter;

/**
 * AppendEntriesResultMessage
 */
@Getter
public class AppendEntriesResultMessage extends AbstractRpcMessage<AppendEntriesResult> {

    private final AppendEntriesRpc appendEntriesRpc;

    public AppendEntriesResultMessage(AppendEntriesResult rpc, NodeId sourceNodeId, Channel channel, AppendEntriesRpc appendEntriesRpc) {
        super(rpc, sourceNodeId, channel);
        this.appendEntriesRpc = appendEntriesRpc;
    }
}
