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

package com.nageoffer.ai.ragent.rag.evaluation.judge.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.eval.EvalProperties;
import com.nageoffer.ai.ragent.rag.evaluation.judge.SemanticEvaluationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ragent.eval", name = "workbench-enabled", havingValue = "true")
public class RagasHttpSemanticEvaluationProvider implements SemanticEvaluationProvider {

    private final EvalProperties evalProperties;

    @Override
    public boolean isAvailable() {
        EvalProperties.Ragas ragas = evalProperties.getRagas();
        if (ragas == null || !ragas.isEnabled() || StrUtil.isBlank(ragas.getEndpoint())) {
            return false;
        }
        try {
            HttpResponse resp = authorizedGet(base() + "/health")
                    .timeout(Math.min(5_000, timeoutMs()))
                    .execute();
            if (!resp.isOk()) {
                return false;
            }
            JSONObject body = JSONUtil.parseObj(resp.body());
            // judge 未配置时仍认为服务可达，由上层决定是否 skip
            return "ok".equalsIgnoreCase(body.getStr("status"));
        } catch (Exception ex) {
            log.warn("RAGAS health check failed: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public String submit(String idempotencyKey,
                         List<Map<String, Object>> snakeCaseRecords,
                         int ragasN,
                         Integer ragasLimit,
                         JudgeEndpointSpec judge) {
        Map<String, Object> body = new HashMap<>();
        body.put("schema_version", "1.0.0");
        body.put("idempotency_key", idempotencyKey);
        body.put("mode", "async");
        body.put("skip_ragas", false);
        body.put("ragas_n", Math.max(1, Math.min(3, ragasN)));
        body.put("ragas_limit", ragasLimit);
        body.put("algorithm_version", "ragas-1.0.0");
        body.put("records", snakeCaseRecords);
        body.put("metrics", List.of(
                "faithfulness",
                "answer_relevancy",
                "answer_correctness",
                "context_precision",
                "context_recall"
        ));
        if (judge != null && judge.hasAny()) {
            Map<String, Object> judgeBody = new HashMap<>();
            putIfPresent(judgeBody, "chat_model", judge.chatModel());
            putIfPresent(judgeBody, "embedding_model", judge.embeddingModel());
            putIfPresent(judgeBody, "chat_base_url", judge.chatBaseUrl());
            putIfPresent(judgeBody, "embedding_base_url", judge.embeddingBaseUrl());
            putIfPresent(judgeBody, "chat_api_key", judge.chatApiKey());
            putIfPresent(judgeBody, "embedding_api_key", judge.embeddingApiKey());
            putIfPresent(judgeBody, "chat_provider", judge.chatProvider());
            putIfPresent(judgeBody, "embedding_provider", judge.embeddingProvider());
            body.put("judge", judgeBody);
        }

        HttpRequest req = authorizedPost(base() + "/v1/evaluations/score")
                .timeout(timeoutMs())
                .body(JSONUtil.toJsonStr(body));
        if (StrUtil.isNotBlank(idempotencyKey)) {
            req.header("Idempotency-Key", idempotencyKey);
        }
        HttpResponse resp = req.execute();
        if (resp.getStatus() == 503) {
            throw new ClientException("JUDGE_NOT_CONFIGURED: " + resp.body());
        }
        if (resp.getStatus() != 200 && resp.getStatus() != 202) {
            throw new ClientException("RAGAS submit failed HTTP " + resp.getStatus() + ": " + resp.body());
        }
        JSONObject json = JSONUtil.parseObj(resp.body());
        String jobId = json.getStr("job_id");
        if (StrUtil.isBlank(jobId)) {
            throw new ClientException("RAGAS submit missing job_id");
        }
        return jobId;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            map.put(key, value.trim());
        }
    }

    @Override
    public JobSnapshot poll(String jobId) {
        try {
            HttpResponse resp = authorizedGet(base() + "/v1/evaluations/score/" + jobId)
                    .timeout(timeoutMs())
                    .execute();
            if (resp.getStatus() == 404) {
                throw new ClientException("RAGAS poll failed HTTP 404: job not found (service may have restarted)");
            }
            if (!resp.isOk()) {
                throw new ClientException("RAGAS poll failed HTTP " + resp.getStatus() + ": " + resp.body());
            }
            return parseSnapshot(JSONUtil.parseObj(resp.body()));
        } catch (ClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ClientException("RAGAS poll connection error: " + ex.getMessage());
        }
    }

    @Override
    public void cancel(String jobId) {
        try {
            authorizedPost(base() + "/v1/evaluations/score/" + jobId + "/cancel")
                    .timeout(timeoutMs())
                    .execute();
        } catch (Exception ex) {
            log.warn("RAGAS cancel failed jobId={}: {}", jobId, ex.getMessage());
        }
    }

    private JobSnapshot parseSnapshot(JSONObject json) {
        JSONObject progress = json.getJSONObject("progress");
        JSONObject token = json.getJSONObject("token_usage");
        List<Map<String, Object>> metrics = toMapList(json.getJSONArray("metrics"));
        List<Map<String, Object>> errors = toMapList(json.getJSONArray("sample_errors"));
        return new JobSnapshot(
                json.getStr("job_id"),
                json.getStr("status"),
                progress == null ? null : progress.getInt("total"),
                progress == null ? null : progress.getInt("completed"),
                progress == null ? null : progress.getInt("failed"),
                progress == null ? null : progress.getInt("skipped"),
                progress == null ? null : progress.getInt("evaluable"),
                progress == null ? null : progress.getInt("work_total"),
                progress == null ? null : progress.getInt("work_completed"),
                metrics,
                errors,
                json.getStr("error_message"),
                token == null ? Map.of() : token,
                json.getDouble("estimated_cost_usd")
        );
    }

    private List<Map<String, Object>> toMapList(JSONArray arr) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (Object o : arr) {
            if (o instanceof JSONObject jo) {
                out.add(jo);
            } else if (o != null) {
                out.add(JSONUtil.parseObj(o));
            }
        }
        return out;
    }

    private String base() {
        return StrUtil.removeSuffix(evalProperties.getRagas().getEndpoint().trim(), "/");
    }

    private int timeoutMs() {
        return Math.max(3_000, evalProperties.getRagas().getTimeoutSeconds() * 1000);
    }

    private HttpRequest authorizedGet(String url) {
        HttpRequest req = HttpRequest.get(url);
        applyToken(req);
        return req;
    }

    private HttpRequest authorizedPost(String url) {
        HttpRequest req = HttpRequest.post(url).contentType("application/json");
        applyToken(req);
        return req;
    }

    private void applyToken(HttpRequest req) {
        String token = evalProperties.getRagas().getServiceToken();
        if (StrUtil.isNotBlank(token)) {
            req.header("Authorization", "Bearer " + token.trim());
        }
    }
}
