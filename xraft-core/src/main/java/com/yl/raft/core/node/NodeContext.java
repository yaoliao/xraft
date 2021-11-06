package com.yl.raft.core.node;

import com.google.common.eventbus.EventBus;
import com.yl.raft.core.log.Log;
import com.yl.raft.core.node.config.NodeConfig;
import com.yl.raft.core.node.store.NodeStore;
import com.yl.raft.core.rpc.Connector;
import com.yl.raft.core.schedule.Scheduler;
import com.yl.raft.core.support.TaskExecutor;
import lombok.Getter;
import lombok.Setter;

/**
 * NodeContext
 */
@Getter
@Setter
public class NodeContext {

    /**
     * 当前节点
     */
    private NodeId selfId;

    /**
     * 成员列表
     */
    private NodeGroup group;

    /**
     * 日志
     */
    private Log log;

    /**
     * RPC 组件
     */
    private Connector connector;

    /**
     * 定时器
     */
    private Scheduler scheduler;

    /**
     * 主线程执行器
     */
    private TaskExecutor taskExecutor;

    /**
     * 角色状态存储
     */
    private NodeStore store;

    /**
     * 配置信息
     */
    private NodeConfig config;

    private EventBus eventBus;

}
