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

package com.nageoffer.ai.ragent.knowledge.feishu;

import com.nageoffer.ai.ragent.knowledge.config.FeishuWikiImportProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 飞书 Open API 简易滑动窗口限流
 */
@Component
@RequiredArgsConstructor
public class FeishuApiRateLimiter {

    private final FeishuWikiImportProperties importProperties;
    private final Deque<Long> timestamps = new ArrayDeque<>();
    private final Object lock = new Object();

    public void acquire() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long windowMs = 60_000L;
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMs) {
                timestamps.pollFirst();
            }
            int limit = Math.max(importProperties.getRateLimitPerMinute(), 1);
            if (timestamps.size() >= limit) {
                long waitMs = windowMs - (now - timestamps.peekFirst()) + 50;
                if (waitMs > 0) {
                    try {
                        lock.wait(waitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                acquire();
                return;
            }
            timestamps.addLast(System.currentTimeMillis());
        }
    }
}
