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

package com.nageoffer.ai.ragent.rag.core.intent;

import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class IntentResolverTest {

    private final IntentResolver resolver = new IntentResolver(
            mock(IntentClassifier.class),
            Runnable::run
    );

    @Test
    void allSubQuestionsMustBeSystemOnly() {
        assertTrue(resolver.areAllSystemOnly(List.of(
                subIntent("问候", IntentKind.SYSTEM),
                subIntent("投诉", IntentKind.SYSTEM)
        )));
        assertFalse(resolver.areAllSystemOnly(List.of(
                subIntent("问候", IntentKind.SYSTEM),
                subIntent("推荐商品", IntentKind.KB)
        )));
        assertFalse(resolver.areAllSystemOnly(List.of()));
    }

    private SubQuestionIntent subIntent(String question, IntentKind kind) {
        IntentNode node = IntentNode.builder()
                .id(question)
                .kind(kind)
                .build();
        NodeScore score = NodeScore.builder()
                .node(node)
                .score(1D)
                .build();
        return new SubQuestionIntent(question, List.of(score));
    }
}
