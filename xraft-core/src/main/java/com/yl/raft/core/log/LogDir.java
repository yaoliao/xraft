package com.yl.raft.core.log;

import java.io.File;

/**
 * LogDir
 */
public interface LogDir {

    void initialize();

    boolean exists();

    File getSnapshotFile();

    File getEntriesFile();

    File getEntryOffsetIndexFile();

    File get();

    boolean renameTo(LogDir logDir);
}
