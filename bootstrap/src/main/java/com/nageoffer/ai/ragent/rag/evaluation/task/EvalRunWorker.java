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

package com.nageoffer.ai.ragent.rag.evaluation.task;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.rag.eval.EvalProperties;
import com.nageoffer.ai.ragent.rag.evaluation.constant.EvalWorkbenchConstants;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalCaseDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalRecordDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalRunDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalCaseMapper;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalRecordMapper;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalRunMapper;
import com.nageoffer.ai.ragent.rag.evaluation.runner.EvalDualPathSampleRecorder;
import com.nageoffer.ai.ragent.rag.evaluation.service.EvalScoreService;
import com.nageoffer.ai.ragent.rag.evaluation.support.EvalJsonSupport;
import com.nageoffer.ai.ragent.rag.evaluation.support.EvalRunTerminalStatus;
import com.nageoffer.ai.ragent.user.dao.entity.UserDO;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Run 录制执行器：租约心跳、逐样本双路径录制、终态结算。
 * <p>
 * 阶段 3–4：双路径录制后执行确定性评分并结算终态。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.eval", name = "workbench-enabled", havingValue = "true")
public class EvalRunWorker {

    private static final Set<String> TERMINAL = Set.of(
            EvalWorkbenchConstants.RUN_COMPLETED,
            EvalWorkbenchConstants.RUN_PARTIAL_SUCCESS,
            EvalWorkbenchConstants.RUN_FAILED,
            EvalWorkbenchConstants.RUN_CANCELLED
    );

    private static final Set<String> SUCCESS_RECORD = Set.of(
            EvalWorkbenchConstants.RECORD_SUCCESS,
            EvalWorkbenchConstants.RECORD_REFUSED
    );

    private final EvalRunMapper runMapper;
    private final EvalCaseMapper caseMapper;
    private final EvalRecordMapper recordMapper;
    private final EvalDualPathSampleRecorder sampleRecorder;
    private final EvalProperties evalProperties;
    private final UserMapper userMapper;
    private final EvalScoreService evalScoreService;
    private final Executor evalRecordExecutor;
    private final String leaseOwnerId = resolveLeaseOwner();
    /** 单样本重跑目标：runId → caseId（submit 时写入，execute 开头取出）。 */
    private final ConcurrentHashMap<String, String> singleCaseTargets = new ConcurrentHashMap<>();

    public EvalRunWorker(EvalRunMapper runMapper,
                         EvalCaseMapper caseMapper,
                         EvalRecordMapper recordMapper,
                         EvalDualPathSampleRecorder sampleRecorder,
                         EvalProperties evalProperties,
                         UserMapper userMapper,
                         EvalScoreService evalScoreService,
                         @Qualifier("evalRecordExecutor") Executor evalRecordExecutor) {
        this.runMapper = runMapper;
        this.caseMapper = caseMapper;
        this.recordMapper = recordMapper;
        this.sampleRecorder = sampleRecorder;
        this.evalProperties = evalProperties;
        this.userMapper = userMapper;
        this.evalScoreService = evalScoreService;
        this.evalRecordExecutor = evalRecordExecutor;
    }

    public void submit(String runId) {
        // 全量/失败 resume 不得误用残留的单样本目标
        singleCaseTargets.remove(runId);
        evalRecordExecutor.execute(() -> {
            try {
                execute(runId);
            } catch (Exception ex) {
                log.error("EvalRunWorker 执行失败 runId={}", runId, ex);
                markFailed(runId, ex.getMessage());
            }
        });
    }

    /**
     * 终态 Run 单 Case 强制重录（不跳过已成功 Record），收尾时重算自建指标且不自动 RAGAS。
     */
    public void submitSingleCaseRerun(String runId, String caseId) {
        if (StrUtil.isBlank(runId) || StrUtil.isBlank(caseId)) {
            throw new IllegalArgumentException("runId/caseId 不能为空");
        }
        singleCaseTargets.put(runId, caseId);
        evalRecordExecutor.execute(() -> {
            try {
                execute(runId);
            } catch (Exception ex) {
                log.error("EvalRunWorker 单样本重跑失败 runId={} caseId={}", runId, caseId, ex);
                singleCaseTargets.remove(runId);
                markFailed(runId, ex.getMessage());
            }
        });
    }

