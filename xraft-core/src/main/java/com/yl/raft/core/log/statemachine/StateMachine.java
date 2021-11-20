package com.yl.raft.core.log.statemachine;

import com.yl.raft.core.log.snapshot.Snapshot;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;

/**
 * StateMachine
 */
public interface StateMachine {

    /**
     * 获取 lastApplied
     */
    int getLastApplied();

    /**
     * 应用日志
     */
    void applyLog(StateMachineContext context, int index, byte[] commandBytes, int firstLogIndex);

    void shutdown();

    /**
     * Should generate or not.
     *
     * @param firstLogIndex first log index in log files, may not be {@code 0}
     * @param lastApplied   last applied log index
     * @return true if should generate, otherwise false
     */
    boolean shouldGenerateSnapshot(int firstLogIndex, int lastApplied);

    void applySnapshot(@Nonnull Snapshot snapshot) throws IOException;

    void generateSnapshot(@Nonnull OutputStream output) throws IOException;

}
