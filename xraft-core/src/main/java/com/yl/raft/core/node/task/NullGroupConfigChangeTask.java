package com.yl.raft.core.node.task;


import com.yl.raft.core.log.entry.GroupConfigEntry;

public class NullGroupConfigChangeTask implements GroupConfigChangeTask {

    @Override
    public void onLogCommitted(GroupConfigEntry entry) {
    }

    @Override
    public GroupConfigChangeTaskResult call() throws Exception {
        return null;
    }

    @Override
    public void setGroupConfigEntry(GroupConfigEntry entry) {
    }

    @Override
    public String toString() {
        return "NullGroupConfigChangeTask{}";
    }

}
