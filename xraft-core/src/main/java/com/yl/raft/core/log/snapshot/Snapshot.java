package com.yl.raft.core.log.snapshot;

import com.yl.raft.core.node.NodeEndpoint;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.util.Set;

/**
 * Snapshot
 */
public interface Snapshot {

    /**
     * 获取最后一条日志的索引
     */
    int getLastIncludedIndex();

    /**
     * 获取最后一条日志的任期
     */
    int getLastIncludedTerm();

    /**
     * 集群配置（快照最后一条日志之前的最新集群配置，而不是快照时的系统集群配置）
     */
    @Nonnull
    Set<NodeEndpoint> getLastConfig();

    /**
     * 获取数据长度
     */
    long getDataSize();

    /**
     * 根据偏移量和长度读取数据块
     */
    @Nonnull
    SnapshotChunk readData(int offset, int length);

    /**
     * 获取数据流
     */
    @Nonnull
    InputStream getDataStream();

    void close();

}
