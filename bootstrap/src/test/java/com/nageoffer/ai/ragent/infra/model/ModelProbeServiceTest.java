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

package com.nageoffer.ai.ragent.infra.model;

import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.framework.exception.RemoteException;
import com.nageoffer.ai.ragent.infra.chat.ChatClient;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.infra.chat.StreamCancellationHandle;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.embedding.EmbeddingClient;
import com.nageoffer.ai.ragent.infra.enums.ModelCapability;
import com.nageoffer.ai.ragent.infra.rerank.RerankClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelProbeServiceTest {

    private ModelProbeService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void probeChatSuccess() {
        AIModelProperties properties = baseProperties();
        service = new ModelProbeService(
                properties,
                List.of(chatClient("bailian", "ok")),
                List.of(),
                List.of());

        ModelProbeResult result = service.probeOne(ModelCapability.CHAT, "qwen-plus");

        assertTrue(result.isHealthy());
        assertEquals("qwen-plus", result.getModelId());
        assertEquals("bailian", result.getProvider());
    }

    @Test
    void probeChatFailureDoesNotThrow() {
        AIModelProperties properties = baseProperties();
        service = new ModelProbeService(
                properties,
                List.of(chatClientFailure("bailian")),
                List.of(),
                List.of());

        ModelProbeResult result = service.probeOne(ModelCapability.CHAT, "qwen-plus");

        assertFalse(result.isHealthy());
        assertEquals("Model does not exist", result.getErrorMessage());
    }

    @Test
    void probeSkipsDisabledCandidate() {
        AIModelProperties properties = baseProperties();
        properties.getChat().getCandidates().get(0).setEnabled(false);
        service = new ModelProbeService(
                properties,
                List.of(chatClient("bailian", "ok")),
                List.of(),
                List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.probeOne(ModelCapability.CHAT, "qwen-plus"));
    }

    @Test
    void probeEmbeddingSuccess() {
        AIModelProperties properties = baseProperties();
        properties.getEmbedding().setCandidates(List.of(candidate("qwen-emb", "siliconflow", "Qwen/Qwen3-Embedding-8B", 1)));
        properties.getProviders().put("siliconflow", provider("https://api.siliconflow.cn"));

        service = new ModelProbeService(
                properties,
                List.of(),
                List.of(embeddingClient("siliconflow")),
                List.of());

        ModelProbeResult result = service.probeOne(ModelCapability.EMBEDDING, "qwen-emb");

        assertTrue(result.isHealthy());
        assertEquals(ModelCapability.EMBEDDING, result.getCapability());
    }

    @Test
    void probeRerankSuccess() {
        AIModelProperties properties = baseProperties();
        properties.getRerank().setCandidates(List.of(candidate("qwen-rerank", "bailian", "qwen3-rerank", 1)));

        service = new ModelProbeService(
                properties,
                List.of(),
                List.of(),
                List.of(rerankClient("bailian")));

        ModelProbeResult result = service.probeOne(ModelCapability.RERANK, "qwen-rerank");

        assertTrue(result.isHealthy());
        assertEquals(ModelCapability.RERANK, result.getCapability());
    }

    private static AIModelProperties baseProperties() {
        AIModelProperties properties = new AIModelProperties();
        properties.getChat().setCandidates(List.of(candidate("qwen-plus", "bailian", "qwen-plus-latest", 1)));
        properties.getProviders().put("bailian", provider("https://dashscope.aliyuncs.com"));
        AIModelProperties.Probe probe = new AIModelProperties.Probe();
        probe.setTimeoutSeconds(5);
        probe.setParallelism(2);
        properties.setProbe(probe);
        return properties;
    }

    private static AIModelProperties.ModelCandidate candidate(String id, String provider, String model, int priority) {
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId(id);
        candidate.setProvider(provider);
        candidate.setModel(model);
        candidate.setPriority(priority);
        candidate.setEnabled(true);
        return candidate;
    }

    private static AIModelProperties.ProviderConfig provider(String url) {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setUrl(url);
        provider.setApiKey("test-key");
        provider.setEndpoints(Map.of("chat", "/v1/chat/completions"));
        return provider;
    }

    private static ChatClient chatClient(String provider, String response) {
        return new ChatClient() {
            @Override
            public String provider() {
                return provider;
            }

            @Override
            public String chat(ChatRequest request, ModelTarget target) {
                return response;
            }

            @Override
            public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static ChatClient chatClientFailure(String provider) {
        return new ChatClient() {
            @Override
            public String provider() {
                return provider;
            }

            @Override
            public String chat(ChatRequest request, ModelTarget target) {
                throw new RemoteException("Model does not exist");
            }

            @Override
            public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static EmbeddingClient embeddingClient(String provider) {
        return new EmbeddingClient() {
            @Override
            public String provider() {
                return provider;
            }

            @Override
            public List<Float> embed(String text, ModelTarget target) {
                return List.of(0.1f, 0.2f);
            }

            @Override
            public List<List<Float>> embedBatch(List<String> texts, ModelTarget target) {
                return List.of(List.of(0.1f, 0.2f));
            }
        };
    }

    private static RerankClient rerankClient(String provider) {
        return new RerankClient() {
            @Override
            public String provider() {
                return provider;
            }

            @Override
            public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN, ModelTarget target) {
                return candidates.subList(0, Math.min(topN, candidates.size()));
            }
        };
    }
}
