package com.yl.raft.core.node;

import com.yl.raft.core.node.role.*;
import com.yl.raft.core.rpc.MockConnector;
import com.yl.raft.core.rpc.message.*;
import com.yl.raft.core.schedule.NullScheduler;
import com.yl.raft.core.support.DirectTaskExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * NodeImplTest
 */
public class NodeImplTest {

    private NodeBuilder newNodeBuilder(NodeId selfId, NodeEndpoint... endpoints) {
        return new NodeBuilder(Arrays.asList(endpoints), selfId)
                .setScheduler(new NullScheduler())
                .setConnector(new MockConnector())
                .setTaskExecutor(new DirectTaskExecutor());
    }

    @Test
    public void testStart() {
        NodeImpl node = (NodeImpl) newNodeBuilder(NodeId.of("A"),
                new NodeEndpoint("A", "localhost", 233)).build();
        node.start();
        AbstractNodeRole role = node.getRole();
        Assert.assertEquals(role.getRoleName(), RoleName.FOLLOWER);
        Assert.assertEquals(role.getTerm(), 0);
        Assert.assertNull(((FollowerNodeRole) role).getVotedFor());
    }

    @Test
    public void testElectionTimeoutWhenFollower() {
        NodeImpl node = (NodeImpl) newNodeBuilder(NodeId.of("A"),
                new NodeEndpoint("A", "localhost", 2333),
                new NodeEndpoint("B", "localhost", 2334),
                new NodeEndpoint("C", "localhost", 2335)
        ).build();
        node.start();
        node.electionTimeout();

        CandidateNodeRole role = (CandidateNodeRole) node.getRole();
        Assert.assertEquals(role.getTerm(), 1);
        Assert.assertEquals(role.getVotesCount(), 1);
        MockConnector connector = (MockConnector) node.getContext().getConnector();

        RequestVoteRpc rpc = (RequestVoteRpc) connector.getRpc();
        Assert.assertEquals(rpc.getTerm(), 1);
        Assert.assertEquals(rpc.getCandidateId(), NodeId.of("A"));
        Assert.assertEquals(rpc.getLastLogIndex(), 0);
        Assert.assertEquals(rpc.getLastLogTerm(), 0);
    }

    @Test
    public void testOnReceiveRequestVoteRpcFollower() {
        NodeImpl node = (NodeImpl) newNodeBuilder(NodeId.of("A"),
                new NodeEndpoint("A", "localhost", 2333),
                new NodeEndpoint("B", "localhost", 2334),
                new NodeEndpoint("C", "localhost", 2335)
        ).build();
        node.start();

        RequestVoteRpc requestVoteRpc = new RequestVoteRpc();
        requestVoteRpc.setTerm(1);
        requestVoteRpc.setCandidateId(NodeId.of("C"));
        requestVoteRpc.setLastLogIndex(0);
        requestVoteRpc.setLastLogTerm(0);
        node.onReceiveRequestVoteRpc(new RequestVoteRpcMessage(requestVoteRpc, NodeId.of("C"), null));

        MockConnector connector = (MockConnector) node.getContext().getConnector();
        RequestVoteResult result = (RequestVoteResult) connector.getResult();

        Assert.assertEquals(result.getTerm(), 1);
        Assert.assertTrue(result.isVoteGranted());
        Assert.assertEquals(NodeId.of("C"), ((FollowerNodeRole) node.getRole()).getVotedFor());
    }

    @Test
    public void testOnReceiveRequestVoteResult() {
        // ????????? Follower
        NodeImpl node = (NodeImpl) newNodeBuilder(NodeId.of("A"),
                new NodeEndpoint("A", "localhost", 2333),
                new NodeEndpoint("B", "localhost", 2334),
                new NodeEndpoint("C", "localhost", 2335)
        ).build();
        node.start();

        // ?????????????????? Candidate
        node.electionTimeout();
        // ?????????????????? Leader
        node.onReceiveRequestVoteResult(new RequestVoteResult(1, true));
        LeaderNodeRole role = (LeaderNodeRole) node.getRole();
        Assert.assertEquals(role.getTerm(), 1);
    }

    @Test
    public void testReplicateLog() {
        NodeImpl node = (NodeImpl) newNodeBuilder(NodeId.of("A"),
                new NodeEndpoint("A", "localhost", 2333),
                new NodeEndpoint("B", "localhost", 2334),
                new NodeEndpoint("C", "localhost", 2335)
        ).build();
        node.start();

        node.electionTimeout();
        node.onReceiveRequestVoteResult(new RequestVoteResult(1, true));
        // ????????????????????????
        node.replicateLog();

        MockConnector connector = (MockConnector) node.getContext().getConnector();
        Assert.assertEquals(connector.getMessageCount(), 3);

        List<MockConnector.Message> messages = connector.getMessages();
        Set<NodeId> destinationNodeIds = messages.subList(1, 3).stream()
                .map(MockConnector.Message::getDestinationNodeId).collect(Collectors.toSet());
        Assert.assertEquals(destinationNodeIds.size(), 2);
        Assert.assertTrue(destinationNodeIds.contains(NodeId.of("B")));
        Assert.assertTrue(destinationNodeIds.contains(NodeId.of("C")));

        AppendEntriesRpc rpc = (AppendEntriesRpc) messages.get(2).getRpc();
        Assert.assertEquals(rpc.getTerm(), 1);
    }

    @Test
    public void testOnReceiveAppendEntriesRpcFollower() {
        NodeImpl node = (NodeImpl) newNodeBuilder(NodeId.of("A"),
                new NodeEndpoint("A", "localhost", 2333),
                new NodeEndpoint("B", "localhost", 2334),
                new NodeEndpoint("C", "localhost", 2335)
        ).build();
        node.start();

        AppendEntriesRpc rpc = new AppendEntriesRpc();
        rpc.setTerm(1);
        rpc.setLeaderId(NodeId.of("B"));

        node.onReceiveAppendEntriesRpc(new AppendEntriesRpcMessage(rpc, NodeId.of("B"), null));
        MockConnector connector = (MockConnector) node.getContext().getConnector();
        AppendEntriesResult result = (AppendEntriesResult) connector.getResult();
        Assert.assertEquals(result.getTerm(), 1);
        Assert.assertTrue(result.isSuccess());
        FollowerNodeRole role = (FollowerNodeRole) node.getRole();
        Assert.assertEquals(role.getTerm(), 1);
        Assert.assertEquals(role.getLeaderId(), NodeId.of("B"));
    }


    @Test
    public void testOnReceiveAppendEntriesNormal() {
        NodeImpl node = (NodeImpl) newNodeBuilder(NodeId.of("A"),
                new NodeEndpoint("A", "localhost", 2333),
                new NodeEndpoint("B", "localhost", 2334),
                new NodeEndpoint("C", "localhost", 2335)
        ).build();
        node.start();

        node.electionTimeout();
        node.onReceiveRequestVoteResult(new RequestVoteResult(1, true));
        node.replicateLog();

        node.onReceiveAppendEntriesResult(new AppendEntriesResultMessage(
                new AppendEntriesResult(message(), 1, true),
                NodeId.of("B"),
                null,
                new AppendEntriesRpc()
        ));
    }

    private String message() {
        return UUID.randomUUID().toString();
    }

}
