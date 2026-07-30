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
import java.util.Map;

@Data
@Builder
public class EvalRunVO {

    private String id;
    private String name;
    private String datasetVersionId;
    private String datasetId;
    private String datasetName;
    private String datasetVersion;
    private String baselineRunId;
    private String status;
    private String currentPhase;
    private String qualityVerdict;
    private Boolean cancelRequested;
    private Map<String, Object> configSnapshot;
    private Map<String, Object> thresholdSnapshot;
    private Map<String, Object> tags;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer progress;
    private Boolean ragasEnabled;
    private String createdBy;
    private Date startedAt;
    private Date finishedAt;
    private String errorMessage;
    private Date createTime;
    private Date updateTime;

    /**
     * 双路径漂移风险披露文案（前端固定展示）。
     */
    private String dualPathDisclaimer;
}
