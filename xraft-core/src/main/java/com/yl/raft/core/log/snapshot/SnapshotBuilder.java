package com.yl.raft.core.log.snapshot;

import com.yl.raft.core.rpc.message.InstallSnapshotRpc;

public interface SnapshotBuilder<T extends Snapshot> {

    /**
     * 追加日志快照
     */
    void append(InstallSnapshotRpc rpc);

    /**
     * 导出日志快照
     */
    T build();

    /**
     * 关闭日志快照
     */
    void close();

}
