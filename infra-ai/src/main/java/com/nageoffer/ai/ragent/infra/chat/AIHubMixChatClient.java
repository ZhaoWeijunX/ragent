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

package com.nageoffer.ai.ragent.infra.chat;

import com.google.gson.JsonObject;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.infra.enums.ModelProvider;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AIHubMixChatClient extends AbstractOpenAIStyleChatClient {

    @Override
    public String provider() {
        return ModelProvider.AI_HUB_MIX.getId();
    }

    /**
     * AIHubMix 的 OpenAI 兼容接口不支持 enable_thinking 参数。
     * 保留基类的通用请求体字段，但跳过该 provider 特有的 thinking 字段。
     */
    @Override
    protected void customizeRequestBody(JsonObject body, ChatRequest request) {
        // AIHubMix 不接受 enable_thinking，即使值为 false 也会返回 400。
    }

    @Override
    @RagTraceNode(name = "aihubmix-chat", type = "LLM_PROVIDER")
    public String chat(ChatRequest request, ModelTarget target) {
        return doChat(request, target);
    }

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
        return doStreamChat(request, callback, target);
    }
}
