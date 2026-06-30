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

package com.nageoffer.ai.ragent.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.knowledge.controller.request.FeishuWikiImportRequest;
import com.nageoffer.ai.ragent.knowledge.controller.vo.FeishuWikiDiscoverVO;
import com.nageoffer.ai.ragent.knowledge.controller.vo.FeishuWikiImportItemVO;
import com.nageoffer.ai.ragent.knowledge.controller.vo.FeishuWikiImportJobVO;
import com.nageoffer.ai.ragent.knowledge.service.FeishuWikiImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 飞书 Wiki 批量导入控制器
 */
@RestController
@RequiredArgsConstructor
@Validated
public class FeishuWikiImportController {

    private final FeishuWikiImportService importService;

    /**
     * 预览可导入的 Wiki 页面列表
     */
    @PostMapping("/knowledge-base/{kb-id}/feishu-wiki/discover")
    public Result<FeishuWikiDiscoverVO> discover(@PathVariable("kb-id") String kbId,
                                                 @RequestBody FeishuWikiImportRequest request) {
        return Results.success(importService.discover(kbId, request));
    }

    /**
     * 启动 Wiki 批量导入任务（异步 MQ 逐页处理）
     */
    @PostMapping("/knowledge-base/{kb-id}/feishu-wiki/import")
    public Result<FeishuWikiImportJobVO> startImport(@PathVariable("kb-id") String kbId,
                                                     @RequestBody FeishuWikiImportRequest request) {
        return Results.success(importService.startImport(kbId, request));
    }

    /**
     * 查询导入任务进度
     */
    @GetMapping("/knowledge-base/feishu-wiki/import/{jobId}")
    public Result<FeishuWikiImportJobVO> getJob(@PathVariable String jobId) {
        return Results.success(importService.getJob(jobId));
    }

    /**
     * 分页查询导入子项
     */
    @GetMapping("/knowledge-base/feishu-wiki/import/{jobId}/items")
    public Result<IPage<FeishuWikiImportItemVO>> listItems(@PathVariable String jobId,
                                                           Page<FeishuWikiImportItemVO> page) {
        return Results.success(importService.listItems(jobId, page));
    }
}
