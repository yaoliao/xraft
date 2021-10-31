package com.yl.raft.core.node.role;

import com.yl.raft.core.node.NodeId;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * abstract role
 */
@Getter
@Setter
@AllArgsConstructor
public abstract class AbstractNodeRole {

    /**
     * 角色类型
     */
    private final RoleName roleName;

    /**
     * 任期
     */
    protected final int term;

    /**
     * 取消超时或者定时任务
     */
    public abstract void cancelTimeOutOrTask();


    public abstract NodeId getLeaderId(NodeId selfId);
}
