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

package com.nageoffer.ai.ragent.rag.service;

import java.util.List;

/**
 * 推荐追问问题服务
 * <p>
 * 答案完成后的懒加载入口：命中已落库的推荐问题直接返回，否则 FAST 档生成后落库再返回
 * 不在 chat 流式关键路径内，由独立接口按需触发
 */
public interface RecommendedQuestionService {

    /**
     * 获取指定 assistant 消息的推荐追问问题（未生成则现生成并落库）
     *
     * @param messageId 消息ID（须为 assistant 消息）
     * @param userId    用户ID（校验归属）
     * @return 推荐追问问题列表（可能为空，表示无合适追问）
     */
    List<String> getOrGenerate(String messageId, String userId);
}
