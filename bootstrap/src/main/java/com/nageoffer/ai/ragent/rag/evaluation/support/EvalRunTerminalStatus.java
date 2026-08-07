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

package com.nageoffer.ai.ragent.rag.evaluation.support;

import com.nageoffer.ai.ragent.rag.evaluation.constant.EvalWorkbenchConstants;

/**
 * Run 终态判定（阶段 3 录制完成后；阶段 4 评分失败口径可再扩展）。
 */
public final class EvalRunTerminalStatus {

    private EvalRunTerminalStatus() {
    }

    public static String resolve(boolean cancelRequested, int successCount, int failedCount) {
        if (cancelRequested) {
            return EvalWorkbenchConstants.RUN_CANCELLED;
        }
        if (successCount <= 0) {
            return EvalWorkbenchConstants.RUN_FAILED;
        }
        if (failedCount > 0) {
            return EvalWorkbenchConstants.RUN_PARTIAL_SUCCESS;
        }
        return EvalWorkbenchConstants.RUN_COMPLETED;
    }
}
