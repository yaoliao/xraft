package com.yl.raft.core.log.snapshot;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * SnapshotChunk
 */
@AllArgsConstructor
@Getter
@ToString
public class SnapshotChunk {

    private final byte[] bytes;

    /**
     * 是否是最后的数据
     */
    private final boolean lastChunk;
}
