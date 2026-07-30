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
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRecordPageRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRunCreateRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRunPageRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRecordVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRunVO;
import com.nageoffer.ai.ragent.rag.evaluation.service.EvalRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测 Run API（阶段 3）。前缀 /admin/evaluations。
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.eval", name = "workbench-enabled", havingValue = "true")
public class EvalRunController {

    private final EvalRunService evalRunService;

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
