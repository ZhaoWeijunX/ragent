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

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.rag.controller.request.RagTraceRunPageRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.RagTraceRunVO;
import com.nageoffer.ai.ragent.rag.eval.EvalProperties;
import com.nageoffer.ai.ragent.rag.eval.EvalResponse;
import com.nageoffer.ai.ragent.rag.evaluation.constant.EvalWorkbenchConstants;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalCaseDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalRecordDO;
import com.nageoffer.ai.ragent.rag.evaluation.support.EvalJsonSupport;
import com.nageoffer.ai.ragent.rag.service.RagTraceQueryService;
import com.nageoffer.ai.ragent.rag.service.handler.StreamCallbackFactory;
import com.nageoffer.ai.ragent.rag.service.pipeline.StreamChatContext;
import com.nageoffer.ai.ragent.rag.service.pipeline.StreamChatPipeline;
import com.nageoffer.ai.ragent.rag.trace.StreamChatTraceRunner;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 双路径单样本录制：真实 Chat 管线 + 旁路检索证据 + taskId→traceId。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvalDualPathSampleRecorder {

    private static final int TRACE_RETRIES = 10;
    private static final long TRACE_INTERVAL_MS = 300L;

    private final StreamChatPipeline chatPipeline;
    private final StreamChatTraceRunner traceRunner;
    private final StreamCallbackFactory callbackFactory;
    private final EvalBypassEvidenceCollector bypassEvidenceCollector;
    private final RagTraceQueryService ragTraceQueryService;
    private final EvalProperties evalProperties;

    public SampleRecordResult record(EvalCaseDO evalCase) {
        Date startedAt = new Date();
        String conversationId = IdUtil.getSnowflakeNextIdStr();
        String taskId = IdUtil.getSnowflakeNextIdStr();
        long timeoutSeconds = Math.max(1, evalProperties.getSampleTimeoutSeconds());
        SseEmitter emitter = new SseEmitter(TimeUnit.SECONDS.toMillis(timeoutSeconds + 30));
        StreamCallback chatHandler = callbackFactory.createChatEventHandler(emitter, conversationId, taskId);
        EvalChatCaptureCallback capture = new EvalChatCaptureCallback(chatHandler);

        try {
            traceRunner.run(evalCase.getQuery(), conversationId, taskId, capture, traceAware -> {
                StreamChatContext ctx = StreamChatContext.builder()
                        .question(evalCase.getQuery())
                        .conversationId(conversationId)
                        .taskId(taskId)
                        .deepThinking(false)
                        .userId(UserContext.getUserId())
                        .callback(traceAware)
                        .build();
                chatPipeline.execute(ctx);
            });

            boolean finished = capture.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                capture.markTimeout();
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        } catch (Exception ex) {
            log.warn("评测样本 Chat 录制异常 caseId={}", evalCase.getId(), ex);
            return SampleRecordResult.builder()
                    .record(buildErrorRecord(evalCase, conversationId, taskId, startedAt, "CHAT_ERROR", ex.getMessage()))
                    .build();
        }

        String chatStatus = capture.getFinalStatus();
        Long ttftMs = capture.getFirstTokenMs();
        long totalLatencyMs = capture.getTotalLatencyMs();
        String response = capture.getResponse();
        String thinking = evalProperties.isRecordThinking() ? capture.getThinking() : null;

        EvalResponse evidence = null;
        String evalError = null;
        long evalLatencyMs = 0L;
        Date evalStarted = new Date();
        try {
            evidence = bypassEvidenceCollector.collect(evalCase.getQuery());
            evalLatencyMs = evidence.getLatencyMs();
        } catch (Exception ex) {
            evalError = ex.getMessage();
            evalLatencyMs = System.currentTimeMillis() - evalStarted.getTime();
            log.warn("评测旁路采集失败 caseId={}", evalCase.getId(), ex);
        }

        String traceId = resolveTraceId(taskId);

        boolean chatOk = EvalWorkbenchConstants.RECORD_SUCCESS.equals(chatStatus)
                || EvalWorkbenchConstants.RECORD_REFUSED.equals(chatStatus);
        String finalStatus = chatOk ? chatStatus : (StrUtil.blankToDefault(chatStatus, EvalWorkbenchConstants.RECORD_ERROR));
        if (EvalWorkbenchConstants.RECORD_UNKNOWN.equals(finalStatus)) {
            finalStatus = EvalWorkbenchConstants.RECORD_ERROR;
        }

        Map<String, Object> raw = new HashMap<>();
        raw.put("schemaVersion", "1.0.0");
        raw.put("queryId", evalCase.getQueryId());
        raw.put("chatStatus", chatStatus);
        raw.put("chatError", capture.getErrorMessage());
        raw.put("evalError", evalError);
        raw.put("thinkingChars", capture.getThinking() == null ? 0 : capture.getThinking().length());
        if (evidence != null) {
            raw.put("subIntents", evidence.getSubIntents());
            raw.put("mcpContext", evidence.getMcpContext());
        }

        List<String> intentLeafIds = evidence == null ? List.of() : nullToEmpty(evidence.getIntentLeafIds());
        String intentPred = intentLeafIds.stream().filter(StrUtil::isNotBlank).findFirst().orElse(null);

        EvalRecordDO record = EvalRecordDO.builder()
                .runId(null)
                .caseId(evalCase.getId())
                .status(finalStatus)
                .question(evalCase.getQuery())
                .response(response)
                .thinking(thinking)
                .retrievedDocIds(EvalJsonSupport.toJsonArray(evidence == null ? List.of() : nullToEmpty(evidence.getRetrievedDocIds())))
                .retrievedChunkIds(EvalJsonSupport.toJsonArray(evidence == null ? List.of() : nullToEmpty(evidence.getRetrievedChunkIds())))
                .retrievedContexts(evidence == null || evidence.getRetrievedContexts() == null
                        ? null : JSONUtil.toJsonStr(evidence.getRetrievedContexts()))
                .retrievedContextDocIds(EvalJsonSupport.toJsonArray(
                        evidence == null ? List.of() : nullToEmpty(evidence.getRetrievedContextDocIds())))
                .predictedIntents(EvalJsonSupport.toJsonArray(intentLeafIds))
                .intentPred(intentPred)
                .hasKb(evidence == null ? null : evidence.isHasKb())
                .hasMcp(evidence == null ? null : evidence.isHasMcp())
                .retrievalSkipped(evidence != null && evidence.isRetrievalSkipped())
                .skipReason(evidence == null ? null : evidence.getSkipReason())
                .ttftMs(ttftMs)
                .totalLatencyMs(totalLatencyMs)
                .evalLatencyMs(evalLatencyMs)
                .conversationId(conversationId)
                .taskId(taskId)
                .traceId(traceId)
                .evidenceSource(EvalWorkbenchConstants.EVIDENCE_DUAL_PATH)
                .errorCode(chatOk ? null : "CHAT_" + finalStatus.toUpperCase())
                .errorMessage(chatOk ? evalError : StrUtil.blankToDefault(capture.getErrorMessage(), evalError))
                .rawPayload(JSONUtil.toJsonStr(raw))
                .startedAt(startedAt)
                .finishedAt(new Date())
                .build();

        return SampleRecordResult.builder().record(record).build();
    }

    private String resolveTraceId(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return null;
        }
        for (int i = 0; i < TRACE_RETRIES; i++) {
            try {
                RagTraceRunPageRequest request = new RagTraceRunPageRequest();
                request.setCurrent(1);
                request.setSize(5);
                request.setTaskId(taskId);
                var page = ragTraceQueryService.pageRuns(request);
                if (page != null && page.getRecords() != null && !page.getRecords().isEmpty()) {
                    RagTraceRunVO first = page.getRecords().get(0);
                    if (first != null && StrUtil.isNotBlank(first.getTraceId())) {
                        return first.getTraceId();
                    }
                }
                Thread.sleep(TRACE_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception ex) {
                log.debug("traceId 查询重试失败 taskId={} attempt={}", taskId, i + 1, ex);
            }
        }
        return null;
    }

    private EvalRecordDO buildErrorRecord(EvalCaseDO evalCase,
                                          String conversationId,
                                          String taskId,
                                          Date startedAt,
                                          String errorCode,
                                          String errorMessage) {
        return EvalRecordDO.builder()
                .caseId(evalCase.getId())
                .status(EvalWorkbenchConstants.RECORD_ERROR)
                .question(evalCase.getQuery())
                .retrievedDocIds("[]")
                .retrievedChunkIds("[]")
                .retrievedContextDocIds("[]")
                .predictedIntents("[]")
                .retrievalSkipped(false)
                .conversationId(conversationId)
                .taskId(taskId)
                .evidenceSource(EvalWorkbenchConstants.EVIDENCE_DUAL_PATH)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .rawPayload("{}")
                .startedAt(startedAt)
                .finishedAt(new Date())
                .build();
    }

    private static List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    @Data
    @Builder
    public static class SampleRecordResult {
        private EvalRecordDO record;
    }
}
