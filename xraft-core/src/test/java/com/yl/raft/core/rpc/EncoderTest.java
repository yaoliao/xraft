package com.yl.raft.core.rpc;

import com.yl.raft.core.Protos;
import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.message.MessageConstants;
import com.yl.raft.core.rpc.message.RequestVoteRpc;
import com.yl.raft.core.rpc.nio.Encoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

/**
 * EncoderTest
 */
public class EncoderTest {

    @Test
    public void testNodeId() throws Exception {
        Encoder encoder = new Encoder();
        ByteBuf buffer = Unpooled.buffer();
        encoder.encode(null, new NodeId("A"), buffer);
        Assert.assertEquals(MessageConstants.MSG_TYPE_NODE_ID, buffer.readInt());
        Assert.assertEquals(1, buffer.readInt());
        Assert.assertEquals((byte) 'A', buffer.readByte());
    }

    @Test
    public void testRequestVoteRpc() throws Exception {
        Encoder encoder = new Encoder();
        ByteBuf buffer = Unpooled.buffer();
        RequestVoteRpc requestVoteRpc = new RequestVoteRpc();
        requestVoteRpc.setTerm(2);
        requestVoteRpc.setCandidateId(new NodeId("A"));
        requestVoteRpc.setLastLogIndex(2);
        requestVoteRpc.setLastLogTerm(1);
        encoder.encode(null, requestVoteRpc, buffer);

        Assert.assertEquals(MessageConstants.MSG_TYPE_REQUEST_VOTE_RPC, buffer.readInt());
        buffer.readInt(); // length
        Protos.RequestVoteRpc decodeRpc = Protos.RequestVoteRpc.parseFrom(new ByteBufInputStream(buffer));
        Assert.assertEquals(decodeRpc.getTerm(), requestVoteRpc.getTerm());
        Assert.assertEquals(decodeRpc.getLastLogIndex(), requestVoteRpc.getLastLogIndex());
        Assert.assertEquals(decodeRpc.getLastLogTerm(), requestVoteRpc.getLastLogTerm());
        Assert.assertEquals(decodeRpc.getCandidateId(), requestVoteRpc.getCandidateId().getValue());
    }


}
