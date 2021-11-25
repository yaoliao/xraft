package com.yl.raft.core.node.task;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeoutException;

/**
 * GroupConfigChangeTaskReference
 */
public interface GroupConfigChangeTaskReference {

    @Nonnull
    GroupConfigChangeTaskResult getResult() throws InterruptedException;

    @Nonnull
    GroupConfigChangeTaskResult getResult(long timeout) throws InterruptedException, TimeoutException;

    void cancel();

    default void awaitDone(long timeout) throws TimeoutException, InterruptedException {
        if (timeout == 0) {
            getResult();
        } else {
            getResult(timeout);
        }
    }

}
