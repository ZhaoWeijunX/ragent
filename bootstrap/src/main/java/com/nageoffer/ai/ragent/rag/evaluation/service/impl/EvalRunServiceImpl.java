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

package com.nageoffer.ai.ragent.rag.evaluation.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mzt.logapi.starter.annotation.LogRecord;
import com.nageoffer.ai.ragent.audit.constant.BizChangeBizType;
import com.nageoffer.ai.ragent.audit.constant.BizChangeOperationType;
import com.nageoffer.ai.ragent.audit.support.BizChangeLogContext;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.eval.EvalProperties;
import com.nageoffer.ai.ragent.rag.evaluation.constant.EvalWorkbenchConstants;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRecordPageRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRunCreateRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRunPageRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRecordVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRunVO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalCaseDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalDatasetDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalDatasetVersionDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalRecordDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalRunDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalCaseMapper;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalDatasetMapper;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalDatasetVersionMapper;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalRecordMapper;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalRunMapper;
import com.nageoffer.ai.ragent.rag.evaluation.service.EvalRunService;
import com.nageoffer.ai.ragent.rag.evaluation.support.EvalConfigSnapshotSupport;
import com.nageoffer.ai.ragent.rag.evaluation.support.EvalJsonSupport;
import com.nageoffer.ai.ragent.rag.evaluation.task.EvalRunWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.eval", name = "workbench-enabled", havingValue = "true")
public class EvalRunServiceImpl implements EvalRunService {

    public static final String DUAL_PATH_DISCLAIMER =
            "双路径证据提示：旁路 (/rag/eval) 检索证据可能与真实 Chat 回答所用上下文不完全一致，请勿将两者视为严格等价。";

    private static final Set<String> TERMINAL = Set.of(
            EvalWorkbenchConstants.RUN_COMPLETED,
            EvalWorkbenchConstants.RUN_PARTIAL_SUCCESS,
            EvalWorkbenchConstants.RUN_FAILED,
            EvalWorkbenchConstants.RUN_CANCELLED
    );

    private static final Set<String> RESUMABLE = Set.of(
            EvalWorkbenchConstants.RUN_FAILED,
            EvalWorkbenchConstants.RUN_PARTIAL_SUCCESS,
            EvalWorkbenchConstants.RUN_CANCELLED
    );

    private final EvalRunMapper runMapper;
    private final EvalRecordMapper recordMapper;
    private final EvalCaseMapper caseMapper;
    private final EvalDatasetVersionMapper versionMapper;
    private final EvalDatasetMapper datasetMapper;
    private final EvalProperties evalProperties;
    private final EvalConfigSnapshotSupport configSnapshotSupport;
    private final EvalRunWorker runWorker;
    private final BizChangeLogContext bizChangeLogContext;

    @Override
    public IPage<EvalRunVO> pageRuns(EvalRunPageRequest request) {
        Page<EvalRunDO> page = new Page<>(request.getCurrent(), request.getSize());
        IPage<EvalRunDO> result = runMapper.selectPage(page, Wrappers.lambdaQuery(EvalRunDO.class)
                .like(StrUtil.isNotBlank(request.getKeyword()), EvalRunDO::getName, request.getKeyword())
                .eq(StrUtil.isNotBlank(request.getStatus()), EvalRunDO::getStatus, request.getStatus())
                .eq(StrUtil.isNotBlank(request.getDatasetVersionId()), EvalRunDO::getDatasetVersionId, request.getDatasetVersionId())
                .orderByDesc(EvalRunDO::getCreateTime));
        Map<String, EvalDatasetVersionDO> versionMap = loadVersions(result.getRecords());
        Map<String, EvalDatasetDO> datasetMap = loadDatasets(versionMap.values());
        return result.convert(run -> toRunVO(run, versionMap, datasetMap));
    }