    public void execute(String runId) {
        if (!tryClaimLease(runId)) {
            log.debug("未能领取租约，跳过 runId={}", runId);
            return;
        }

        EvalRunDO run = runMapper.selectById(runId);
        if (run == null || TERMINAL.contains(run.getStatus())) {
            releaseLease(runId);
            return;
        }

        bindUserContext(run.getCreatedBy());
        AtomicBoolean heartbeatStop = new AtomicBoolean(false);
        Thread heartbeat = startHeartbeat(runId, heartbeatStop);
        try {
            // PENDING 或恢复中的非终态 → RECORDING
            runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                    .eq(EvalRunDO::getId, runId)
                    .notIn(EvalRunDO::getStatus, TERMINAL)
                    .set(EvalRunDO::getStatus, EvalWorkbenchConstants.RUN_RECORDING)
                    .set(EvalRunDO::getCurrentPhase, EvalWorkbenchConstants.RUN_RECORDING)
                    .set(EvalRunDO::getErrorMessage, null));

            run = runMapper.selectById(runId);
            if (run.getStartedAt() == null) {
                runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                        .eq(EvalRunDO::getId, runId)
                        .set(EvalRunDO::getStartedAt, new Date()));
            }

            List<EvalCaseDO> cases = caseMapper.selectList(Wrappers.lambdaQuery(EvalCaseDO.class)
                    .eq(EvalCaseDO::getDatasetVersionId, run.getDatasetVersionId())
                    .orderByAsc(EvalCaseDO::getQueryId));

            String onlyCaseId = singleCaseTargets.remove(runId);
            boolean singleCaseRerun = StrUtil.isNotBlank(onlyCaseId);
            if (singleCaseRerun) {
                cases = cases.stream().filter(c -> onlyCaseId.equals(c.getId())).toList();
                if (cases.isEmpty()) {
                    markFailed(runId, "单样本重跑失败：Case 不在当前数据集版本中 caseId=" + onlyCaseId);
                    return;
                }
                log.info("单样本重跑 runId={} caseId={}", runId, onlyCaseId);
            }

            // 全量/失败 resume：已有成功 Record 时跳过成功样本；单样本重跑强制覆盖
            boolean skipSuccess = !singleCaseRerun && hasSuccessfulRecord(runId);

            for (EvalCaseDO evalCase : cases) {
                if (isCancelRequested(runId)) {
                    break;
                }
                if (!renewLease(runId)) {
                    log.warn("租约续期失败，中止本轮 runId={}", runId);
                    return;
                }
                if (skipSuccess && shouldSkipCase(runId, evalCase.getId())) {
                    continue;
                }

                int attempts = Math.max(0, evalProperties.getSampleRetryTimes()) + 1;
                EvalRecordDO recorded = null;
                for (int i = 0; i < attempts; i++) {
                    try {
                        recorded = sampleRecorder.record(evalCase).getRecord();
                        if (recorded != null && SUCCESS_RECORD.contains(recorded.getStatus())) {
                            break;
                        }
                    } catch (Exception ex) {
                        log.warn("样本录制异常 runId={} caseId={} attempt={}", runId, evalCase.getId(), i + 1, ex);
                        recorded = EvalRecordDO.builder()
                                .caseId(evalCase.getId())
                                .status(EvalWorkbenchConstants.RECORD_ERROR)
                                .question(evalCase.getQuery())
                                .retrievedDocIds("[]")
                                .retrievedChunkIds("[]")
                                .retrievedContextDocIds("[]")
                                .predictedIntents("[]")
                                .retrievalSkipped(false)
                                .evidenceSource(EvalWorkbenchConstants.EVIDENCE_DUAL_PATH)
                                .errorCode("SAMPLE_EXCEPTION")
                                .errorMessage(ex.getMessage())
                                .rawPayload("{}")
                                .startedAt(new Date())
                                .finishedAt(new Date())
                                .build();
                    }
                }
                if (recorded != null) {
                    upsertRecord(runId, recorded);
                    refreshCounters(runId);
                }
            }

            if (isCancelRequested(runId)) {
                markUnstartedCasesCancelled(runId, cases);
            }

