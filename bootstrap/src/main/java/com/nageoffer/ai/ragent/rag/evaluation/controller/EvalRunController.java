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

package com.nageoffer.ai.ragent.rag.evaluation.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.rag.evaluation.constant.EvalWorkbenchConstants;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRagasRescoreRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRecordPageRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRunCreateRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRunPageRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalMetricReportVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRagasJudgeModelsVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRecordVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRunCompareVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRunVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalScoreBatchVO;
import com.nageoffer.ai.ragent.rag.evaluation.service.EvalCompareService;
import com.nageoffer.ai.ragent.rag.evaluation.service.EvalRunService;
import com.nageoffer.ai.ragent.rag.evaluation.service.EvalScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评测 Run API（阶段 3–6）。前缀 /admin/evaluations。
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.eval", name = "workbench-enabled", havingValue = "true")
public class EvalRunController {

    private final EvalRunService evalRunService;
    private final EvalScoreService evalScoreService;
    private final EvalCompareService evalCompareService;

    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/runs")
    public Result<IPage<EvalRunVO>> pageRuns(EvalRunPageRequest request) {
        requireAdmin();
        return Results.success(evalRunService.pageRuns(request));
    }

    @PostMapping(EvalWorkbenchConstants.API_PREFIX + "/runs")
    public Result<String> createRun(@RequestBody EvalRunCreateRequest request) {
        requireAdmin();
        return Results.success(evalRunService.createRun(request));
    }

    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/runs/{runId}")
    public Result<EvalRunVO> getRun(@PathVariable String runId) {
        requireAdmin();
        return Results.success(evalRunService.getRun(runId));
    }

    @PostMapping(EvalWorkbenchConstants.API_PREFIX + "/runs/{runId}/cancel")
    public Result<Void> cancelRun(@PathVariable String runId) {
        requireAdmin();
        evalRunService.cancelRun(runId);
        return Results.success();
    }

    @PostMapping(EvalWorkbenchConstants.API_PREFIX + "/runs/{runId}/resume")
    public Result<Void> resumeRun(@PathVariable String runId) {
        requireAdmin();
        evalRunService.resumeRun(runId);
        return Results.success();
    }

    @PostMapping(EvalWorkbenchConstants.API_PREFIX + "/runs/{runId}/rescore")
    public Result<String> rescore(@PathVariable String runId) {
        requireAdmin();
        return Results.success(evalScoreService.scoreDeterministic(runId));
    }

    @PostMapping(EvalWorkbenchConstants.API_PREFIX + "/runs/{runId}/ragas-rescore")
    public Result<String> ragasRescore(@PathVariable String runId,
                                       @RequestBody(required = false) EvalRagasRescoreRequest request) {
        requireAdmin();
        return Results.success(evalScoreService.submitRagasAsync(runId, request));
    }

    @PostMapping(EvalWorkbenchConstants.API_PREFIX + "/runs/{runId}/ragas-batches/{batchId}/cancel")
    public Result<Void> cancelRagasBatch(@PathVariable String runId, @PathVariable String batchId) {
        requireAdmin();
        evalScoreService.cancelRagasBatch(runId, batchId);
        return Results.success();
    }

    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/ragas-judge-models")
    public Result<EvalRagasJudgeModelsVO> ragasJudgeModels() {
        requireAdmin();
        return Results.success(evalScoreService.listRagasJudgeModels());
    }

    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/runs/{runId}/score-batches")
    public Result<List<EvalScoreBatchVO>> listScoreBatches(@PathVariable String runId) {
        requireAdmin();
        return Results.success(evalScoreService.listBatches(runId));
    }

    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/runs/{runId}/metrics")
    public Result<EvalMetricReportVO> metrics(@PathVariable String runId,
                                              @RequestParam(required = false) String batchId,
                                              @RequestParam(required = false) String scoreType) {
        requireAdmin();
        return Results.success(evalScoreService.getReport(runId, batchId, scoreType));
    }

    /**
     * 同数据集版本 Run 对比（自建 + RAGAS 同页）。跨版本拒绝。
     */
    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/runs/{runId}/compare/{baselineRunId}")
    public Result<EvalRunCompareVO> compare(@PathVariable String runId,
                                            @PathVariable String baselineRunId) {
        requireAdmin();
        return Results.success(evalCompareService.compare(runId, baselineRunId));
    }

    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/runs/{runId}/export")
    public ResponseEntity<byte[]> export(@PathVariable String runId,
                                         @RequestParam(required = false) String batchId,
                                         @RequestParam(required = false, defaultValue = "json") String format) {
        requireAdmin();
        byte[] body = evalScoreService.exportReport(runId, batchId, format);
        String filename = "eval-run-" + runId + "." + ("csv".equalsIgnoreCase(format) ? "csv"
                : "jsonl".equalsIgnoreCase(format) ? "jsonl" : "json");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/runs/{runId}/records")
    public Result<IPage<EvalRecordVO>> pageRecords(@PathVariable String runId, EvalRecordPageRequest request) {
        requireAdmin();
        return Results.success(evalRunService.pageRecords(runId, request));
    }

    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/records/{recordId}")
    public Result<EvalRecordVO> getRecord(@PathVariable String recordId) {
        requireAdmin();
        return Results.success(evalRunService.getRecord(recordId));
    }

    private void requireAdmin() {
        StpUtil.checkRole("admin");
    }
}
