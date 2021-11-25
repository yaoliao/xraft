package com.yl.raft.core.node;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.EventBus;
import com.yl.raft.core.log.FileLog;
import com.yl.raft.core.log.Log;
import com.yl.raft.core.log.MemoryLog;
import com.yl.raft.core.node.config.NodeConfig;
import com.yl.raft.core.node.store.FileNodeStore;
import com.yl.raft.core.node.store.MemoryNodeStore;
import com.yl.raft.core.node.store.NodeStore;
import com.yl.raft.core.rpc.Connector;
import com.yl.raft.core.rpc.nio.NioConnector;
import com.yl.raft.core.schedule.DefaultScheduler;
import com.yl.raft.core.schedule.Scheduler;
import com.yl.raft.core.support.SingleThreadTaskExecutor;
import com.yl.raft.core.support.TaskExecutor;
import io.netty.channel.nio.NioEventLoopGroup;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * NodeBuilder
 */
public class NodeBuilder {

    private NodeConfig config = new NodeConfig();
    private final NodeGroup group;
    private final NodeId selfId;
    private final EventBus eventBus;
    private Scheduler scheduler;
    private Connector connector;
    private TaskExecutor taskExecutor;
    private Log log;
    private NodeStore store = null;
    private NioEventLoopGroup workerNioEventLoopGroup = null;
    private TaskExecutor groupConfigChangeTaskExecutor;

    private boolean standby;

    public NodeBuilder(NodeEndpoint endpoint) {
        this(Collections.singletonList(endpoint), endpoint.getId());
    }

    public NodeBuilder(Collection<NodeEndpoint> endpoint, NodeId selfId) {
        this.group = new NodeGroup(endpoint, selfId);
        this.selfId = selfId;
        this.eventBus = new EventBus(selfId.getValue());
    }

    public NodeBuilder setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
        return this;
    }

    public NodeBuilder setConnector(Connector connector) {
        this.connector = connector;
        return this;
    }

    public NodeBuilder setTaskExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
        return this;
    }

    public NodeBuilder setLog(Log log) {
        this.log = log;
        return this;
    }

    public NodeBuilder setDataDir(@Nullable String dataDirPath) {
        if (dataDirPath == null || dataDirPath.isEmpty()) {
            return this;
        }
        File dataDir = new File(dataDirPath);
        if (!dataDir.isDirectory() || !dataDir.exists()) {
            throw new IllegalArgumentException("[" + dataDirPath + "] not a directory, or not exists");
        }
        log = new FileLog(dataDir, eventBus, group.listEndpointOfMajor());
        store = new FileNodeStore(new File(dataDir, FileNodeStore.FILE_NAME));
        return this;
    }

    public NodeBuilder setStandby(boolean standby) {
        this.standby = standby;
        return this;
    }

    public NodeBuilder setWorkerNioEventLoopGroup(@Nonnull NioEventLoopGroup workerNioEventLoopGroup) {
        Preconditions.checkNotNull(workerNioEventLoopGroup);
        this.workerNioEventLoopGroup = workerNioEventLoopGroup;
        return this;
    }

    public Node build() {
        return new NodeImpl(buildContext());
    }

    private NodeContext buildContext() {
        NodeContext nodeContext = new NodeContext();
        nodeContext.setSelfId(selfId);
        nodeContext.setGroup(group);
        nodeContext.setConnector(connector == null ? createNioConnector() : connector);
        nodeContext.setScheduler(scheduler == null ? new DefaultScheduler(config) : scheduler);
        nodeContext.setTaskExecutor(taskExecutor == null ? new SingleThreadTaskExecutor("node") : taskExecutor);
        nodeContext.setEventBus(eventBus);
        nodeContext.setStore(store == null ? new MemoryNodeStore() : store);
        nodeContext.setLog(log == null ? new MemoryLog(eventBus, group.listEndpointOfMajor()) : log);
        nodeContext.setConfig(config);
        nodeContext.setGroupConfigChangeTaskExecutor(groupConfigChangeTaskExecutor != null ? groupConfigChangeTaskExecutor :
                new SingleThreadTaskExecutor("group-config-change"));
        return nodeContext;
    }

    @Nonnull
    private NioConnector createNioConnector() {
        int port = group.findSelf().getEndpoint().getPort();
        if (workerNioEventLoopGroup != null) {
            return new NioConnector(workerNioEventLoopGroup, selfId, eventBus, port, config.getLogReplicationInterval());
        }
        return new NioConnector(new NioEventLoopGroup(config.getNioWorkerThreads()), false,
                selfId, eventBus, port, config.getLogReplicationInterval());
    }

    @Nonnull
    private NodeMode evaluateMode() {
        if (standby) {
            return NodeMode.STANDBY;
        }
        if (group.isStandalone()) {
            return NodeMode.STANDALONE;
        }
        return NodeMode.GROUP_MEMBER;
    }
}
