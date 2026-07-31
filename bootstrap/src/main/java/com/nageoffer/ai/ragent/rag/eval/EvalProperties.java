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

package com.nageoffer.ai.ragent.rag.eval;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 评测配置：旁路接口（/rag/eval）与评测工作台共用前缀 {@code app.eval}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.eval")
public class EvalProperties {

    /**
     * 是否启用评测旁路 {@code GET /rag/eval}。
     * <p>
     * false：不注册 EvalController，零旁路开销；true：注册旁路接口供评测录制取证据。
     */
    private boolean enabled = false;

    /**
     * 是否启用评测工作台（Admin API、异步 Runner、专用线程池）。
     * <p>
     * 生产默认 false；与 {@link #enabled} 独立——可只开旁路不做工作台。
     */
    private boolean workbenchEnabled = false;

    /**
     * 同时处于录制阶段的最大 Run 数。
     */
    private int maxActiveRuns = 1;

    /**
     * 单 Run 内样本录制并发。
     */
    private int recordConcurrency = 1;

    /**
     * 单样本超时（秒）。
     */
    private int sampleTimeoutSeconds = 120;

    /**
     * 单样本失败重试次数。
     */
    private int sampleRetryTimes = 1;

    /**
     * 是否将 thinking 写入 t_eval_record；MVP 默认 false。
     */
    private boolean recordThinking = false;

    /**
     * Runner 租约心跳秒数。
     */
    private int leaseHeartbeatSeconds = 30;

    /**
     * Runner 租约过期秒数。
     */
    private int leaseExpireSeconds = 90;

    /**
     * RAGAS 评分服务配置。
     */
    private Ragas ragas = new Ragas();

    @Data
    public static class Ragas {

        /**
         * 是否允许发起 RAGAS 评分（仍受 Run 创建参数约束）。
         */
        private boolean enabled = false;

        /**
         * 评分服务 Base URL，例如 http://ragenteval:8089
         */
        private String endpoint = "";

        /**
         * 服务间调用 Token（可选）。
         */
        private String serviceToken = "";

        private int concurrency = 2;

        private int timeoutSeconds = 180;

        private int retryTimes = 2;

        private int maxSamplesPerRun = 500;

        /**
         * ragas_n 上限（1–3）。
         */
        private int maxIndependentRuns = 3;

        /**
         * 轮询外部 RAGAS job 的固定间隔（秒），不做退避。
         */
        private int pollIntervalSeconds = 10;

        /**
         * RAGAS Judge 专用聊天模型候选（与业务 {@code ai.chat} 分离，仅评测选用）。
         */
        private JudgeChat judgeChat = new JudgeChat();
    }

    @Data
    public static class JudgeChat {

        private String defaultModel = "gpt-5.4-mini";

        private List<JudgeModelCandidate> candidates = new ArrayList<>();
    }

    @Data
    public static class JudgeModelCandidate {

        private String id;

        private String provider;

        private String model;

        private Boolean enabled = true;
    }
}