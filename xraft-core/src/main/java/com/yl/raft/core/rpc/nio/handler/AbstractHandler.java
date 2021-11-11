package com.yl.raft.core.rpc.nio.handler;

import com.google.common.eventbus.EventBus;
import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.Channel;
import com.yl.raft.core.rpc.message.*;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * AbstractHandler
 */
@Slf4j
public class AbstractHandler extends ChannelDuplexHandler {

    protected final EventBus eventBus;
    protected NodeId remoteId;
    protected Channel channel;
    private AppendEntriesRpc lastAppendEntriesRpc;

    public AbstractHandler(EventBus eventBus) {
        this.eventBus = eventBus;
    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        assert remoteId != null;
        assert channel != null;

        if (msg instanceof RequestVoteRpc) {
            RequestVoteRpc rpc = (RequestVoteRpc) msg;
            eventBus.post(new RequestVoteRpcMessage(rpc, remoteId, channel));
        } else if (msg instanceof RequestVoteResult) {
            eventBus.post(msg);
        } else if (msg instanceof AppendEntriesRpc) {
            AppendEntriesRpc rpc = (AppendEntriesRpc) msg;
            eventBus.post(new AppendEntriesRpcMessage(rpc, remoteId, channel));
        } else if (msg instanceof AppendEntriesResult) {
            AppendEntriesResult result = (AppendEntriesResult) msg;
            if (lastAppendEntriesRpc == null) {
                log.warn("no last append entries rpc");
            } else {
                if (!Objects.equals(result.getMessageId(), lastAppendEntriesRpc.getMessageId())) {
                    log.warn("incorrect append entries rpc message id {}, expected {}", result.getMessageId(), lastAppendEntriesRpc.getMessageId());
                } else {
                    eventBus.post(new AppendEntriesResultMessage(result, remoteId, channel, lastAppendEntriesRpc));
                    lastAppendEntriesRpc = null;
                }
            }
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof AppendEntriesRpc) {
            lastAppendEntriesRpc = (AppendEntriesRpc) msg;
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.warn(cause.getMessage(), cause);
        ctx.close();
    }
}
