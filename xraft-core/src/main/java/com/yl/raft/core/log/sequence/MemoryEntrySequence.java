package com.yl.raft.core.log.sequence;

import com.yl.raft.core.log.entry.Entry;
import com.yl.raft.core.log.entry.GroupConfigEntry;
import com.yl.raft.core.node.NodeEndpoint;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * MemoryEntrySequence
 */
@Slf4j
@ToString
public class MemoryEntrySequence extends AbstractEntrySequence {

    private final List<Entry> entries = new ArrayList<>();

    private int commitIndex;


    public MemoryEntrySequence() {
        // nextLogIndex 从 1 开始，而不是 0 开始
        this(1);
    }

    public MemoryEntrySequence(int logIndexOffset) {
        super(logIndexOffset);
    }

    @Override
    protected List<Entry> doSubList(int fromIndex, int toIndex) {
        return entries.subList(fromIndex - logIndexOffset, toIndex - logIndexOffset);
    }

    @Override
    protected Entry doGetEntry(int index) {
        return entries.get(index - logIndexOffset);
    }

    @Override
    protected void doAppend(Entry entry) {
        entries.add(entry);
    }

    @Override
    protected void doRemoveAfter(int index) {
        if (index < doGetFirstLogIndex()) {
            entries.clear();
            nextLogIndex = logIndexOffset;
        } else {
            entries.subList(index - logIndexOffset + 1, entries.size()).clear();
            nextLogIndex = index + 1;
        }
    }

    @Override
    public void commit(int index) {
        this.commitIndex = index;
    }

    @Override
    public int getCommitIndex() {
        return commitIndex;
    }

    @Override
    public GroupConfigEntryList buildGroupConfigEntryList(Set<NodeEndpoint> initialGroup) {
        GroupConfigEntryList list = new GroupConfigEntryList(initialGroup);
        for (Entry entry : entries) {
            if (entry instanceof GroupConfigEntry) {
                list.add((GroupConfigEntry) entry);
            }
        }
        return list;
    }

    @Override
    public void close() {

    }
}
