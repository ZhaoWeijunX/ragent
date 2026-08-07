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

package com.nageoffer.ai.ragent.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.rag.controller.request.KeywordReindexRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.KeywordReindexCreatedVO;
import com.nageoffer.ai.ragent.rag.controller.vo.KeywordReindexJobVO;
import com.nageoffer.ai.ragent.rag.service.KeywordReindexService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * ES 重建文档索引
 * 本接口只用于管理员回填历史文档的 ES索引，前端无入口，暂不入主分支
 */
@RestController
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "rag.keyword", name = "type", havingValue = "es")
public class KeywordReindexController {

    private final KeywordReindexService reindexService;

    @PostMapping("/admin/rag/keyword/reindex")
    public Result<KeywordReindexCreatedVO> create(@RequestBody KeywordReindexRequest request) {
        requireAdmin();
        return Results.success(reindexService.create(request));
    }

    @GetMapping("/admin/rag/keyword/reindex/{jobId}")
    public Result<KeywordReindexJobVO> get(@PathVariable String jobId) {
        requireAdmin();
        return Results.success(reindexService.get(jobId));
    }

    private void requireAdmin() {
        StpUtil.checkRole("admin");
    }
}
