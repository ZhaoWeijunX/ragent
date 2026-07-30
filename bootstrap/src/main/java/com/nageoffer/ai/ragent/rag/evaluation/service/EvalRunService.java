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

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRecordPageRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRunCreateRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRunPageRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRecordVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRunVO;

public interface EvalRunService {

    IPage<EvalRunVO> pageRuns(EvalRunPageRequest request);

    EvalRunVO getRun(String runId);

    String createRun(EvalRunCreateRequest request);

    void cancelRun(String runId);

    /**
     * 失败样本幂等重跑（resume）。
     */
    void resumeRun(String runId);

    IPage<EvalRecordVO> pageRecords(String runId, EvalRecordPageRequest request);

    EvalRecordVO getRecord(String recordId);
}
