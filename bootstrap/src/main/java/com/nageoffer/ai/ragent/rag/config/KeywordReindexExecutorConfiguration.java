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

package com.nageoffer.ai.ragent.rag.config;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.alibaba.ttl.threadpool.TtlExecutors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnProperty(prefix = "rag.keyword", name = "type", havingValue = "es")
public class KeywordReindexExecutorConfiguration {

    @Bean(name = "keywordReindexExecutor")
    public Executor keywordReindexExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1),
                ThreadFactoryBuilder.create().setNamePrefix("keyword_reindex_executor_").build(),
                new ThreadPoolExecutor.AbortPolicy());
        return TtlExecutors.getTtlExecutor(executor);
    }
}
