package com.yl.raft.core.log.entry;

import lombok.ToString;

/**
 * NoOpEntry
 */
@ToString
public class NoOpEntry extends AbstractEntry {

    public NoOpEntry(int index, int term) {
        super(KIND_NO_OP, index, term);
    }

    @Override
    public byte[] getCommandBytes() {
        return new byte[0];
    }
}
