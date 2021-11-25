package com.yl.raft.core.node.role;

import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.schedule.ElectionTimeout;
import lombok.Getter;
import lombok.ToString;

/**
 * follower role
 */
@Getter
@ToString
public class FollowerNodeRole extends AbstractNodeRole {

    /**
     * 投票给谁，可能为 null
     */
    private final NodeId votedFor;

    /**
     * 当前的 leader 节点，可能为 null
     */
    private final NodeId leaderId;

    /**
     * 选举超时定时器
     */
    private final ElectionTimeout electionTimeout;

    /**
     * 最后的从 leader 收到的心跳时间
     */
    private final long lastHeartbeat;


    public FollowerNodeRole(int term, NodeId votedFor, NodeId leaderId, long lastHeartbeat, ElectionTimeout electionTimeout) {
        super(RoleName.FOLLOWER, term);
        this.votedFor = votedFor;
        this.leaderId = leaderId;
        this.electionTimeout = electionTimeout;
        this.lastHeartbeat = lastHeartbeat;
    }

    @Override
    public void cancelTimeOutOrTask() {
        this.electionTimeout.cancel();
    }

    @Override
    public NodeId getLeaderId(NodeId selfId) {
        return leaderId;
    }
}
