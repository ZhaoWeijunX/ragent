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

package com.nageoffer.ai.ragent.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.ai.ragent.knowledge.controller.request.FeishuWikiImportRequest;
import com.nageoffer.ai.ragent.knowledge.controller.vo.FeishuWikiDiscoverVO;
import com.nageoffer.ai.ragent.knowledge.controller.vo.FeishuWikiImportItemVO;
import com.nageoffer.ai.ragent.knowledge.controller.vo.FeishuWikiImportJobVO;

/**
 * 飞书 Wiki 批量导入服务
 */
public interface FeishuWikiImportService {

    FeishuWikiDiscoverVO discover(String kbId, FeishuWikiImportRequest request);

    FeishuWikiImportJobVO startImport(String kbId, FeishuWikiImportRequest request);

    void processNextItem(String jobId);

    FeishuWikiImportJobVO getJob(String jobId);

    IPage<FeishuWikiImportItemVO> listItems(String jobId, Page<FeishuWikiImportItemVO> page);
}
