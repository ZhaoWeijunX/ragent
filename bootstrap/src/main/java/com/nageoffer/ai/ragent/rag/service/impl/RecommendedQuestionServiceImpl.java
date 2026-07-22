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

package com.nageoffer.ai.ragent.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMessageMapper;
import com.nageoffer.ai.ragent.rag.service.RecommendedQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 推荐追问问题服务默认实现
 * <p>
 * 命中已落库推荐问题直接返回；否则取答案 + 前一条用户提问，FAST 档生成后落库再返回；
 * 生成无结果则返回空列表且不落库（允许后续重试，避免缓存投毒）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendedQuestionServiceImpl implements RecommendedQuestionService {

    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_USER = "user";

    private final ConversationMessageMapper conversationMessageMapper;
    private final RecommendedQuestionGenerator generator;

    @Override
    public List<String> getOrGenerate(String messageId, String userId) {
        ConversationMessageDO message = loadAssistantMessage(messageId, userId);

        // 缓存命中：已落库的推荐问题直接返回，零模型调用
        List<String> cached = message.getRecommendedQuestions();
        if (CollUtil.isNotEmpty(cached)) {
            return cached;
        }

        // 取该答案的前一条用户提问（无则传空，生成器内 nullToEmpty 兜底）
        String question = loadPreviousQuestion(message);

        List<String> generated = generator.generate(question, message.getContent(), message.getRetrievedChunks());
        if (CollUtil.isEmpty(generated)) {
            // 宁缺毋滥：无合适追问则不落库，允许后续重试
            return List.of();
        }

        // 落库：仅 SET recommended_questions（MP 默认 NOT_NULL 策略只更新非空字段，updateTime 自动填充）
        ConversationMessageDO update = new ConversationMessageDO();
        update.setId(message.getId());
        update.setRecommendedQuestions(generated);
        conversationMessageMapper.updateById(update);
        return generated;
    }

    /**
     * 定位 assistant 消息并校验归属（他人消息或非 assistant 消息一律视为不存在）
     */
    private ConversationMessageDO loadAssistantMessage(String messageId, String userId) {
        ConversationMessageDO message = conversationMessageMapper.selectOne(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getId, messageId)
                        .eq(ConversationMessageDO::getUserId, userId)
                        .eq(ConversationMessageDO::getRole, ROLE_ASSISTANT)
                        .eq(ConversationMessageDO::getDeleted, 0)
        );
        if (message == null) {
            throw new ClientException("消息不存在");
        }
        return message;
    }

    /**
     * 取同会话中早于该答案的最近一条用户提问
     */
    private String loadPreviousQuestion(ConversationMessageDO message) {
        ConversationMessageDO previous = conversationMessageMapper.selectOne(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getConversationId, message.getConversationId())
                        .eq(ConversationMessageDO::getUserId, message.getUserId())
                        .eq(ConversationMessageDO::getRole, ROLE_USER)
                        .eq(ConversationMessageDO::getDeleted, 0)
                        .lt(ConversationMessageDO::getCreateTime, message.getCreateTime())
                        .orderByDesc(ConversationMessageDO::getCreateTime)
                        .last("limit 1")
        );
        return previous == null ? null : previous.getContent();
    }
}
