package com.yl.raft.core.log.snapshot;

import com.yl.raft.core.node.NodeEndpoint;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

/**
 * EmptySnapshot
 */
public class EmptySnapshot implements Snapshot {

    @Override
    public int getLastIncludedIndex() {
        return 0;
    }

    @Override
    public int getLastIncludedTerm() {
        return 0;
    }

    @Nonnull
    @Override
    public Set<NodeEndpoint> getLastConfig() {
        return Collections.emptySet();
    }

    @Override
    public long getDataSize() {
        return 0;
    }

    @Nonnull
    @Override
    public SnapshotChunk readData(int offset, int length) {
        if (offset == 0) {
            return new SnapshotChunk(new byte[0], true);
        }
        throw new IllegalArgumentException("offset > 0");
    }

    @Nonnull
    @Override
    public InputStream getDataStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public void close() {

    }
}
