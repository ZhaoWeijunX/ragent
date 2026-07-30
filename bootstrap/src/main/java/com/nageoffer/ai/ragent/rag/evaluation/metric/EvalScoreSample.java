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

package com.nageoffer.ai.ragent.rag.evaluation.metric;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 评分用样本视图：Case 标注 + Record 录制结果合并。
 */
@Data
@Builder
public class EvalScoreSample {

    private String recordId;
    private String caseId;
    private String queryId;
    private String question;
    private String response;
    private String intentL1;
    private String intentL2;
    private String difficulty;
    private boolean requiresRag;
    private List<String> referenceDocIds;
    private List<String> referenceDocIdsNice;
    private String groundTruth;
    private String intentPred;
    private List<String> retrievedDocIds;
    private Boolean hasKb;
    private Boolean retrievalSkipped;
    private Long ttftMs;
    private Long totalLatencyMs;
    private String recordStatus;
    private String errorCode;
    private String errorMessage;
    private String traceId;
    private List<String> failureReasons;

    public List<String> safeRetrieved() {
        return retrievedDocIds == null ? Collections.emptyList() : retrievedDocIds;
    }

    public List<String> safeReference() {
        return referenceDocIds == null ? Collections.emptyList() : referenceDocIds;
    }

    public List<String> safeNice() {
        return referenceDocIdsNice == null ? Collections.emptyList() : referenceDocIdsNice;
    }
}
