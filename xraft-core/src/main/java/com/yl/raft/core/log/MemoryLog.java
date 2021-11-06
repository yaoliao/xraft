package com.yl.raft.core.log;

import com.yl.raft.core.log.sequence.EntrySequence;
import com.yl.raft.core.log.sequence.MemoryEntrySequence;

/**
 * MemoryLog
 */
public class MemoryLog extends AbstractLog {

    public MemoryLog() {
        this(new MemoryEntrySequence());
    }

    MemoryLog(EntrySequence entrySequence) {
        this.entrySequence = entrySequence;
    }

}
