package com.yl.raft.core.log.sequence;

import com.yl.raft.core.log.entry.Entry;
import com.yl.raft.core.log.entry.EntryMeta;
import com.yl.raft.core.node.NodeEndpoint;

import java.util.List;
import java.util.Set;

/**
 * EntrySequence
 */
public interface EntrySequence {

    boolean isEmpty();

    int getFirstLogIndex();

    int getLastLogIndex();

    int getNextLogIndex();

    List<Entry> subList(int fromIndex);

    // [fromIndex, toIndex)
    List<Entry> subList(int fromIndex, int toIndex);

    boolean isEntryPresent(int index);

    EntryMeta getEntryMeta(int index);

    Entry getEntry(int index);

    Entry getLastEntry();

    void append(Entry entry);

    void append(List<Entry> entries);

    void commit(int index);

    int getCommitIndex();

    void removeAfter(int index);

    GroupConfigEntryList buildGroupConfigEntryList(Set<NodeEndpoint> initialGroup);

    void close();

}
