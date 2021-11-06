package com.yl.raft.core.log.sequence;

import com.yl.raft.core.log.entry.EntryMeta;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * EntryIndexItem
 */
@Getter
@AllArgsConstructor
public class EntryIndexItem {

    private final int index;
    private final long offset;
    private final int kind;
    private final int term;

    EntryMeta toEntryMeta() {
        return new EntryMeta(kind, index, term);
    }
}
