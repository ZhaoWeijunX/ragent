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

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.framework.exception.RemoteException;
import com.nageoffer.ai.ragent.infra.chat.ChatClient;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.embedding.EmbeddingClient;
import com.nageoffer.ai.ragent.infra.enums.ModelCapability;
import com.nageoffer.ai.ragent.infra.enums.ModelProvider;
import com.nageoffer.ai.ragent.infra.rerank.RerankClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模型主动探测服务：旁路生产路由，对候选模型发起最小化真实 API 调用。
 */
@Slf4j
@Service
public class ModelProbeService {

    private static final String PROBE_TEXT = "ping";
    private static final List<RetrievedChunk> RERANK_PROBE_CANDIDATES = List.of(
            RetrievedChunk.builder().id("probe-1").text("This is a probe document for rerank health check.").score(0.5f).build(),
            RetrievedChunk.builder().id("probe-2").text("Another probe document to validate rerank connectivity.").score(0.4f).build()
    );

    private static final AtomicInteger PROBE_THREAD_COUNTER = new AtomicInteger();

    private final AIModelProperties properties;
    private final Map<String, ChatClient> chatClientsByProvider;
    private final Map<String, EmbeddingClient> embeddingClientsByProvider;
    private final Map<String, RerankClient> rerankClientsByProvider;
    private final ExecutorService probeExecutor;

    public ModelProbeService(
            AIModelProperties properties,
            List<ChatClient> chatClients,
            List<EmbeddingClient> embeddingClients,
            List<RerankClient> rerankClients) {
        this.properties = properties;
        this.chatClientsByProvider = chatClients.stream()
                .collect(Collectors.toMap(ChatClient::provider, Function.identity(), (a, b) -> a));
        this.embeddingClientsByProvider = embeddingClients.stream()
                .collect(Collectors.toMap(EmbeddingClient::provider, Function.identity(), (a, b) -> a));
        this.rerankClientsByProvider = rerankClients.stream()
                .collect(Collectors.toMap(RerankClient::provider, Function.identity(), (a, b) -> a));
        int parallelism = resolveParallelism();
        this.probeExecutor = Executors.newFixedThreadPool(parallelism, namedThreadFactory());
    }

    public List<ModelProbeResult> probeAll() {
        List<ModelProbeResult> results = new ArrayList<>();
        for (ModelCapability capability : EnumSet.of(ModelCapability.CHAT, ModelCapability.EMBEDDING, ModelCapability.RERANK)) {
            results.addAll(probe(capability));
        }
        return results;
    }

    public List<ModelProbeResult> probe(ModelCapability capability) {
        AIModelProperties.ModelGroup group = resolveGroup(capability);
        if (group == null || group.getCandidates() == null) {
            return List.of();
        }
        List<ProbeTask> tasks = group.getCandidates().stream()
                .filter(this::isEnabled)
                .map(candidate -> new ProbeTask(capability, candidate))
                .toList();
        return runInParallel(tasks);
    }

    public ModelProbeResult probeOne(ModelCapability capability, String modelId) {
        if (!StringUtils.hasText(modelId)) {
            throw new IllegalArgumentException("modelId is required");
        }
        AIModelProperties.ModelCandidate candidate = findCandidate(capability, modelId.trim());
        if (candidate == null) {
            throw new IllegalArgumentException("Model candidate not found: " + modelId);
        }
        if (!isEnabled(candidate)) {
            throw new IllegalArgumentException("Model candidate is disabled: " + modelId);
        }
        return probeCandidate(capability, candidate);
    }

