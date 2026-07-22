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

import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.rag.service.RecommendedQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 推荐追问问题控制器
 * <p>
 * 答案完成后的懒加载入口，与 chat 流式接口解耦：命中已落库推荐问题直接返回，否则现生成并落库
 * 不占用 chat 关键路径，保证 /rag/v3/chat 性能不受影响
 */
@RestController
@RequiredArgsConstructor
public class RecommendedQuestionController {

    private final RecommendedQuestionService recommendedQuestionService;

    /**
     * 获取指定 assistant 消息的推荐追问问题
     */
    @GetMapping("/conversations/messages/{messageId}/recommended-questions")
    public Result<List<String>> recommend(@PathVariable String messageId) {
        return Results.success(recommendedQuestionService.getOrGenerate(messageId, UserContext.getUserId()));
    }
}
