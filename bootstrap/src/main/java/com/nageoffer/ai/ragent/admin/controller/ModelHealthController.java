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

package com.nageoffer.ai.ragent.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.admin.controller.vo.ModelProbeReportVO;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.infra.enums.ModelCapability;
import com.nageoffer.ai.ragent.infra.model.ModelProbeResult;
import com.nageoffer.ai.ragent.infra.model.ModelProbeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型健康主动探测
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/models")
public class ModelHealthController {

    private final ModelProbeService modelProbeService;

    @PostMapping("/probe")
    public Result<ModelProbeReportVO> probeAll() {
        requireAdmin();
        return Results.success(toReport(modelProbeService.probeAll()));
    }

    @PostMapping("/probe/{capability}")
    public Result<ModelProbeReportVO> probeCapability(@PathVariable String capability) {
        requireAdmin();
        return Results.success(toReport(modelProbeService.probe(parseCapability(capability))));
    }

    @PostMapping("/probe/{capability}/{modelId}")
    public Result<ModelProbeReportVO> probeOne(@PathVariable String capability, @PathVariable String modelId) {
        requireAdmin();
        ModelProbeResult result = modelProbeService.probeOne(parseCapability(capability), modelId);
        return Results.success(toReport(List.of(result)));
    }

    private void requireAdmin() {
        StpUtil.checkRole("admin");
    }

    private ModelCapability parseCapability(String capability) {
        if (capability == null) {
            throw new IllegalArgumentException("capability is required");
        }
        return switch (capability.trim().toLowerCase()) {
            case "chat" -> ModelCapability.CHAT;
            case "embedding" -> ModelCapability.EMBEDDING;
            case "rerank" -> ModelCapability.RERANK;
            default -> throw new IllegalArgumentException("Unsupported capability: " + capability);
        };
    }

    private ModelProbeReportVO toReport(List<ModelProbeResult> results) {
        return ModelProbeReportVO.builder()
                .probedAt(System.currentTimeMillis())
                .results(results.stream().map(this::toItem).toList())
                .build();
    }

    private ModelProbeReportVO.ModelProbeItemVO toItem(ModelProbeResult result) {
        return ModelProbeReportVO.ModelProbeItemVO.builder()
                .capability(result.getCapability())
                .modelId(result.getModelId())
                .provider(result.getProvider())
                .model(result.getModel())
                .healthy(result.isHealthy())
                .latencyMs(result.getLatencyMs())
                .errorMessage(result.getErrorMessage())
                .build();
    }
}
