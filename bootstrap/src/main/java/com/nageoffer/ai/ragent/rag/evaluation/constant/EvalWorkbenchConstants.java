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

package com.nageoffer.ai.ragent.rag.evaluation.constant;

/**
 * 评估集 / 版本 / Run / 评分相关状态常量（阶段 0 ADR 冻结口径）。
 */
public final class EvalWorkbenchConstants {

    private EvalWorkbenchConstants() {
    }

    public static final String API_PREFIX = "/admin/evaluations";

    /** 数据集 status */
    public static final String DATASET_ACTIVE = "ACTIVE";
    public static final String DATASET_ARCHIVED = "ARCHIVED";

    /** 版本 status */
    public static final String VERSION_DRAFT = "DRAFT";
    public static final String VERSION_PUBLISHED = "PUBLISHED";
    public static final String VERSION_ARCHIVED = "ARCHIVED";

    /** Run status / phase */
    public static final String RUN_PENDING = "PENDING";
    public static final String RUN_RECORDING = "RECORDING";
    public static final String RUN_DETERMINISTIC_SCORING = "DETERMINISTIC_SCORING";
    public static final String RUN_RAGAS_SCORING = "RAGAS_SCORING";
    public static final String RUN_REPORTING = "REPORTING";
    public static final String RUN_COMPLETED = "COMPLETED";
    public static final String RUN_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    public static final String RUN_FAILED = "FAILED";
    public static final String RUN_CANCELLED = "CANCELLED";

    /** score batch type */
    public static final String SCORE_DETERMINISTIC = "DETERMINISTIC";
    public static final String SCORE_RAGAS = "RAGAS";

    /** evidence */
    public static final String EVIDENCE_DUAL_PATH = "DUAL_PATH_CHAT_AND_EVAL";

    /** Record.finalStatus / t_eval_record.status */
    public static final String RECORD_SUCCESS = "success";
    public static final String RECORD_REFUSED = "refused";
    public static final String RECORD_ERROR = "error";
    public static final String RECORD_CANCELLED = "cancelled";
    public static final String RECORD_UNKNOWN = "unknown";
    public static final String RECORD_PENDING = "PENDING";
}
