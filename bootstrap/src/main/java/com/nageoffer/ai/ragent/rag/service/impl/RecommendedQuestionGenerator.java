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
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.convention.GroundingChunk;
import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.enums.Tier;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.RECOMMENDED_QUESTIONS_PROMPT_PATH;

/**
 * 推荐追问问题生成器
 * <p>
 * 答案完成后的 LLM 派生调用（FAST 档），由懒加载接口按需触发，不在 chat 流式关键路径内
 * 拆为独立 bean 是为了让 Spring AOP 的 {@link RagTraceNode} 拦截生效（同类 self-call 不触发 proxy）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendedQuestionGenerator {

    private static final int DEFAULT_RECOMMEND_COUNT = 3;

    private final PromptTemplateLoader promptTemplateLoader;
    private final LLMService llmService;

    @RagTraceNode(name = "recommended-question-gen", type = "RECOMMEND_GEN")
    public List<String> generate(String question, String answer, List<GroundingChunk> chunks) {
        int count = DEFAULT_RECOMMEND_COUNT;
        // chunk 文本最后单独替换（不与其余槽同批 fillSlots），避免片段内字面 {question} 等被误伤
        String prompt = promptTemplateLoader.render(
                RECOMMENDED_QUESTIONS_PROMPT_PATH,
                Map.of(
                        "question", StrUtil.nullToEmpty(question),
                        "answer", StrUtil.nullToEmpty(answer),
                        "count", String.valueOf(count)
                )
        );
        prompt = prompt.replace("{chunks}", buildChunksText(chunks));

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(prompt)))
                    .temperature(0.7D)
                    .topP(0.8D)
                    .thinking(false)
                    .build();
            String raw = llmService.chat(request, Tier.FAST);
            return parseQuestions(raw, count);
        } catch (Exception ex) {
            log.warn("生成推荐追问问题失败", ex);
            return List.of();
        }
    }

    /**
     * 拼装 grounding 片段文本 供 prompt 注入；无片段时给出提示语降级为仅依据问答生成
     */
    private String buildChunksText(List<GroundingChunk> chunks) {
        if (CollUtil.isEmpty(chunks)) {
            return "（无检索片段，仅依据问答生成）";
        }
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (GroundingChunk chunk : chunks) {
            sb.append(idx++).append(". 【")
                    .append(StrUtil.nullToEmpty(chunk.getDocName()))
                    .append("】")
                    .append(StrUtil.nullToEmpty(chunk.getText()))
                    .append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /**
     * 健壮解析：去代码围栏 -> JSON 数组 -> trim/去空/去重/截断；任何异常或非数组都视为无结果
     */
    private List<String> parseQuestions(String raw, int count) {
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        String stripped = stripCodeFence(raw).trim();
        if (StrUtil.isBlank(stripped)) {
            return List.of();
        }
        try {
            JSONArray array = JSONUtil.parseArray(stripped);
            LinkedHashSet<String> dedup = new LinkedHashSet<>();
            for (Object item : array) {
                if (item == null) {
                    continue;
                }
                String text = StrUtil.trim(item.toString());
                if (StrUtil.isNotBlank(text)) {
                    dedup.add(text);
                }
            }
            if (dedup.isEmpty()) {
                return List.of();
            }
            List<String> result = new ArrayList<>(dedup);
            return result.size() > count ? result.subList(0, count) : result;
        } catch (Exception ex) {
            log.warn("解析推荐追问问题失败，原文：{}", StrUtil.maxLength(raw, 200));
            return List.of();
        }
    }

    /**
     * 去除可能的 markdown 代码围栏（```json ... ``` 或 ``` ... ```）
     */
    private String stripCodeFence(String raw) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            int lastFence = text.lastIndexOf("```");
            if (lastFence >= 0) {
                text = text.substring(0, lastFence);
            }
        }
        return text;
    }
}
