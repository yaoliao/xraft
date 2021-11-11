package com.yl.raft.core.rpc.nio.handler;

import com.google.common.eventbus.EventBus;
import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.nio.NioChannel;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/**
 * ToRemoteHandler
 */
@Slf4j
public class ToRemoteHandler extends AbstractHandler {

    private final NodeId selfNodeId;

    public ToRemoteHandler(EventBus eventBus, NodeId remoteId, NodeId selfNodeId) {
        super(eventBus);
        this.remoteId = remoteId;
        this.selfNodeId = selfNodeId;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.write(selfNodeId);
        channel = new NioChannel(ctx.channel());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        log.debug("receive {} from {}", msg, remoteId);
        super.channelRead(ctx, msg);
    }
}