    @PreDestroy
    void shutdown() {
        probeExecutor.shutdown();
        try {
            if (!probeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                probeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            probeExecutor.shutdownNow();
        }
    }

    private List<ModelProbeResult> runInParallel(List<ProbeTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        long timeoutSeconds = resolveTimeoutSeconds();
        List<CompletableFuture<ModelProbeResult>> futures = new ArrayList<>(tasks.size());
        for (ProbeTask task : tasks) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> probeCandidate(task.capability(), task.candidate()),
                    probeExecutor));
        }
        List<ModelProbeResult> results = new ArrayList<>(tasks.size());
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<ModelProbeResult> future = futures.get(i);
            ProbeTask task = tasks.get(i);
            long startedAt = System.nanoTime();
            try {
                results.add(future.get(timeoutSeconds, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                future.cancel(true);
                results.add(timeoutResult(task, startedAt));
            } catch (Exception e) {
                results.add(failureResult(task.capability(), task.candidate(), startedAt, e));
            }
        }
        return results;
    }

    private ModelProbeResult probeCandidate(ModelCapability capability, AIModelProperties.ModelCandidate candidate) {
        String modelId = resolveId(candidate);
        long startedAt = System.nanoTime();
        ModelTarget target = buildProbeTarget(candidate);
        if (target == null) {
            return ModelProbeResult.builder()
                    .capability(capability)
                    .modelId(modelId)
                    .provider(candidate.getProvider())
                    .model(candidate.getModel())
                    .healthy(false)
                    .latencyMs(elapsedMs(startedAt))
                    .errorMessage("Provider configuration missing: " + candidate.getProvider())
                    .build();
        }
        try {
            invokeProbe(capability, target);
            return ModelProbeResult.builder()
                    .capability(capability)
                    .modelId(modelId)
                    .provider(candidate.getProvider())
                    .model(candidate.getModel())
                    .healthy(true)
                    .latencyMs(elapsedMs(startedAt))
                    .build();
        } catch (Exception e) {
            return failureResult(capability, candidate, startedAt, e);
        }
    }

    private void invokeProbe(ModelCapability capability, ModelTarget target) {
        String provider = target.candidate().getProvider();
        switch (capability) {
            case CHAT -> {
                ChatClient client = chatClientsByProvider.get(provider);
                if (client == null) {
                    throw new IllegalStateException("Chat client missing for provider: " + provider);
                }
                ChatRequest request = ChatRequest.builder()
                        .messages(List.of(ChatMessage.user(PROBE_TEXT)))
                        .maxTokens(1)
                        .temperature(0.0)
                        .thinking(false)
                        .build();
                client.chat(request, target);
            }
            case EMBEDDING -> {
                EmbeddingClient client = embeddingClientsByProvider.get(provider);
                if (client == null) {
                    throw new IllegalStateException("Embedding client missing for provider: " + provider);
                }
                List<Float> vector = client.embed(PROBE_TEXT, target);
                if (vector == null || vector.isEmpty()) {
                    throw new IllegalStateException("Embedding response is empty");
                }
            }
            case RERANK -> {
                RerankClient client = rerankClientsByProvider.get(provider);
                if (client == null) {
                    throw new IllegalStateException("Rerank client missing for provider: " + provider);
                }
                List<RetrievedChunk> reranked = client.rerank(PROBE_TEXT, RERANK_PROBE_CANDIDATES, 1, target);
                if (reranked == null || reranked.isEmpty()) {
                    throw new IllegalStateException("Rerank response is empty");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported capability: " + capability);
        }
    }

    private ModelProbeResult failureResult(
            ModelCapability capability,
            AIModelProperties.ModelCandidate candidate,
            long startedAt,
            Exception error) {
        return ModelProbeResult.builder()
                .capability(capability)
                .modelId(resolveId(candidate))
                .provider(candidate.getProvider())
                .model(candidate.getModel())
                .healthy(false)
                .latencyMs(elapsedMs(startedAt))
                .errorMessage(resolveErrorMessage(error))
                .build();
    }

    private ModelProbeResult timeoutResult(ProbeTask task, long startedAt) {
        return ModelProbeResult.builder()
                .capability(task.capability())
                .modelId(resolveId(task.candidate()))
                .provider(task.candidate().getProvider())
                .model(task.candidate().getModel())
                .healthy(false)
                .latencyMs(elapsedMs(startedAt))
                .errorMessage("probe timeout")
                .build();
    }

    private ModelTarget buildProbeTarget(AIModelProperties.ModelCandidate candidate) {
        AIModelProperties.ProviderConfig provider = properties.getProviders().get(candidate.getProvider());
        if (provider == null && !ModelProvider.NOOP.matches(candidate.getProvider())) {
            return null;
        }
        return new ModelTarget(resolveId(candidate), candidate, provider);
    }

    private AIModelProperties.ModelCandidate findCandidate(ModelCapability capability, String modelId) {
        AIModelProperties.ModelGroup group = resolveGroup(capability);
        if (group == null || group.getCandidates() == null) {
            return null;
        }
        return group.getCandidates().stream()
                .filter(Objects::nonNull)
                .filter(candidate -> modelId.equals(resolveId(candidate)))
                .findFirst()
                .orElse(null);
    }

    private AIModelProperties.ModelGroup resolveGroup(ModelCapability capability) {
        return switch (capability) {
            case CHAT -> properties.getChat();
            case EMBEDDING -> properties.getEmbedding();
            case RERANK -> properties.getRerank();
            default -> null;
        };
    }

    private boolean isEnabled(AIModelProperties.ModelCandidate candidate) {
        return candidate != null && !Boolean.FALSE.equals(candidate.getEnabled());
    }

    private String resolveId(AIModelProperties.ModelCandidate candidate) {
        if (StrUtil.isNotBlank(candidate.getId())) {
            return candidate.getId();
        }
        return String.format("%s::%s",
                Objects.toString(candidate.getProvider(), "unknown"),
                Objects.toString(candidate.getModel(), "unknown"));
    }

    private int resolveParallelism() {
        AIModelProperties.Probe probe = properties.getProbe();
        if (probe == null || probe.getParallelism() == null || probe.getParallelism() < 1) {
            return 4;
        }
        return probe.getParallelism();
    }

    private long resolveTimeoutSeconds() {
        AIModelProperties.Probe probe = properties.getProbe();
        if (probe == null || probe.getTimeoutSeconds() == null || probe.getTimeoutSeconds() < 1) {
            return 15L;
        }
        return probe.getTimeoutSeconds();
    }

    private long elapsedMs(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private String resolveErrorMessage(Exception error) {
        if (error == null) {
            return "unknown error";
        }
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        if (current instanceof RemoteException remoteException && StringUtils.hasText(remoteException.getMessage())) {
            return remoteException.getMessage();
        }
        return StringUtils.hasText(current.getMessage()) ? current.getMessage() : current.getClass().getSimpleName();
    }

    private ThreadFactory namedThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("model-probe-" + PROBE_THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private record ProbeTask(ModelCapability capability, AIModelProperties.ModelCandidate candidate) {
    }
}
