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

import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRagasRescoreRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalMetricReportVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRagasJudgeModelsVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalScoreBatchVO;

import java.util.List;

public interface EvalScoreService {

    /**
     * 对 Run 执行一轮自建指标评分，新建 score_batch。
     * @return batchId
     */
    String scoreDeterministic(String runId);

    /**
     * 管理台手工重新执行自建指标评分，并记录审计日志。
     */
    String rescoreDeterministic(String runId);

    /**
     * 同步执行一轮 RAGAS（供 Runner 流水线内调用）：提交外部服务并轮询至终态。
     * 失败不回滚 Record / 自建分数。
     * @return batchId；跳过时返回 null
     */
    String scoreRagas(String runId);

    /**
     * 异步提交一轮 RAGAS（供管理台「RAGAS 评分」）：立即返回 batchId，后台轮询并落库。
     * 若已有 PENDING/RUNNING 的 RAGAS 批次则复用其 id（防连点）。
     * @param request Judge 模型候选 id；null/空字段时回退 judge-chat / ai.embedding 默认模型（含 endpoint/key）
     * @return batchId；跳过时返回 null（仅同步 Runner 路径）；管理台异步路径遇未启用会抛错
     */
    String submitRagasAsync(String runId, EvalRagasRescoreRequest request);

    /**
     * 管理台手工重新执行 RAGAS 评分，并记录审计日志。
     */
    String rescoreRagas(String runId, EvalRagasRescoreRequest request);

    /**
     * 取消进行中的 RAGAS 批次：协作式 cancel 外部 job，并将 batch 标为 FAILED。
     */
    void cancelRagasBatch(String runId, String batchId);

    /**
     * RAGAS 弹窗可选模型：Judge chat（ragent.eval.ragas.judge-chat）+ embedding（ai.embedding）。
     */
    EvalRagasJudgeModelsVO listRagasJudgeModels();

    List<EvalScoreBatchVO> listBatches(String runId);

    /**
     * @param scoreType DETERMINISTIC / RAGAS；空则按 batchId 或默认自建指标最新批次
     */
    EvalMetricReportVO getReport(String runId, String batchId, String scoreType);

    /**
     * 导出自建指标批次：format=json|jsonl|csv
     */
    byte[] exportReport(String runId, String batchId, String format);
}
