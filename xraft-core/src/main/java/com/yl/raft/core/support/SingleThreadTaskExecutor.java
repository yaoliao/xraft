package com.yl.raft.core.support;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * SingleThreadTaskExecutor
 */
@Slf4j
public class SingleThreadTaskExecutor implements TaskExecutor {

    private final ExecutorService executorService;

    public SingleThreadTaskExecutor() {
        this(Executors.defaultThreadFactory());
    }

    public SingleThreadTaskExecutor(String name) {
        this(r -> new Thread(r, name));
    }

    private SingleThreadTaskExecutor(ThreadFactory threadFactory) {
        executorService = Executors.newSingleThreadExecutor(threadFactory);
    }

    @NonNull
    @Override
    public Future<?> submit(@NonNull Runnable task) {
        // 直接使用 executorService.submit(task) 会把异常吃掉了，外层也没有去判断异常。这里再包装一下
        CompletableFuture<Void> future = CompletableFuture.runAsync(task, executorService);
        future.whenComplete((v, t) -> {
            if (t != null) {
                log.error("SingleThreadTaskExecutor execute task error.", t);
            }
        });
        return future;
    }

    @NonNull
    @Override
    public <V> Future<V> submit(@NonNull Callable<V> task) {
        return executorService.submit(task);
    }

    @Override
    public void shutdown() throws InterruptedException {
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.SECONDS);
    }
}
