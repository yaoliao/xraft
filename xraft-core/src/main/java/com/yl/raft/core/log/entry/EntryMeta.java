package com.yl.raft.core.log.entry;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * EntryMeta
 */
@Data
@AllArgsConstructor
public class EntryMeta {

    private final int kind;
    private final int index;
    private final int term;
}
