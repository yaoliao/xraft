package com.yl.raft.core.rpc;

import com.yl.raft.core.node.NodeEndpoint;
import com.yl.raft.core.rpc.message.AppendEntriesResult;
import com.yl.raft.core.rpc.message.AppendEntriesRpc;
import com.yl.raft.core.rpc.message.RequestVoteResult;
import com.yl.raft.core.rpc.message.RequestVoteRpc;

import javax.annotation.Nonnull;
import java.util.Collection;

public interface Connector {

    void initialize();

    void close();

    void resetChannels();

    void sendRequestVote(@Nonnull RequestVoteRpc rpc, @Nonnull Collection<NodeEndpoint> destinationEndpoints);

    void replyRequestVote(@Nonnull RequestVoteResult result, @Nonnull NodeEndpoint destinationEndpoint);

    void sendAppendEntries(@Nonnull AppendEntriesRpc rpc, @Nonnull NodeEndpoint destinationEndpoint);

    void replyAppendEntries(@Nonnull AppendEntriesResult result, @Nonnull NodeEndpoint destinationEndpoint);

}
