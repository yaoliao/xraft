package com.yl.raft.core.rpc;

import com.yl.raft.core.Protos;
import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.message.MessageConstants;
import com.yl.raft.core.rpc.message.RequestVoteRpc;
import com.yl.raft.core.rpc.nio.Decoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * DecoderTest
 */
public class DecoderTest {

    @Test
    public void testNodeId() throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(MessageConstants.MSG_TYPE_NODE_ID);
        buffer.writeInt(1);
        buffer.writeByte((byte) 'A');
        Decoder decoder = new Decoder();
        List<Object> out = new ArrayList<>();
        decoder.decode(null, buffer, out);
        Assert.assertNotNull(out.get(0));
        Assert.assertEquals(new NodeId("A"), out.get(0));
    }

    @Test
    public void testRequestVoteRpc() throws Exception {
        Protos.RequestVoteRpc requestVoteRpc = Protos.RequestVoteRpc.newBuilder()
                .setTerm(2)
                .setCandidateId("A")
                .setLastLogIndex(2)
                .setLastLogTerm(1)
                .build();
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(MessageConstants.MSG_TYPE_REQUEST_VOTE_RPC);
        byte[] bytes = requestVoteRpc.toByteArray();
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);

        Decoder decoder = new Decoder();
        List<Object> out = new ArrayList<>();
        decoder.decode(null, buffer, out);
        Assert.assertNotNull(out.get(0));
        Assert.assertEquals(RequestVoteRpc.class, out.get(0).getClass());
        RequestVoteRpc rpc = (RequestVoteRpc) out.get(0);
        Assert.assertEquals(requestVoteRpc.getTerm(), rpc.getTerm());
        Assert.assertEquals(requestVoteRpc.getLastLogTerm(), rpc.getLastLogTerm());
        Assert.assertEquals(requestVoteRpc.getLastLogIndex(), rpc.getLastLogIndex());
        Assert.assertEquals(requestVoteRpc.getCandidateId(), rpc.getCandidateId().getValue());
    }

}
