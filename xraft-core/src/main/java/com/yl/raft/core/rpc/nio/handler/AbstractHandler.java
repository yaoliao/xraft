package com.yl.raft.core.rpc.nio.handler;

import com.google.common.eventbus.EventBus;
import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.Channel;
import com.yl.raft.core.rpc.message.*;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AbstractHandler
 */
@Slf4j
public class AbstractHandler extends ChannelDuplexHandler {

    protected final EventBus eventBus;
    protected NodeId remoteId;
    protected Channel channel;
    // TODO 书中这里的实现有问题 保存和获取 lastAppendEntriesRpc 的分别是 FromRemoteHandler 和 ToRemoteHandler，所以根本就取不到值
    //  现在这样实现也有问题，这个 map 可能会一直膨胀，最好用 nodeId 作为 key
    private static Map<String, AppendEntriesRpc> lastAppendEntriesRpcMap = new ConcurrentHashMap<>();

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
            if (lastAppendEntriesRpcMap.get(result.getMessageId()) == null) {
                log.warn("no last append entries rpc");
            } else {
                if (!Objects.equals(result.getMessageId(), lastAppendEntriesRpcMap.get(result.getMessageId()).getMessageId())) {
                    log.warn("incorrect append entries rpc message id {}, expected {}", result.getMessageId(),
                            lastAppendEntriesRpcMap.get(result.getMessageId()).getMessageId());
                } else {
                    eventBus.post(new AppendEntriesResultMessage(result, remoteId, channel, lastAppendEntriesRpcMap.get(result.getMessageId())));
                    lastAppendEntriesRpcMap.remove(result.getMessageId());
                }
            }
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof AppendEntriesRpc) {
            lastAppendEntriesRpcMap.put(((AppendEntriesRpc) msg).getMessageId(), (AppendEntriesRpc) msg);
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.warn(cause.getMessage(), cause);
        ctx.close();
    }
}
