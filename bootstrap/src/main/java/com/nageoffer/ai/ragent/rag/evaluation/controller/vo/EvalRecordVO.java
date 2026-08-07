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

package com.nageoffer.ai.ragent.rag.evaluation.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class EvalRecordVO {

    private String id;
    private String runId;
    private String caseId;
    private String queryId;
    private String status;
    private String question;
    private String response;

    /** Case 标注（来自评估集，便于样本详情对照） */
    private String intentL1;
    private String intentL2;
    private String difficulty;
    private Boolean requiresRag;
    private String expectedAnswerType;
    private List<String> expectedDocIds;
    private List<String> niceToHaveDocIds;
    private String groundTruth;
    private String trapType;

    private List<String> retrievedDocIds;
    private List<String> retrievedChunkIds;
    private List<String> retrievedContexts;
    private List<String> retrievedContextDocIds;
    private List<String> predictedIntents;
    private String intentPred;
    private Boolean hasKb;
    private Boolean hasMcp;
    private Boolean retrievalSkipped;
    private String skipReason;
    private Long ttftMs;
    private Long totalLatencyMs;
    private Long evalLatencyMs;
    private String conversationId;
    private String taskId;
    private String traceId;
    private String evidenceSource;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> rawPayload;
    private Date startedAt;
    private Date finishedAt;
    private Date createTime;
    private Date updateTime;
}
