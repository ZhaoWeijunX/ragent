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

import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRunCompareVO;

public interface EvalCompareService {

    /**
     * 同数据集版本 Run 对比：一次返回自建 + RAGAS（若有）及 Judge 模型。
     * 版本不一致或同一 Run 直接抛业务错误。
     */
    EvalRunCompareVO compare(String runId, String baselineRunId);
}