            finalizeRun(runId, singleCaseRerun);
        } finally {
            heartbeatStop.set(true);
            heartbeat.interrupt();
            releaseLease(runId);
            UserContext.clear();
        }
    }

    private void finalizeRun(String runId, boolean singleCaseRerun) {
        EvalRunDO run = runMapper.selectById(runId);
        if (run == null) {
            return;
        }
        refreshCounters(runId);
        if (isCancelRequested(runId)) {
            runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                    .eq(EvalRunDO::getId, runId)
                    .set(EvalRunDO::getStatus, EvalWorkbenchConstants.RUN_CANCELLED)
                    .set(EvalRunDO::getCurrentPhase, EvalWorkbenchConstants.RUN_CANCELLED)
                    .set(EvalRunDO::getFinishedAt, new Date())
                    .set(EvalRunDO::getProgress, 100));
            return;
        }

        // 阶段 4：确定性评分 + 报告占位收尾
        runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                .eq(EvalRunDO::getId, runId)
                .set(EvalRunDO::getStatus, EvalWorkbenchConstants.RUN_DETERMINISTIC_SCORING)
                .set(EvalRunDO::getCurrentPhase, EvalWorkbenchConstants.RUN_DETERMINISTIC_SCORING));
        try {
            evalScoreService.scoreDeterministic(runId);
        } catch (Exception ex) {
            log.error("自建指标评分失败，仍按录制结果结算终态 runId={}", runId, ex);
            runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                    .eq(EvalRunDO::getId, runId)
                    .set(EvalRunDO::getErrorMessage, "deterministic scoring failed: " + ex.getMessage()));
        }

        // 阶段 5：可选自动 RAGAS（单样本重跑跳过，由用户手动触发）
        EvalRunDO afterDet = runMapper.selectById(runId);
        if (!singleCaseRerun && afterDet != null && shouldAutoStartRagas(afterDet)) {
            runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                    .eq(EvalRunDO::getId, runId)
                    .set(EvalRunDO::getStatus, EvalWorkbenchConstants.RUN_RAGAS_SCORING)
                    .set(EvalRunDO::getCurrentPhase, EvalWorkbenchConstants.RUN_RAGAS_SCORING));
            try {
                evalScoreService.scoreRagas(runId);
            } catch (Exception ex) {
                log.error("RAGAS 评分失败（不影响自建指标报告） runId={}", runId, ex);
            }
        }

        runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                .eq(EvalRunDO::getId, runId)
                .set(EvalRunDO::getStatus, EvalWorkbenchConstants.RUN_REPORTING)
                .set(EvalRunDO::getCurrentPhase, EvalWorkbenchConstants.RUN_REPORTING));

        refreshCounters(runId);
        run = runMapper.selectById(runId);
        int success = run.getSuccessCount() == null ? 0 : run.getSuccessCount();
        int failed = run.getFailedCount() == null ? 0 : run.getFailedCount();
        String terminal = EvalRunTerminalStatus.resolve(false, success, failed);
        runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                .eq(EvalRunDO::getId, runId)
                .set(EvalRunDO::getStatus, terminal)
                .set(EvalRunDO::getCurrentPhase, terminal)
                .set(EvalRunDO::getFinishedAt, new Date())
                .set(EvalRunDO::getProgress, 100)
                .set(EvalRunDO::getErrorMessage, EvalWorkbenchConstants.RUN_FAILED.equals(terminal)
                        ? StrUtil.blankToDefault(run.getErrorMessage(), "no successful samples")
                        : (StrUtil.startWith(run.getErrorMessage(), "deterministic scoring failed")
                        ? run.getErrorMessage() : null)));
    }

    private void markUnstartedCasesCancelled(String runId, List<EvalCaseDO> cases) {
        Date now = new Date();
        for (EvalCaseDO evalCase : cases) {
            EvalRecordDO existing = recordMapper.selectOne(Wrappers.lambdaQuery(EvalRecordDO.class)
                    .eq(EvalRecordDO::getRunId, runId)
                    .eq(EvalRecordDO::getCaseId, evalCase.getId())
                    .last("LIMIT 1"));
            // 已有终态录制（成功/拒答/失败）保留；仅补未开跑样本
            if (existing != null) {
                continue;
            }
            upsertRecord(runId, EvalRecordDO.builder()
                    .caseId(evalCase.getId())
                    .status(EvalWorkbenchConstants.RECORD_CANCELLED)
                    .question(evalCase.getQuery())
                    .retrievedDocIds("[]")
                    .retrievedChunkIds("[]")
                    .retrievedContextDocIds("[]")
                    .predictedIntents("[]")
                    .retrievalSkipped(false)
                    .evidenceSource(EvalWorkbenchConstants.EVIDENCE_DUAL_PATH)
                    .errorCode("RUN_CANCELLED")
                    .errorMessage("run cancelled before sample started")
                    .rawPayload("{}")
                    .startedAt(now)
                    .finishedAt(now)
                    .build());
        }
        refreshCounters(runId);
    }

    private boolean shouldSkipCase(String runId, String caseId) {
        EvalRecordDO existing = recordMapper.selectOne(Wrappers.lambdaQuery(EvalRecordDO.class)
                .eq(EvalRecordDO::getRunId, runId)
                .eq(EvalRecordDO::getCaseId, caseId)
                .last("LIMIT 1"));
        return existing != null && SUCCESS_RECORD.contains(existing.getStatus());
    }

    private boolean hasSuccessfulRecord(String runId) {
        Long count = recordMapper.selectCount(Wrappers.lambdaQuery(EvalRecordDO.class)
                .eq(EvalRecordDO::getRunId, runId)
                .in(EvalRecordDO::getStatus, SUCCESS_RECORD));
        return count != null && count > 0;
    }

    private void upsertRecord(String runId, EvalRecordDO recorded) {
        recorded.setRunId(runId);
        EvalRecordDO existing = recordMapper.selectOne(Wrappers.lambdaQuery(EvalRecordDO.class)
                .eq(EvalRecordDO::getRunId, runId)
                .eq(EvalRecordDO::getCaseId, recorded.getCaseId())
                .last("LIMIT 1"));
        if (existing == null) {
            if (StrUtil.isBlank(recorded.getId())) {
                recorded.setId(IdUtil.getSnowflakeNextIdStr());
            }
            recordMapper.insert(recorded);
            return;
        }
        recorded.setId(existing.getId());
        recordMapper.updateById(recorded);
    }

    private void refreshCounters(String runId) {
        EvalRunDO run = runMapper.selectById(runId);
        if (run == null) {
            return;
        }
        Long success = recordMapper.selectCount(Wrappers.lambdaQuery(EvalRecordDO.class)
                .eq(EvalRecordDO::getRunId, runId)
                .in(EvalRecordDO::getStatus, SUCCESS_RECORD));
        Long failed = recordMapper.selectCount(Wrappers.lambdaQuery(EvalRecordDO.class)
                .eq(EvalRecordDO::getRunId, runId)
                .eq(EvalRecordDO::getStatus, EvalWorkbenchConstants.RECORD_ERROR));
        Long cancelled = recordMapper.selectCount(Wrappers.lambdaQuery(EvalRecordDO.class)
                .eq(EvalRecordDO::getRunId, runId)
                .eq(EvalRecordDO::getStatus, EvalWorkbenchConstants.RECORD_CANCELLED));
        int successCount = success == null ? 0 : success.intValue();
        int failedCount = failed == null ? 0 : failed.intValue();
        int cancelledCount = cancelled == null ? 0 : cancelled.intValue();
        int total = run.getTotalCount() == null ? 0 : run.getTotalCount();
        int done = Math.min(total, successCount + failedCount + cancelledCount);
        int progress = total <= 0 ? 0 : (int) Math.min(100, Math.round(done * 100.0 / total));
        runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                .eq(EvalRunDO::getId, runId)
                .set(EvalRunDO::getSuccessCount, successCount)
                .set(EvalRunDO::getFailedCount, failedCount)
                .set(EvalRunDO::getProgress, progress));
    }

    public boolean tryClaimLease(String runId) {
        Date expireAt = new Date(System.currentTimeMillis()
                + Math.max(30, evalProperties.getLeaseExpireSeconds()) * 1000L);
        Date now = new Date();
        int updated = runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                .eq(EvalRunDO::getId, runId)
                .notIn(EvalRunDO::getStatus, TERMINAL)
                .and(w -> w.isNull(EvalRunDO::getLeaseOwner)
                        .or().lt(EvalRunDO::getLeaseExpireAt, now)
                        .or().eq(EvalRunDO::getLeaseOwner, leaseOwnerId))
                .set(EvalRunDO::getLeaseOwner, leaseOwnerId)
                .set(EvalRunDO::getLeaseExpireAt, expireAt));
        return updated > 0;
    }

    private boolean renewLease(String runId) {
        Date expireAt = new Date(System.currentTimeMillis()
                + Math.max(30, evalProperties.getLeaseExpireSeconds()) * 1000L);
        int updated = runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                .eq(EvalRunDO::getId, runId)
                .eq(EvalRunDO::getLeaseOwner, leaseOwnerId)
                .notIn(EvalRunDO::getStatus, TERMINAL)
                .set(EvalRunDO::getLeaseExpireAt, expireAt));
        return updated > 0;
    }

    private void releaseLease(String runId) {
        runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                .eq(EvalRunDO::getId, runId)
                .eq(EvalRunDO::getLeaseOwner, leaseOwnerId)
                .set(EvalRunDO::getLeaseOwner, null)
                .set(EvalRunDO::getLeaseExpireAt, null));
    }

    private Thread startHeartbeat(String runId, AtomicBoolean stop) {
        long intervalMs = Math.max(5, evalProperties.getLeaseHeartbeatSeconds()) * 1000L;
        Thread t = new Thread(() -> {
            while (!stop.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(intervalMs);
                    renewLease(runId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ex) {
                    log.debug("租约心跳失败 runId={}", runId, ex);
                }
            }
        }, "eval-lease-" + runId);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private boolean isCancelRequested(String runId) {
        EvalRunDO run = runMapper.selectById(runId);
        return run != null && run.getCancelRequested() != null && run.getCancelRequested() == 1;
    }

    private void markFailed(String runId, String message) {
        runMapper.update(null, Wrappers.lambdaUpdate(EvalRunDO.class)
                .eq(EvalRunDO::getId, runId)
                .notIn(EvalRunDO::getStatus, TERMINAL)
                .set(EvalRunDO::getStatus, EvalWorkbenchConstants.RUN_FAILED)
                .set(EvalRunDO::getCurrentPhase, EvalWorkbenchConstants.RUN_FAILED)
                .set(EvalRunDO::getErrorMessage, message)
                .set(EvalRunDO::getFinishedAt, new Date())
                .set(EvalRunDO::getLeaseOwner, null)
                .set(EvalRunDO::getLeaseExpireAt, null));
    }

    private void bindUserContext(String userId) {
        if (StrUtil.isBlank(userId)) {
            UserContext.set(LoginUser.builder().userId("eval-system").username("eval-system").role("admin").build());
            return;
        }
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            UserContext.set(LoginUser.builder().userId(userId).username(userId).role("admin").build());
            return;
        }
        UserContext.set(LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .avatar(user.getAvatar())
                .build());
    }

    private static String resolveLeaseOwner() {
        try {
            return IdUtil.getSnowflakeNextIdStr() + "@" + InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return IdUtil.getSnowflakeNextIdStr() + "@unknown";
        }
    }

    /**
     * 自动 RAGAS：需 Run.ragasEnabled，且 configSnapshot.ragas.autoStart=true。
     * 兼容旧快照：仅有 ragasEnabled、无 autoStart 字段时视为自动开始（与拆分前行为一致）。
     */
    private static boolean shouldAutoStartRagas(EvalRunDO run) {
        if (!Boolean.TRUE.equals(run.getRagasEnabled())) {
            return false;
        }
        Map<String, Object> snap = EvalJsonSupport.toMap(run.getConfigSnapshot());
        Object ragasObj = snap.get("ragas");
        if (!(ragasObj instanceof Map<?, ?> raw)) {
            return true;
        }
        Object autoStart = raw.get("autoStart");
        if (autoStart == null) {
            return true;
        }
        if (autoStart instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(autoStart));
    }
}