    @Override
    public EvalRunVO getRun(String runId) {
        EvalRunDO run = requireRun(runId);
        EvalDatasetVersionDO version = versionMapper.selectById(run.getDatasetVersionId());
        EvalDatasetDO dataset = version == null ? null : datasetMapper.selectById(version.getDatasetId());
        Map<String, EvalDatasetVersionDO> versionMap = new HashMap<>();
        if (version != null) {
            versionMap.put(version.getId(), version);
        }
        Map<String, EvalDatasetDO> datasetMap = new HashMap<>();
        if (dataset != null) {
            datasetMap.put(dataset.getId(), dataset);
        }
        return toRunVO(run, versionMap, datasetMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(
            success = "创建评测运行「{{#request.name}}」",
            fail = "创建评测运行失败：{{#_errorMsg}}",
            type = BizChangeBizType.EVAL_RUN,
            subType = BizChangeOperationType.CREATE,
            bizNo = BizChangeLogContext.BIZ_ID_EXPRESSION,
            extra = BizChangeLogContext.SNAPSHOT_EXPRESSION,
            condition = BizChangeLogContext.RECORD_CONDITION
    )
    public String createRun(EvalRunCreateRequest request) {
        Assert.notNull(request, () -> new ClientException("请求不能为空"));
        Assert.notBlank(StrUtil.trim(request.getName()), () -> new ClientException("运行名称不能为空"));
        Assert.notBlank(request.getDatasetVersionId(), () -> new ClientException("数据集版本不能为空"));

        EvalDatasetVersionDO version = versionMapper.selectById(request.getDatasetVersionId());
        Assert.notNull(version, () -> new ClientException("数据集版本不存在"));
        Assert.isTrue(EvalWorkbenchConstants.VERSION_PUBLISHED.equals(version.getStatus()),
                () -> new ClientException("仅已发布版本可创建 Run"));

        Long sampleCount = caseMapper.selectCount(Wrappers.lambdaQuery(EvalCaseDO.class)
                .eq(EvalCaseDO::getDatasetVersionId, version.getId()));
        Assert.isTrue(sampleCount != null && sampleCount > 0, () -> new ClientException("版本无样本，无法创建 Run"));

        long active = countActiveRuns();
        Assert.isTrue(active < Math.max(1, evalProperties.getMaxActiveRuns()),
                () -> new ClientException("活动 Run 已达上限 max-active-runs=" + evalProperties.getMaxActiveRuns()));

        if (StrUtil.isNotBlank(request.getBaselineRunId())) {
            EvalRunDO baseline = runMapper.selectById(request.getBaselineRunId());
            Assert.notNull(baseline, () -> new ClientException("基线 Run 不存在"));
        }

        Map<String, Object> configSnapshot = configSnapshotSupport.build(version, sampleCount.intValue());
        boolean ragasEnabled = Boolean.TRUE.equals(request.getRagasEnabled());
        // 未启用 RAGAS 时忽略 autoStart，避免脏配置
        boolean ragasAutoStart = ragasEnabled && Boolean.TRUE.equals(request.getRagasAutoStart());
        Map<String, Object> ragasPref = new LinkedHashMap<>();
        ragasPref.put("enabled", ragasEnabled);
        ragasPref.put("autoStart", ragasAutoStart);
        if (ragasEnabled && StrUtil.isNotBlank(request.getRagasChatModelId())) {
            ragasPref.put("chatModelId", StrUtil.trim(request.getRagasChatModelId()));
        }
        if (ragasEnabled && StrUtil.isNotBlank(request.getRagasEmbeddingModelId())) {
            ragasPref.put("embeddingModelId", StrUtil.trim(request.getRagasEmbeddingModelId()));
        }
        configSnapshot.put("ragas", ragasPref);

        Map<String, Object> thresholdSnapshot = new LinkedHashMap<>();
        thresholdSnapshot.put("schemaVersion", "1.0.0");
        thresholdSnapshot.put("policyId", null);
        thresholdSnapshot.put("policyVersion", "draft");
        thresholdSnapshot.put("rules", List.of());
        thresholdSnapshot.put("onViolate", "FAIL");

        Map<String, Object> tags = request.getTags() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getTags());
        tags.putIfAbsent("environment", "unknown");
        tags.putIfAbsent("appVersion", "unknown");
        tags.putIfAbsent("gitCommit", "unknown");

        String runId = IdUtil.getSnowflakeNextIdStr();
        EvalRunDO run = EvalRunDO.builder()
                .id(runId)
                .name(StrUtil.trim(request.getName()))
                .datasetVersionId(version.getId())
                .baselineRunId(StrUtil.blankToDefault(request.getBaselineRunId(), null))
                .status(EvalWorkbenchConstants.RUN_PENDING)
                .currentPhase(EvalWorkbenchConstants.RUN_PENDING)
                .qualityVerdict(EvalWorkbenchConstants.VERDICT_NOT_EVALUATED)
                .cancelRequested(0)
                .configSnapshot(JSONUtil.toJsonStr(configSnapshot))
                .thresholdSnapshot(JSONUtil.toJsonStr(thresholdSnapshot))
                .tags(JSONUtil.toJsonStr(tags))
                .totalCount(sampleCount.intValue())
                .successCount(0)
                .failedCount(0)
                .progress(0)
                .ragasEnabled(ragasEnabled)
                .createdBy(UserContext.getUserId())
                .build();
        runMapper.insert(run);
        bizChangeLogContext.put(runId, null, run);

        runWorker.submit(runId);
        return runId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelRun(String runId) {
        EvalRunDO run = requireRun(runId);
        if (TERMINAL.contains(run.getStatus())) {
            throw new ClientException("终态 Run 不可取消");
        }
        runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                .eq(EvalRunDO::getId, runId)
                .notIn(EvalRunDO::getStatus, TERMINAL)
                .set(EvalRunDO::getCancelRequested, 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumeRun(String runId) {
        EvalRunDO run = requireRun(runId);
        Assert.isTrue(RESUMABLE.contains(run.getStatus()),
                () -> new ClientException("仅 FAILED / PARTIAL_SUCCESS / CANCELLED 可重试失败样本"));

        long active = countActiveRuns();
        Assert.isTrue(active < Math.max(1, evalProperties.getMaxActiveRuns()),
                () -> new ClientException("活动 Run 已达上限"));

        // 重置为 PENDING，保留已成功 Record；失败样本由 Worker 幂等重写
        runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                .eq(EvalRunDO::getId, runId)
                .set(EvalRunDO::getStatus, EvalWorkbenchConstants.RUN_PENDING)
                .set(EvalRunDO::getCurrentPhase, EvalWorkbenchConstants.RUN_PENDING)
                .set(EvalRunDO::getCancelRequested, 0)
                .set(EvalRunDO::getFinishedAt, null)
                .set(EvalRunDO::getErrorMessage, null)
                .set(EvalRunDO::getLeaseOwner, null)
                .set(EvalRunDO::getLeaseExpireAt, null)
                .set(EvalRunDO::getProgress, 0));
        runWorker.submit(runId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rerunRecord(String runId, String recordId) {
        EvalRunDO run = requireRun(runId);
        Assert.isTrue(TERMINAL.contains(run.getStatus()),
                () -> new ClientException("仅终态 Run 可单样本重跑"));

        EvalRecordDO record = recordMapper.selectById(recordId);
        Assert.notNull(record, () -> new ClientException("录制记录不存在"));
        Assert.isTrue(runId.equals(record.getRunId()), () -> new ClientException("记录不属于该 Run"));
        Assert.notBlank(record.getCaseId(), () -> new ClientException("记录缺少 caseId"));

        EvalCaseDO evalCase = caseMapper.selectById(record.getCaseId());
        Assert.notNull(evalCase, () -> new ClientException("对应 Case 不存在"));
        Assert.isTrue(Objects.equals(run.getDatasetVersionId(), evalCase.getDatasetVersionId()),
                () -> new ClientException("Case 与 Run 数据集版本不一致"));

        long active = countActiveRuns();
        Assert.isTrue(active < Math.max(1, evalProperties.getMaxActiveRuns()),
                () -> new ClientException("活动 Run 已达上限"));

        runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                .eq(EvalRunDO::getId, runId)
                .set(EvalRunDO::getStatus, EvalWorkbenchConstants.RUN_PENDING)
                .set(EvalRunDO::getCurrentPhase, EvalWorkbenchConstants.RUN_PENDING)
                .set(EvalRunDO::getCancelRequested, 0)
                .set(EvalRunDO::getFinishedAt, null)
                .set(EvalRunDO::getErrorMessage, null)
                .set(EvalRunDO::getLeaseOwner, null)
                .set(EvalRunDO::getLeaseExpireAt, null));
        runWorker.submitSingleCaseRerun(runId, record.getCaseId());
    }

    @Override
    public IPage<EvalRecordVO> pageRecords(String runId, EvalRecordPageRequest request) {
        requireRun(runId);
        Page<EvalRecordDO> page = new Page<>(request.getCurrent(), request.getSize());
        IPage<EvalRecordDO> result = recordMapper.selectPage(page, Wrappers.lambdaQuery(EvalRecordDO.class)
                .eq(EvalRecordDO::getRunId, runId)
                .eq(StrUtil.isNotBlank(request.getStatus()), EvalRecordDO::getStatus, request.getStatus())
                .like(StrUtil.isNotBlank(request.getKeyword()), EvalRecordDO::getQuestion, request.getKeyword())
                .orderByAsc(EvalRecordDO::getCreateTime));
        Map<String, EvalCaseDO> caseMap = loadCases(result.getRecords());
        return result.convert(r -> toRecordVO(r, caseMap.get(r.getCaseId())));
    }

    @Override
    public EvalRecordVO getRecord(String recordId) {
        EvalRecordDO record = recordMapper.selectById(recordId);
        Assert.notNull(record, () -> new ClientException("录制记录不存在"));
        EvalCaseDO evalCase = caseMapper.selectById(record.getCaseId());
        return toRecordVO(record, evalCase);
    }

    private long countActiveRuns() {
        Long count = runMapper.selectCount(Wrappers.lambdaQuery(EvalRunDO.class)
                .in(EvalRunDO::getStatus,
                        EvalWorkbenchConstants.RUN_PENDING,
                        EvalWorkbenchConstants.RUN_RECORDING,
                        EvalWorkbenchConstants.RUN_DETERMINISTIC_SCORING,
                        EvalWorkbenchConstants.RUN_RAGAS_SCORING,
                        EvalWorkbenchConstants.RUN_REPORTING));
        return count == null ? 0 : count;
    }

    private EvalRunDO requireRun(String runId) {
        EvalRunDO run = runMapper.selectById(runId);
        Assert.notNull(run, () -> new ClientException("Run 不存在"));
        return run;
    }

    private Map<String, EvalDatasetVersionDO> loadVersions(List<EvalRunDO> runs) {
        Set<String> ids = runs.stream().map(EvalRunDO::getDatasetVersionId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return versionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(EvalDatasetVersionDO::getId, v -> v, (a, b) -> a));
    }

    private Map<String, EvalDatasetDO> loadDatasets(Iterable<EvalDatasetVersionDO> versions) {
        Set<String> ids = new java.util.HashSet<>();
        for (EvalDatasetVersionDO v : versions) {
            if (v != null && v.getDatasetId() != null) {
                ids.add(v.getDatasetId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return datasetMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(EvalDatasetDO::getId, d -> d, (a, b) -> a));
    }

    private Map<String, EvalCaseDO> loadCases(List<EvalRecordDO> records) {
        Set<String> caseIds = records.stream().map(EvalRecordDO::getCaseId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (caseIds.isEmpty()) {
            return Map.of();
        }
        return caseMapper.selectBatchIds(caseIds).stream()
                .collect(Collectors.toMap(EvalCaseDO::getId, c -> c, (a, b) -> a));
    }

    private EvalRunVO toRunVO(EvalRunDO run,
                              Map<String, EvalDatasetVersionDO> versionMap,
                              Map<String, EvalDatasetDO> datasetMap) {
        EvalDatasetVersionDO version = versionMap.get(run.getDatasetVersionId());
        EvalDatasetDO dataset = version == null ? null : datasetMap.get(version.getDatasetId());
        return EvalRunVO.builder()
                .id(run.getId())
                .name(run.getName())
                .datasetVersionId(run.getDatasetVersionId())
                .datasetId(dataset == null ? null : dataset.getId())
                .datasetName(dataset == null ? null : dataset.getName())
                .datasetVersion(version == null ? null : version.getVersion())
                .baselineRunId(run.getBaselineRunId())
                .status(run.getStatus())
                .currentPhase(run.getCurrentPhase())
                .qualityVerdict(run.getQualityVerdict())
                .cancelRequested(run.getCancelRequested() != null && run.getCancelRequested() == 1)
                .configSnapshot(EvalJsonSupport.toMap(run.getConfigSnapshot()))
                .thresholdSnapshot(EvalJsonSupport.toMap(run.getThresholdSnapshot()))
                .tags(EvalJsonSupport.toMap(run.getTags()))
                .totalCount(run.getTotalCount())
                .successCount(run.getSuccessCount())
                .failedCount(run.getFailedCount())
                .progress(run.getProgress())
                .ragasEnabled(run.getRagasEnabled())
                .createdBy(run.getCreatedBy())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .errorMessage(run.getErrorMessage())
                .createTime(run.getCreateTime())
                .updateTime(run.getUpdateTime())
                .dualPathDisclaimer(DUAL_PATH_DISCLAIMER)
                .build();
    }

    private EvalRecordVO toRecordVO(EvalRecordDO record, EvalCaseDO evalCase) {
        return EvalRecordVO.builder()
                .id(record.getId())
                .runId(record.getRunId())
                .caseId(record.getCaseId())
                .queryId(evalCase == null ? null : evalCase.getQueryId())
                .status(record.getStatus())
                .question(record.getQuestion())
                .response(record.getResponse())
                .intentL1(evalCase == null ? null : evalCase.getIntentL1())
                .intentL2(evalCase == null ? null : evalCase.getIntentL2())
                .difficulty(evalCase == null ? null : evalCase.getDifficulty())
                .requiresRag(evalCase == null ? null : evalCase.getRequiresRag())
                .expectedAnswerType(evalCase == null ? null : evalCase.getExpectedAnswerType())
                .expectedDocIds(evalCase == null ? List.of() : EvalJsonSupport.toStringList(evalCase.getExpectedDocIds()))
                .niceToHaveDocIds(evalCase == null ? List.of() : EvalJsonSupport.toStringList(evalCase.getNiceToHaveDocIds()))
                .groundTruth(evalCase == null ? null : evalCase.getGroundTruth())
                .trapType(evalCase == null ? null : evalCase.getTrapType())
                .retrievedDocIds(EvalJsonSupport.toStringList(record.getRetrievedDocIds()))
                .retrievedChunkIds(EvalJsonSupport.toStringList(record.getRetrievedChunkIds()))
                .retrievedContexts(EvalJsonSupport.toStringList(
                        record.getRetrievedContexts() == null ? "[]" : record.getRetrievedContexts()))
                .retrievedContextDocIds(EvalJsonSupport.toStringList(record.getRetrievedContextDocIds()))
                .predictedIntents(EvalJsonSupport.toStringList(record.getPredictedIntents()))
                .intentPred(record.getIntentPred())
                .hasKb(record.getHasKb())
                .hasMcp(record.getHasMcp())
                .retrievalSkipped(record.getRetrievalSkipped())
                .skipReason(record.getSkipReason())
                .ttftMs(record.getTtftMs())
                .totalLatencyMs(record.getTotalLatencyMs())
                .evalLatencyMs(record.getEvalLatencyMs())
                .conversationId(record.getConversationId())
                .taskId(record.getTaskId())
                .traceId(record.getTraceId())
                .evidenceSource(record.getEvidenceSource())
                .errorCode(record.getErrorCode())
                .errorMessage(record.getErrorMessage())
                .rawPayload(EvalJsonSupport.toMap(record.getRawPayload()))
                .startedAt(record.getStartedAt())
                .finishedAt(record.getFinishedAt())
                .createTime(record.getCreateTime())
                .updateTime(record.getUpdateTime())
                .build();
    }
}
