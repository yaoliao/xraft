package com.yl.raft.core.rpc.nio;

import com.yl.raft.core.node.NodeId;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * InboundChannelGroup
 */
@Slf4j
public class InboundChannelGroup {

    private final List<NioChannel> channels = new CopyOnWriteArrayList<>();

    public void add(NodeId remoteId, NioChannel channel) {
        log.debug("channel INBOUND-{} connected", remoteId);
        channel.getDelegate().closeFuture().addListener((ChannelFutureListener) future -> {
            log.debug("channel INBOUND-{} disconnected", remoteId);
            remove(channel);
        });
    }

    private void remove(NioChannel channel) {
        channels.remove(channel);
    }

    void closeAll() {
        log.debug("close all inbound channels");
        for (NioChannel channel : channels) {
            channel.close();
        }
    }
}
