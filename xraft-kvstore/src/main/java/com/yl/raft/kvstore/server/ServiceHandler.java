package com.yl.raft.kvstore.server;

import com.yl.raft.kvstore.message.CommandRequest;
import com.yl.raft.kvstore.message.GetCommand;
import com.yl.raft.kvstore.message.SetCommand;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * ServiceHandler
 */
public class ServiceHandler extends ChannelInboundHandlerAdapter {

    private final Service service;

    public ServiceHandler(Service service) {
        this.service = service;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof GetCommand) {
            service.get(new CommandRequest<>((GetCommand) msg, ctx.channel()));
        } else if (msg instanceof SetCommand) {
            service.set(new CommandRequest<>((SetCommand) msg, ctx.channel()));
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
