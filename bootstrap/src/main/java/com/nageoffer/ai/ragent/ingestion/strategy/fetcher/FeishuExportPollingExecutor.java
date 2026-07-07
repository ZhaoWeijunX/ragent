/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.ingestion.strategy.fetcher;

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 飞书 PDF 导出任务共享轮询调度器
 * <p>
 * 将 HTTP 轮询从业务线程剥离到独立调度池；调用方仍在 {@code future.get()} 上阻塞等待结果。
 */
@Slf4j
@Component
public class FeishuExportPollingExecutor {

    private static final int SCHEDULER_THREADS = 2;
    private static final long SHUTDOWN_AWAIT_SECONDS = 10;

    private ScheduledExecutorService scheduler;

    @FunctionalInterface
    public interface PollAttempt {
        /**
         * @return 非空表示任务完成；{@code null} 表示继续轮询
         */
        String attempt();
    }

    @PostConstruct
    void init() {
        this.scheduler = Executors.newScheduledThreadPool(SCHEDULER_THREADS, namedFactory());
        log.info("FeishuExportPollingExecutor 启动: schedulerThreads={}", SCHEDULER_THREADS);
    }

    /**
     * 提交轮询并在 future 完成时返回 file_token
     */
    public CompletableFuture<String> submitAndAwait(PollAttempt pollAttempt, Duration timeout, long intervalMs) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Instant deadline = Instant.now().plus(timeout);
        long safeIntervalMs = Math.max(100L, intervalMs);

        ScheduledFuture<?>[] holder = new ScheduledFuture[1];
        Runnable poll = () -> doPoll(pollAttempt, future, deadline, holder);

        poll.run();
        if (!future.isDone()) {
            holder[0] = scheduler.scheduleAtFixedRate(poll, safeIntervalMs, safeIntervalMs, TimeUnit.MILLISECONDS);
            future.whenComplete((result, throwable) -> cancelPolling(holder));
        }
        return future;
    }

    private void doPoll(PollAttempt pollAttempt,
                        CompletableFuture<String> future,
                        Instant deadline,
                        ScheduledFuture<?>[] holder) {
        if (future.isDone()) {
            return;
        }
        try {
            String fileToken = pollAttempt.attempt();
            if (fileToken != null) {
                complete(future, fileToken, holder);
            } else if (Instant.now().isAfter(deadline)) {
                completeExceptionally(future,
                        new ClientException("飞书 PDF 导出超时"), holder);
            }
        } catch (RuntimeException e) {
            completeExceptionally(future, e, holder);
        }
    }

    private static void complete(CompletableFuture<String> future, String fileToken, ScheduledFuture<?>[] holder) {
        if (future.complete(fileToken)) {
            cancelPolling(holder);
        }
    }

    private static void completeExceptionally(CompletableFuture<String> future,
                                              Throwable error,
                                              ScheduledFuture<?>[] holder) {
        if (future.completeExceptionally(error)) {
            cancelPolling(holder);
        }
    }

    private static void cancelPolling(ScheduledFuture<?>[] holder) {
        ScheduledFuture<?> task = holder[0];
        if (task != null) {
            task.cancel(false);
        }
    }

    @PreDestroy
    void shutdown() {
        if (scheduler == null) {
            return;
        }
        log.info("FeishuExportPollingExecutor 优雅停机中，等待 active 任务最多 {}s", SHUTDOWN_AWAIT_SECONDS);
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("FeishuExportPollingExecutor 强制停机");
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    private static ThreadFactory namedFactory() {
        AtomicInteger seq = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, "feishu-export-poll-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }
}
