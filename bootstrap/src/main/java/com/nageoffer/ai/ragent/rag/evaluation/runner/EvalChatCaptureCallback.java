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

package com.nageoffer.ai.ragent.rag.evaluation.runner;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.rag.evaluation.constant.EvalWorkbenchConstants;
import lombok.Getter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 捕获真实 Chat 流式结果（TTFT / response / thinking），并透传到正式 SSE 事件处理器。
 */
public class EvalChatCaptureCallback implements StreamCallback {

    private final StreamCallback delegate;
    private final long startedAtMs;
    private final StringBuilder response = new StringBuilder();
    private final StringBuilder thinking = new StringBuilder();
    private final CountDownLatch done = new CountDownLatch(1);
    private final AtomicBoolean firstTokenSeen = new AtomicBoolean(false);

    @Getter
    private volatile Long firstTokenMs;
    @Getter
    private volatile String finalStatus = EvalWorkbenchConstants.RECORD_UNKNOWN;
    @Getter
    private volatile String errorMessage;

    public EvalChatCaptureCallback(StreamCallback delegate) {
        this.delegate = delegate;
        this.startedAtMs = System.currentTimeMillis();
    }

    @Override
    public void onReplyToMessageId(String messageId) {
        delegate.onReplyToMessageId(messageId);
    }

    @Override
    public void onContent(String content) {
        if (StrUtil.isNotBlank(content) && firstTokenSeen.compareAndSet(false, true)) {
            firstTokenMs = System.currentTimeMillis() - startedAtMs;
        }
        if (content != null) {
            response.append(content);
        }
        delegate.onContent(content);
    }

    @Override
    public void onThinking(String content) {
        if (content != null) {
            thinking.append(content);
        }
        delegate.onThinking(content);
    }

    @Override
    public void onSources(java.util.List<com.nageoffer.ai.ragent.framework.convention.SourceRef> sources) {
        delegate.onSources(sources);
    }

    @Override
    public void onGroundingChunks(java.util.List<com.nageoffer.ai.ragent.framework.convention.GroundingChunk> chunks) {
        delegate.onGroundingChunks(chunks);
    }

    @Override
    public void onComplete() {
        try {
            finalStatus = EvalWorkbenchConstants.RECORD_SUCCESS;
            delegate.onComplete();
        } finally {
            done.countDown();
        }
    }

    @Override
    public void onError(Throwable error) {
        try {
            finalStatus = EvalWorkbenchConstants.RECORD_ERROR;
            errorMessage = error == null ? "chat error" : error.getMessage();
            delegate.onError(error);
        } finally {
            done.countDown();
        }
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return done.await(timeout, unit);
    }

    public String getResponse() {
        return response.toString();
    }

    public String getThinking() {
        return thinking.isEmpty() ? null : thinking.toString();
    }

    public long getTotalLatencyMs() {
        return System.currentTimeMillis() - startedAtMs;
    }

    public void markTimeout() {
        if (done.getCount() > 0) {
            finalStatus = EvalWorkbenchConstants.RECORD_ERROR;
            errorMessage = "sample timeout";
            done.countDown();
        }
    }

    public void markCancelled() {
        if (done.getCount() > 0) {
            finalStatus = EvalWorkbenchConstants.RECORD_CANCELLED;
            errorMessage = "run cancelled";
            done.countDown();
        }
    }
}
