package com.yl.raft.core.log;

import com.yl.raft.core.log.entry.Entry;
import com.yl.raft.core.log.entry.EntryMeta;
import com.yl.raft.core.log.entry.GeneralEntry;
import com.yl.raft.core.log.entry.NoOpEntry;
import com.yl.raft.core.log.statemachine.StateMachine;
import com.yl.raft.core.node.NodeId;
import com.yl.raft.core.rpc.message.AppendEntriesRpc;

import java.util.List;

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
    boolean appendEntriesFromLeader(int prevLogIndex, int prevLogTerm, List<Entry> entries);

    /**
     * 推进 commitIndex
     */
    void advanceCommitIndex(int newCommitIndex, int currentTerm);

    /**
     * 设置状态机
     */
     void setStateMachine(StateMachine stateMachine);

    /**
     * 关闭
     */
    void close();

}
