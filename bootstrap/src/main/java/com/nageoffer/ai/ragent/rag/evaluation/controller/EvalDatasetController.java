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
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalCasePageRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalCaseUpsertRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalDatasetCreateRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalDatasetPageRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalDatasetUpdateRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalDatasetVersionCreateRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalCaseVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalDatasetVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalDatasetVersionVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalImportResultVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalValidateResultVO;
import com.nageoffer.ai.ragent.rag.evaluation.service.EvalDatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 评估集管理 API（阶段 2）。前缀 /admin/evaluations。
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ragent.eval", name = "workbench-enabled", havingValue = "true")
public class EvalDatasetController {

    private final EvalDatasetService evalDatasetService;

    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/datasets")
    public Result<IPage<EvalDatasetVO>> pageDatasets(EvalDatasetPageRequest request) {
        requireAdmin();
        return Results.success(evalDatasetService.pageDatasets(request));
    }

    @PostMapping(EvalWorkbenchConstants.API_PREFIX + "/datasets")
    public Result<String> createDataset(@RequestBody EvalDatasetCreateRequest request) {
        requireAdmin();
        return Results.success(evalDatasetService.createDataset(request));
    }

    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/datasets/{id}")
    public Result<EvalDatasetVO> getDataset(@PathVariable("id") String id) {
        requireAdmin();
        return Results.success(evalDatasetService.getDataset(id));
    }

    @PutMapping(EvalWorkbenchConstants.API_PREFIX + "/datasets/{id}")
    public Result<Void> updateDataset(@PathVariable("id") String id, @RequestBody EvalDatasetUpdateRequest request) {
        requireAdmin();
        evalDatasetService.updateDataset(id, request);
        return Results.success();
    }

    @DeleteMapping(EvalWorkbenchConstants.API_PREFIX + "/datasets/{id}")
    public Result<Void> deleteDataset(@PathVariable("id") String id) {
        requireAdmin();
        evalDatasetService.deleteDataset(id);
        return Results.success();
    }

    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/datasets/{id}/versions")
    public Result<List<EvalDatasetVersionVO>> listVersions(@PathVariable("id") String id) {
        requireAdmin();
        return Results.success(evalDatasetService.listVersions(id));
    }

    @PostMapping(EvalWorkbenchConstants.API_PREFIX + "/datasets/{id}/versions")
    public Result<String> createDraftVersion(@PathVariable("id") String id,
                                             @RequestBody(required = false) EvalDatasetVersionCreateRequest request) {
        requireAdmin();
        return Results.success(evalDatasetService.createDraftVersion(id, request));
    }

    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/dataset-versions/{versionId}")
    public Result<EvalDatasetVersionVO> getVersion(@PathVariable String versionId) {
        requireAdmin();
        return Results.success(evalDatasetService.getVersion(versionId));
    }

    @PostMapping(EvalWorkbenchConstants.API_PREFIX + "/dataset-versions/{versionId}/copy")
    public Result<String> copyVersion(@PathVariable String versionId) {
        requireAdmin();
        return Results.success(evalDatasetService.copyVersion(versionId));
    }

    @PostMapping(EvalWorkbenchConstants.API_PREFIX + "/dataset-versions/{versionId}/archive")
    public Result<Void> archiveVersion(@PathVariable String versionId) {
        requireAdmin();
        evalDatasetService.archiveVersion(versionId);
        return Results.success();
    }

    @PostMapping(EvalWorkbenchConstants.API_PREFIX + "/dataset-versions/{versionId}/unarchive")
    public Result<Void> unarchiveVersion(@PathVariable String versionId) {
        requireAdmin();
        evalDatasetService.unarchiveVersion(versionId);
        return Results.success();
    }

    @DeleteMapping(EvalWorkbenchConstants.API_PREFIX + "/dataset-versions/{versionId}")
    public Result<Void> deleteVersion(@PathVariable String versionId) {
        requireAdmin();
        evalDatasetService.deleteVersion(versionId);
        return Results.success();
    }

    @PostMapping(value = EvalWorkbenchConstants.API_PREFIX + "/dataset-versions/{versionId}/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<EvalImportResultVO> importCases(@PathVariable String versionId,
                                                  @RequestPart("file") MultipartFile file) {
        requireAdmin();
        return Results.success(evalDatasetService.importCases(versionId, file));
    }

    @PostMapping(EvalWorkbenchConstants.API_PREFIX + "/dataset-versions/{versionId}/validate")
    public Result<EvalValidateResultVO> validateVersion(@PathVariable String versionId) {
        requireAdmin();
        return Results.success(evalDatasetService.validateVersion(versionId));
    }

    @PostMapping(EvalWorkbenchConstants.API_PREFIX + "/dataset-versions/{versionId}/publish")
    public Result<Void> publishVersion(@PathVariable String versionId) {
        requireAdmin();
        evalDatasetService.publishVersion(versionId);
        return Results.success();
    }

    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/dataset-versions/{versionId}/export")
    public ResponseEntity<byte[]> exportVersion(@PathVariable String versionId) {
        requireAdmin();
        String jsonl = evalDatasetService.exportVersionJsonl(versionId);
        byte[] body = jsonl.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"eval-cases-" + versionId + ".jsonl\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @GetMapping(EvalWorkbenchConstants.API_PREFIX + "/dataset-versions/{versionId}/cases")
    public Result<IPage<EvalCaseVO>> pageCases(@PathVariable String versionId, EvalCasePageRequest request) {
        requireAdmin();
        return Results.success(evalDatasetService.pageCases(versionId, request));
    }

    @PostMapping(EvalWorkbenchConstants.API_PREFIX + "/dataset-versions/{versionId}/cases")
    public Result<String> createCase(@PathVariable String versionId, @RequestBody EvalCaseUpsertRequest request) {
        requireAdmin();
        return Results.success(evalDatasetService.createCase(versionId, request));
    }

    @PutMapping(EvalWorkbenchConstants.API_PREFIX + "/cases/{caseId}")
    public Result<Void> updateCase(@PathVariable String caseId, @RequestBody EvalCaseUpsertRequest request) {
        requireAdmin();
        evalDatasetService.updateCase(caseId, request);
        return Results.success();
    }

    @DeleteMapping(EvalWorkbenchConstants.API_PREFIX + "/cases/{caseId}")
    public Result<Void> deleteCase(@PathVariable String caseId) {
        requireAdmin();
        evalDatasetService.deleteCase(caseId);
        return Results.success();
    }

    private void requireAdmin() {
        StpUtil.checkRole("admin");
    }
}
