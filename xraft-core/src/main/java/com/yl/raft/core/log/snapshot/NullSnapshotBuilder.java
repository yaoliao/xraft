package com.yl.raft.core.log.snapshot;

import com.yl.raft.core.rpc.message.InstallSnapshotRpc;

/**
 * NullSnapshotBuilder
 */
public class NullSnapshotBuilder implements SnapshotBuilder {

    @Override
    public void append(InstallSnapshotRpc rpc) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Snapshot build() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
    }
}
