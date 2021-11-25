package com.yl.raft.core.log.event;

/**
 * SnapshotGeneratedEvent
 */
public class SnapshotGeneratedEvent {

    private final int lastIncludedIndex;

    public SnapshotGeneratedEvent(int lastIncludedIndex) {
        this.lastIncludedIndex = lastIncludedIndex;
    }

    public int getLastIncludedIndex() {
        return lastIncludedIndex;
    }

}
