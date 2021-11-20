package com.yl.raft.core.rpc.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * InstallSnapshotResult
 */
@AllArgsConstructor
@Getter
@ToString
public class InstallSnapshotResult {

    private final int term;

    private String messageId;

}
