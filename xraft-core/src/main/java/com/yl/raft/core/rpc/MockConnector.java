package com.yl.raft.core.rpc;

import com.yl.raft.core.node.NodeEndpoint;
import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.message.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * MockConnector
 */
@Slf4j
public class MockConnector implements Connector {

    private LinkedList<Message> messages = new LinkedList<>();

    @Override
    public void initialize() {

    }

    @Override
    public void close() {

    }

    @Override
    public void resetChannels() {

    }

    @Override
    public void sendRequestVote(@Nonnull RequestVoteRpc rpc, @Nonnull Collection<NodeEndpoint> destinationEndpoints) {
        Message message = new Message();
        message.setRpc(rpc);
        messages.add(message);
    }

    @Override
    public void replyRequestVote(@Nonnull RequestVoteResult result, @Nonnull NodeEndpoint destinationEndpoint) {
        Message message = new Message();
        message.setResult(result);
        message.setDestinationNodeId(destinationEndpoint.getId());
        messages.add(message);
    }

    @Override
    public void sendAppendEntries(@Nonnull AppendEntriesRpc rpc, @Nonnull NodeEndpoint destinationEndpoint) {
        Message message = new Message();
        message.setRpc(rpc);
        message.setDestinationNodeId(destinationEndpoint.getId());
        messages.add(message);
    }

    @Override
    public void replyAppendEntries(@Nonnull AppendEntriesResult result, @Nonnull NodeEndpoint destinationEndpoint) {
        Message message = new Message();
        message.setResult(result);
        message.setDestinationNodeId(destinationEndpoint.getId());
        messages.add(message);
    }

    @Override
    public void sendInstallSnapshot(@Nonnull InstallSnapshotRpc rpc, @Nonnull NodeEndpoint destinationEndpoint) {
        Message m = new Message();
        m.rpc = rpc;
        m.destinationNodeId = destinationEndpoint.getId();
        messages.add(m);
    }

    @Override
    public void replyInstallSnapshot(@Nonnull InstallSnapshotResult result, @Nonnull InstallSnapshotRpcMessage rpcMessage) {
        Message m = new Message();
        m.result = result;
        m.destinationNodeId = rpcMessage.getSourceNodeId();
        messages.add(m);
    }

    public Message getLastMessage() {
        return messages.isEmpty() ? null : messages.getLast();
    }

    private Message getLastMessageOrDefault() {
        return messages.isEmpty() ? new Message() : messages.getLast();
    }

    public Object getRpc() {
        return getLastMessageOrDefault().rpc;
    }

    public Object getResult() {
        return getLastMessageOrDefault().result;
    }

    public NodeId getDestinationNodeId() {
        return getLastMessageOrDefault().destinationNodeId;
    }

    public int getMessageCount() {
        return messages.size();
    }

    public List<Message> getMessages() {
        return new ArrayList<>(messages);
    }

    public void clearMessage() {
        messages.clear();
    }

    @Data
    public static class Message {
        private Object rpc;
        private NodeId destinationNodeId;
        private Object result;
    }
}
