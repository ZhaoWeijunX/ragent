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

package com.nageoffer.ai.ragent.rag.eval;

import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.IntentResolver;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrievalEngine;
import com.nageoffer.ai.ragent.rag.core.rewrite.QueryRewriteService;
import com.nageoffer.ai.ragent.rag.core.rewrite.RewriteResult;
import com.nageoffer.ai.ragent.rag.dto.RetrievalContext;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalControllerTest {

    @Mock
    private QueryRewriteService queryRewriteService;
    @Mock
    private IntentResolver intentResolver;
    @Mock
    private RetrievalEngine retrievalEngine;
    @Mock
    private KnowledgeChunkMapper knowledgeChunkMapper;
    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    private EvalController controller;

    @BeforeEach
    void setUp() {
        controller = new EvalController(
                queryRewriteService,
                intentResolver,
                retrievalEngine,
                knowledgeChunkMapper,
                knowledgeDocumentMapper
        );
    }

    @Test
    void systemOnlyIntentSkipsRetrieval() {
        String question = "我有一个功能建议";
        RewriteResult rewriteResult = new RewriteResult(question, List.of(question));
        List<SubQuestionIntent> subIntents = List.of(intent(question, "F2_功能建议", IntentKind.SYSTEM));
        when(queryRewriteService.rewriteWithSplit(question, List.of())).thenReturn(rewriteResult);
        when(intentResolver.resolve(rewriteResult)).thenReturn(subIntents);
        when(intentResolver.areAllSystemOnly(subIntents)).thenReturn(true);

        Result<EvalResponse> result = controller.chat(question);

        EvalResponse response = result.getData();
        assertTrue(response.isRetrievalSkipped());
        assertEquals("SYSTEM_ONLY", response.getSkipReason());
        assertFalse(response.isHasKb());
        assertFalse(response.isHasMcp());
        assertTrue(response.getRetrievedDocIds().isEmpty());
        assertEquals(List.of("F2_功能建议"), response.getIntentLeafIds());
        verifyNoInteractions(retrievalEngine, knowledgeChunkMapper, knowledgeDocumentMapper);
    }

    @Test
    void nonSystemIntentStillRunsRetrieval() {
        String question = "推荐一款扫地机器人";
        RewriteResult rewriteResult = new RewriteResult(question, List.of(question));
        List<SubQuestionIntent> subIntents = List.of(intent(question, "S1_选购推荐", IntentKind.KB));
        RetrievalContext retrievalContext = RetrievalContext.builder()
                .intentChunks(Map.of())
                .build();
        when(queryRewriteService.rewriteWithSplit(question, List.of())).thenReturn(rewriteResult);
        when(intentResolver.resolve(rewriteResult)).thenReturn(subIntents);
        when(intentResolver.areAllSystemOnly(subIntents)).thenReturn(false);
        when(retrievalEngine.retrieve(subIntents)).thenReturn(retrievalContext);

        Result<EvalResponse> result = controller.chat(question);

        assertFalse(result.getData().isRetrievalSkipped());
        assertNull(result.getData().getSkipReason());
        verify(retrievalEngine).retrieve(subIntents);
    }

    private SubQuestionIntent intent(String question, String id, IntentKind kind) {
        IntentNode node = IntentNode.builder()
                .id(id)
                .kind(kind)
                .build();
        return new SubQuestionIntent(
                question,
                List.of(NodeScore.builder().node(node).score(1D).build())
        );
    }
}
