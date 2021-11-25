package com.yl.raft.core.node.task;

import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.message.AppendEntriesResultMessage;
import com.yl.raft.core.rpc.message.InstallSnapshotResultMessage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * NewNodeCatchUpTaskGroup
 */
public class NewNodeCatchUpTaskGroup {

    private final ConcurrentMap<NodeId, NewNodeCatchUpTask> taskMap = new ConcurrentHashMap<>();

    /**
     * 添加一个 catch up 任务
     */
    public boolean add(NewNodeCatchUpTask task) {
        return taskMap.putIfAbsent(task.getNodeId(), task) == null;
    }

    /**
     * 收到日志复制的回复
     */
    public boolean onReceiveAppendEntriesResult(AppendEntriesResultMessage resultMessage, int nextLogIndex) {
        // 如果不是 catch up 中的节点的回复直接返回
        NewNodeCatchUpTask task = taskMap.get(resultMessage.getSourceNodeId());
        if (task == null) {
            return false;
        }
        // 是 catch up 中的节点
        task.onReceiveAppendEntriesResult(resultMessage, nextLogIndex);
        return true;
    }

    public boolean onReceiveInstallSnapshotResult(InstallSnapshotResultMessage resultMessage, int nextLogIndex) {
        NewNodeCatchUpTask task = taskMap.get(resultMessage.getSourceNodeId());
        if (task == null) {
            return false;
        }
        task.onReceiveInstallSnapshotResult(resultMessage, nextLogIndex);
        return true;
    }

    /**
     * 移除节点
     */
    public boolean remove(NewNodeCatchUpTask task) {
        return taskMap.remove(task.getNodeId()) != null;
    }

}
