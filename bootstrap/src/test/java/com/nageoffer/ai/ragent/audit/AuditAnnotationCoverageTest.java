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

package com.nageoffer.ai.ragent.audit;

import com.mzt.logapi.starter.annotation.LogRecord;
import com.nageoffer.ai.ragent.audit.constant.BizChangeBizType;
import com.nageoffer.ai.ragent.audit.constant.BizChangeOperationType;
import com.nageoffer.ai.ragent.knowledge.controller.request.FeishuWikiImportRequest;
import com.nageoffer.ai.ragent.knowledge.service.impl.FeishuWikiImportServiceImpl;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalCaseUpsertRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalDatasetVersionCreateRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRagasRescoreRequest;
import com.nageoffer.ai.ragent.rag.evaluation.service.impl.EvalDatasetServiceImpl;
import com.nageoffer.ai.ragent.rag.evaluation.service.impl.EvalRunServiceImpl;
import com.nageoffer.ai.ragent.rag.evaluation.service.impl.EvalScoreServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuditAnnotationCoverageTest {

    @Test
    void feishuAuditsBatchStartButNotAsyncItems() throws Exception {
        assertAudit(
                FeishuWikiImportServiceImpl.class,
                "startImport",
                BizChangeBizType.KNOWLEDGE_BASE,
                BizChangeOperationType.RUN,
                String.class,
                FeishuWikiImportRequest.class);
        assertNotAudited(FeishuWikiImportServiceImpl.class, "processNextItem", String.class);
    }

    @Test
    void evaluationAuditsVersionAndRunLifecycle() throws Exception {
        assertAudit(
                EvalDatasetServiceImpl.class,
                "createDraftVersion",
                BizChangeBizType.EVAL_DATASET_VERSION,
                BizChangeOperationType.CREATE,
                String.class,
                EvalDatasetVersionCreateRequest.class);
        assertAudit(
                EvalDatasetServiceImpl.class,
                "copyVersion",
                BizChangeBizType.EVAL_DATASET_VERSION,
                BizChangeOperationType.CREATE,
                String.class);
        assertAudit(
                EvalDatasetServiceImpl.class,
                "archiveVersion",
                BizChangeBizType.EVAL_DATASET_VERSION,
                BizChangeOperationType.UPDATE,
                String.class);
        assertAudit(
                EvalDatasetServiceImpl.class,
                "unarchiveVersion",
                BizChangeBizType.EVAL_DATASET_VERSION,
                BizChangeOperationType.UPDATE,
                String.class);
        assertAudit(
                EvalDatasetServiceImpl.class,
                "importCases",
                BizChangeBizType.EVAL_DATASET_VERSION,
                BizChangeOperationType.UPDATE,
                String.class,
                MultipartFile.class);

        assertAudit(
                EvalRunServiceImpl.class,
                "cancelRun",
                BizChangeBizType.EVAL_RUN,
                BizChangeOperationType.RUN,
                String.class);
        assertAudit(
                EvalRunServiceImpl.class,
                "resumeRun",
                BizChangeBizType.EVAL_RUN,
                BizChangeOperationType.RUN,
                String.class);
        assertAudit(
                EvalRunServiceImpl.class,
                "rerunRecord",
                BizChangeBizType.EVAL_RUN,
                BizChangeOperationType.RUN,
                String.class,
                String.class);
    }

    @Test
    void evaluationKeepsCaseChangesAndAutomaticScoresOutOfAudit() throws Exception {
        assertNotAudited(
                EvalDatasetServiceImpl.class,
                "createCase",
                String.class,
                EvalCaseUpsertRequest.class);
        assertNotAudited(
                EvalDatasetServiceImpl.class,
                "updateCase",
                String.class,
                EvalCaseUpsertRequest.class);
        assertNotAudited(EvalDatasetServiceImpl.class, "deleteCase", String.class);

        assertNotAudited(EvalScoreServiceImpl.class, "scoreDeterministic", String.class);
        assertNotAudited(EvalScoreServiceImpl.class, "scoreRagas", String.class);
        assertNotAudited(
                EvalScoreServiceImpl.class,
                "submitRagasAsync",
                String.class,
                EvalRagasRescoreRequest.class);

        assertAudit(
                EvalScoreServiceImpl.class,
                "rescoreDeterministic",
                BizChangeBizType.EVAL_RUN,
                BizChangeOperationType.RUN,
                String.class);
        assertAudit(
                EvalScoreServiceImpl.class,
                "rescoreRagas",
                BizChangeBizType.EVAL_RUN,
                BizChangeOperationType.RUN,
                String.class,
                EvalRagasRescoreRequest.class);
        assertAudit(
                EvalScoreServiceImpl.class,
                "cancelRagasBatch",
                BizChangeBizType.EVAL_RUN,
                BizChangeOperationType.RUN,
                String.class,
                String.class);
    }

    private void assertAudit(
            Class<?> target,
            String methodName,
            String bizType,
            String operationType,
            Class<?>... parameterTypes) throws Exception {
        LogRecord annotation = method(target, methodName, parameterTypes).getAnnotation(LogRecord.class);
        assertNotNull(annotation, () -> target.getSimpleName() + "." + methodName + " should be audited");
        assertEquals(bizType, annotation.type());
        assertEquals(operationType, annotation.subType());
    }

    private void assertNotAudited(Class<?> target, String methodName, Class<?>... parameterTypes) throws Exception {
        assertNull(
                method(target, methodName, parameterTypes).getAnnotation(LogRecord.class),
                () -> target.getSimpleName() + "." + methodName + " should not be audited");
    }

    private Method method(Class<?> target, String methodName, Class<?>... parameterTypes) throws Exception {
        return target.getMethod(methodName, parameterTypes);
    }
}
