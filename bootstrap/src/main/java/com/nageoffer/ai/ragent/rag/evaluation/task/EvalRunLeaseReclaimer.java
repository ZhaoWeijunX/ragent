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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.rag.eval.EvalProperties;
import com.nageoffer.ai.ragent.rag.evaluation.constant.EvalWorkbenchConstants;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalRunDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 租约过期 / PENDING Run 恢复调度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ragent.eval", name = "workbench-enabled", havingValue = "true")
public class EvalRunLeaseReclaimer {

    private static final Set<String> ACTIVE = Set.of(
            EvalWorkbenchConstants.RUN_PENDING,
            EvalWorkbenchConstants.RUN_RECORDING,
            EvalWorkbenchConstants.RUN_DETERMINISTIC_SCORING,
            EvalWorkbenchConstants.RUN_RAGAS_SCORING,
            EvalWorkbenchConstants.RUN_REPORTING
    );

    private final EvalRunMapper runMapper;
    private final EvalRunWorker runWorker;
    private final EvalProperties evalProperties;

    @Scheduled(fixedDelayString = "${ragent.eval.lease-heartbeat-seconds:30}000")
    public void reclaim() {
        Date now = new Date();
        List<EvalRunDO> candidates = runMapper.selectList(Wrappers.lambdaQuery(EvalRunDO.class)
                .in(EvalRunDO::getStatus, ACTIVE)
                .and(w -> w.isNull(EvalRunDO::getLeaseOwner)
                        .or().lt(EvalRunDO::getLeaseExpireAt, now)
                        .or().eq(EvalRunDO::getStatus, EvalWorkbenchConstants.RUN_PENDING))
                .orderByAsc(EvalRunDO::getCreateTime)
                .last("LIMIT 5"));
        if (candidates.isEmpty()) {
            return;
        }
        long activeRecording = countActiveRecording();
        int maxActive = Math.max(1, evalProperties.getMaxActiveRuns());
        for (EvalRunDO run : candidates) {
            if (activeRecording >= maxActive) {
                break;
            }
            if (EvalWorkbenchConstants.RUN_PENDING.equals(run.getStatus())
                    || run.getLeaseExpireAt() == null
                    || run.getLeaseExpireAt().before(now)) {
                log.info("恢复/领取评测 Run id={} status={}", run.getId(), run.getStatus());
                runWorker.submit(run.getId());
                activeRecording++;
            }
        }
    }

    private long countActiveRecording() {
        Long count = runMapper.selectCount(Wrappers.lambdaQuery(EvalRunDO.class)
                .in(EvalRunDO::getStatus,
                        EvalWorkbenchConstants.RUN_RECORDING,
                        EvalWorkbenchConstants.RUN_DETERMINISTIC_SCORING,
                        EvalWorkbenchConstants.RUN_RAGAS_SCORING,
                        EvalWorkbenchConstants.RUN_REPORTING)
                .isNotNull(EvalRunDO::getLeaseOwner)
                .gt(EvalRunDO::getLeaseExpireAt, new Date()));
        return count == null ? 0 : count;
    }
}
