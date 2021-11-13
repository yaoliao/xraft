package com.yl.raft.kvstore.message;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * CommandRequest
 */
@AllArgsConstructor
@Getter
public class CommandRequest<T> {

    private T command;
    private Channel channel;

    public void reply(Object response) {
        this.channel.writeAndFlush(response);
    }

    public void addCloseListener(Runnable runnable) {
        this.channel.closeFuture().addListener((ChannelFutureListener) future -> runnable.run());
    }

}
