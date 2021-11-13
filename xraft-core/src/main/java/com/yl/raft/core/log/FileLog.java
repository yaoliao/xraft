package com.yl.raft.core.log;

import com.yl.raft.core.log.sequence.FileEntrySequence;

import java.io.File;

/**
 * FileLog
 */
public class FileLog extends AbstractLog {

    private final RootDir rootDir;

    public FileLog(File baseDir) {
        rootDir = new RootDir(baseDir);

        LogGeneration latestGeneration = rootDir.getLatestGeneration();
        // TODO add log
        if (latestGeneration != null) {
            entrySequence = new FileEntrySequence(latestGeneration, latestGeneration.getLastIncludedIndex());
        } else {
            LogGeneration firstGeneration = rootDir.createFirstGeneration();
            entrySequence = new FileEntrySequence(firstGeneration, 1);
        }
    }


}
