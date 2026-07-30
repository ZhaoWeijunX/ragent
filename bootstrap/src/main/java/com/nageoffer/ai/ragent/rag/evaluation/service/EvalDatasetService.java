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

package com.nageoffer.ai.ragent.rag.evaluation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 评估集资产管理（阶段 2）。
 */
public interface EvalDatasetService {

    IPage<EvalDatasetVO> pageDatasets(EvalDatasetPageRequest request);

    EvalDatasetVO getDataset(String datasetId);

    String createDataset(EvalDatasetCreateRequest request);

    void updateDataset(String datasetId, EvalDatasetUpdateRequest request);

    void deleteDataset(String datasetId);

    List<EvalDatasetVersionVO> listVersions(String datasetId);

    EvalDatasetVersionVO getVersion(String versionId);

    String createDraftVersion(String datasetId, EvalDatasetVersionCreateRequest request);

    String copyVersion(String versionId);

    void archiveVersion(String versionId);

    void unarchiveVersion(String versionId);

    void deleteVersion(String versionId);

    EvalImportResultVO importCases(String versionId, MultipartFile file);

    EvalValidateResultVO validateVersion(String versionId);

    void publishVersion(String versionId);

    String exportVersionJsonl(String versionId);

    IPage<EvalCaseVO> pageCases(String versionId, EvalCasePageRequest request);

    String createCase(String versionId, EvalCaseUpsertRequest request);

    void updateCase(String caseId, EvalCaseUpsertRequest request);

    void deleteCase(String caseId);
}
