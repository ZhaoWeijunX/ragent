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

import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalConfigSnapshotSupportTest {

    @Test
    void sanitizeCandidatesOmitsUrlAndKeepsIdentity() {
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId("qwen-emb");
        candidate.setProvider("bailian");
        candidate.setModel("text-embedding-v3");
        candidate.setUrl("https://secret.example/v1");
        candidate.setDimension(1536);
        candidate.setPriority(1);
        candidate.setEnabled(true);
        candidate.setSupportsThinking(false);

        List<Map<String, Object>> rows = EvalConfigSnapshotSupport.sanitizeCandidates(List.of(candidate));
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("qwen-emb", row.get("id"));
        assertEquals("bailian", row.get("provider"));
        assertEquals("text-embedding-v3", row.get("model"));
        assertEquals(1536, row.get("dimension"));
        assertFalse(row.containsKey("url"));
        assertFalse(row.containsKey("apiKey"));
    }

    @Test
    void hashClasspathPromptsIsStable() {
        String a = EvalConfigSnapshotSupport.hashClasspathPrompts(List.of(
                "prompt/answer-chat-kb.st",
                "prompt/answer-chat-system.st"
        ));
        String b = EvalConfigSnapshotSupport.hashClasspathPrompts(List.of(
                "prompt/answer-chat-system.st",
                "prompt/answer-chat-kb.st"
        ));
        assertEquals(a, b);
        assertTrue(a.matches("[0-9a-f]{64}"));
    }
}
