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

package com.nageoffer.ai.ragent.rag.evaluation.config;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.alibaba.ttl.threadpool.TtlExecutors;
import com.nageoffer.ai.ragent.rag.eval.EvalProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 评测工作台配置：仅在 {@code app.eval.workbench-enabled=true} 时注册任务资源。
 * <p>
 * 不改动聊天线程池；Mapper 由启动类统一扫描。
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.eval", name = "workbench-enabled", havingValue = "true")
public class EvaluationWorkbenchConfiguration {

    private final EvalProperties evalProperties;

    /**
     * 评测录制专用线程池，与 chatEntryExecutor 隔离。
     */
    @Bean(name = "evalRecordExecutor")
    public Executor evalRecordExecutor() {
        int size = Math.max(1, evalProperties.getRecordConcurrency());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                size,
                size,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                ThreadFactoryBuilder.create()
                        .setNamePrefix("eval_record_executor_")
                        .build(),
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return TtlExecutors.getTtlExecutor(executor);
    }

    /**
     * RAGAS 异步评分线程池：与录制池隔离，避免 LLM-judge 长轮询占满录制并发。
     */
    @Bean(name = "evalRagasExecutor")
    public Executor evalRagasExecutor() {
        int size = Math.max(1, Math.min(4, evalProperties.getRagas().getConcurrency()));
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                size,
                size,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                ThreadFactoryBuilder.create()
                        .setNamePrefix("eval_ragas_executor_")
                        .build(),
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return TtlExecutors.getTtlExecutor(executor);
    }
}
