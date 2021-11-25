package com.yl.raft.core.node.task;

import com.yl.raft.core.log.entry.GroupConfigEntry;

import java.util.concurrent.Callable;

public interface GroupConfigChangeTask extends Callable<GroupConfigChangeTaskResult> {

    GroupConfigChangeTask NONE = new NullGroupConfigChangeTask();

    void setGroupConfigEntry(GroupConfigEntry entry);

    void onLogCommitted(GroupConfigEntry entry);

}
