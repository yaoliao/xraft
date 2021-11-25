package com.yl.raft.core.node;

import com.yl.raft.core.log.statemachine.StateMachine;
import com.yl.raft.core.node.role.RoleNameAndLeaderId;
import com.yl.raft.core.node.task.GroupConfigChangeTaskReference;

import javax.annotation.Nonnull;

/**
 * Node
 */
public interface Node {

    /**
     * 启动
     */
    void start();

    /**
     * 停止
     */
    void stop() throws InterruptedException;

    /**
     * 注册状态机
     */
    void registerStateMachine(@Nonnull StateMachine stateMachine);

    /**
     * 追加日志
     */
    void appendLog(@Nonnull byte[] commandBytes);

    /**
     * 获取当前节点信息和 leader 信息
     */
    RoleNameAndLeaderId getRoleNameAndLeaderId();

    /**
     * 添加节点
     */
    @Nonnull
    GroupConfigChangeTaskReference addNode(@Nonnull NodeEndpoint endpoint);

    /**
     * 移除节点
     */
    @Nonnull
    GroupConfigChangeTaskReference removeNode(@Nonnull NodeId id);

}
