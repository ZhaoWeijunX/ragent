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

package com.nageoffer.ai.ragent.rag.evaluation.support;

import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalCaseUpsertRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalImportIssueVO;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalCaseImportSupportTest {

    @Test
    void parseJsonlAndCamelCase() {
        String content = """
                {"query_id":"A-1","query":"你好","requires_rag":false,"difficulty":"easy","intent_l2":"X"}
                {"queryId":"A-2","query":"世界","requiresRag":true,"expectedDocIds":["DOC_1"],"difficulty":"medium"}
                """;
        EvalCaseImportSupport.ParseFileResult result = EvalCaseImportSupport.parseFile(content);
        assertEquals(0, result.fileIssues().size());
        assertEquals(2, result.cases().size());
        assertEquals("A-1", result.cases().get(0).request().getQueryId());
        assertEquals("A-2", result.cases().get(1).request().getQueryId());
        assertTrue(Boolean.TRUE.equals(result.cases().get(1).request().getRequiresRag()));
    }

    @Test
    void rejectDuplicateQueryId() {
        EvalCaseUpsertRequest first = new EvalCaseUpsertRequest();
        first.setQueryId("A-1");
        first.setQuery("q1");
        first.setRequiresRag(false);
        first.setDifficulty("easy");

        EvalCaseUpsertRequest second = new EvalCaseUpsertRequest();
        second.setQueryId("A-1");
        second.setQuery("q2");
        second.setRequiresRag(false);
        second.setDifficulty("easy");

        Set<String> ids = new HashSet<>();
        List<EvalImportIssueVO> i1 = EvalCaseImportSupport.validateRequest(first, 1, ids, Set.of(), Set.of(), false);
        List<EvalImportIssueVO> i2 = EvalCaseImportSupport.validateRequest(second, 2, ids, Set.of(), Set.of(), false);
        assertTrue(i1.stream().noneMatch(x -> "QUERY_ID_DUPLICATE".equals(x.getCode())));
        assertTrue(i2.stream().anyMatch(x -> "QUERY_ID_DUPLICATE".equals(x.getCode())));
    }

    @Test
    void warnUnresolvedDocAndIntent() {
        EvalCaseUpsertRequest req = new EvalCaseUpsertRequest();
        req.setQueryId("B-1");
        req.setQuery("需要知识");
        req.setRequiresRag(true);
        req.setDifficulty("hard");
        req.setIntentL2("UNKNOWN_INTENT");
        req.setExpectedDocIds(List.of("MISSING_DOC"));

        List<EvalImportIssueVO> issues = EvalCaseImportSupport.validateRequest(
                req, 1, new HashSet<>(), Set.of("KNOWN_INTENT"), Set.of("EXISTING_DOC"), true);
        assertTrue(issues.stream().anyMatch(x -> "INTENT_UNMAPPED".equals(x.getCode())));
        assertTrue(issues.stream().anyMatch(x -> "DOC_UNRESOLVED".equals(x.getCode())));
        assertFalse(issues.stream().anyMatch(x -> EvalCaseImportSupport.LEVEL_ERROR.equals(x.getLevel())
                && "GOLD_DOC_MISSING".equals(x.getCode())));
    }

    @Test
    void stripDocExtension() {
        assertEquals("FAQ_VAC_001", EvalCaseImportSupport.stripDocExtension("FAQ_VAC_001.md"));
        assertEquals("noext", EvalCaseImportSupport.stripDocExtension("noext"));
    }
}
