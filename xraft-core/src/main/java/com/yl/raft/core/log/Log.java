package com.yl.raft.core.log;

import com.yl.raft.core.log.entry.*;
import com.yl.raft.core.log.statemachine.StateMachine;
import com.yl.raft.core.node.NodeEndpoint;
import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.message.AppendEntriesRpc;
import com.yl.raft.core.rpc.message.InstallSnapshotRpc;

import java.util.List;
import java.util.Set;

public interface Log {

    int ALL_ENTRIES = -1;

    /**
     * 获取最新的日志的元信息
     */
    EntryMeta getLastEntryMeta();

    /**
     * 创建 AppendEntriesRpc 消息
     */
    AppendEntriesRpc createAppendEntriesRpc(int term, NodeId selfId, int nextIndex, int maxEntries);

    /**
     * 获取下一条日志索引
     */
    int getNextIndex();

    /**
     * 获取当前 commitIndex
     */
    int getCommitIndex();

    /**
     * 日志比较
     */
    boolean isNewerThan(int lastLogIndex, int lastLogTerm);

    /**
     * 增加一个空的日志条目
     */
    NoOpEntry appendEntry(int term);

    /**
     * 增加一个正常的日志条目
     */
    GeneralEntry appendEntry(int term, byte[] command);

    /**
     * 追加来自 leader 的日志条目
     */
    AppendEntriesState appendEntriesFromLeader(int prevLogIndex, int prevLogTerm, List<Entry> entries);

    /**
     * 推进 commitIndex
     */
    void advanceCommitIndex(int newCommitIndex, int currentTerm);

    /**
     * 设置状态机
     */
    void setStateMachine(StateMachine stateMachine);

    /**
     * Install snapshot.
     *
     * @param rpc rpc
     * @return install snapshot state
     */
    InstallSnapshotState installSnapshot(InstallSnapshotRpc rpc);

    /**
     * Generate snapshot.
     *
     * @param lastIncludedIndex last included index
     * @param groupConfig       group config
     */
    void generateSnapshot(int lastIncludedIndex, Set<NodeEndpoint> groupConfig);

    void snapshotGenerated(int lastIncludedIndex);

    /**
     * Create install snapshot rpc from log.
     *
     * @param term   current term
     * @param selfId self node id
     * @param offset data offset
     * @param length data length
     * @return install snapshot rpc
     */
    InstallSnapshotRpc createInstallSnapshotRpc(int term, NodeId selfId, int offset, int length);

    Set<NodeEndpoint> getLastGroup();

    /**
     * 添加一个 addNode 的日志条目
     */
    AddNodeEntry appendEntryForAddNode(int term, Set<NodeEndpoint> nodeEndpoints, NodeEndpoint newNodeEndpoint);

    /**
     * 添加一个 removeNode 的日志条目
     */
    RemoveNodeEntry appendEntryForRemoveNode(int term, Set<NodeEndpoint> nodeEndpoints, NodeId nodeToRemove);

    /**
     * 关闭
     */
    void close();

}
