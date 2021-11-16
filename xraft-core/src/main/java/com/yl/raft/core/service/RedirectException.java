package com.yl.raft.core.service;

import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.ChannelException;

/**
 * RedirectException
 */
public class RedirectException extends ChannelException {

    private final NodeId leaderId;

    public RedirectException(NodeId leaderId) {
        this.leaderId = leaderId;
    }

    public NodeId getLeaderId() {
        return leaderId;
    }

}
