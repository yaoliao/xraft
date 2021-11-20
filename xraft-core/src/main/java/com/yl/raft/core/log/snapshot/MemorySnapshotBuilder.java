package com.yl.raft.core.log.snapshot;

import com.yl.raft.core.log.LogException;
import com.yl.raft.core.rpc.message.InstallSnapshotRpc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * MemorySnapshotBuilder
 */
public class MemorySnapshotBuilder extends AbstractSnapshotBuilder<MemorySnapshot> {

    private final ByteArrayOutputStream output;

    public MemorySnapshotBuilder(InstallSnapshotRpc firstRpc) {
        super(firstRpc);
        output = new ByteArrayOutputStream();

        try {
            output.write(firstRpc.getData());
        } catch (IOException e) {
            throw new LogException(e);
        }
    }

    @Override
    protected void doWrite(byte[] data) throws IOException {
        output.write(data);
    }

    @Override
    public MemorySnapshot build() {
        return new MemorySnapshot(lastIncludedIndex, lastIncludedTerm, output.toByteArray(), lastConfig);
    }

    @Override
    public void close() {
    }
}
