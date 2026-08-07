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

import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeChunkDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.nageoffer.ai.ragent.rag.controller.request.KeywordReindexRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.KeywordReindexJobVO;
import com.nageoffer.ai.ragent.rag.core.keyword.KeywordIndexService;
import com.nageoffer.ai.ragent.rag.core.keyword.model.KeywordIndexDocument;
import com.nageoffer.ai.ragent.rag.enums.KeywordReindexJobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeywordReindexServiceImplTest {

    @Mock
    private KeywordIndexService keywordIndexService;

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Mock
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Test
    void shouldDeleteAndReindexPersistedChunksInBatches() {
        KeywordReindexServiceImpl service = newService(command -> command.run());
        KnowledgeBaseDO knowledgeBase = KnowledgeBaseDO.builder()
                .id("kb-1")
                .collectionName("collection-1")
                .build();
        KnowledgeDocumentDO document = KnowledgeDocumentDO.builder()
                .id("doc-1")
                .kbId("kb-1")
                .enabled(1)
                .status("success")
                .build();
        List<KnowledgeChunkDO> chunks = List.of(
                KnowledgeChunkDO.builder().id("chunk-1").docId("doc-1").chunkIndex(0).content("first").enabled(1).build(),
                KnowledgeChunkDO.builder().id("chunk-2").docId("doc-1").chunkIndex(1).content("second").enabled(1).build());

        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(knowledgeBase);
        when(knowledgeDocumentMapper.selectById("doc-1")).thenReturn(document);
        when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of(document));
        when(knowledgeChunkMapper.selectList(any())).thenReturn(chunks);

        KeywordReindexRequest request = new KeywordReindexRequest();
        request.setKnowledgeBaseId("kb-1");
        request.setDocumentId("doc-1");
        request.setBatchSize(1);

        KeywordReindexJobVO result = service.get(service.create(request).getJobId());

        assertEquals(KeywordReindexJobStatus.SUCCESS.name(), result.getStatus());
        assertEquals(1, result.getTotalDocuments());
        assertEquals(1, result.getSuccessDocuments());
        assertEquals(0, result.getFailedDocuments());
        assertEquals(2, result.getTotalChunks());
        assertEquals(2, result.getIndexedChunks());
        verify(keywordIndexService).deleteDocumentIndex("collection-1", "doc-1");

        ArgumentCaptor<List<KeywordIndexDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(keywordIndexService, org.mockito.Mockito.times(2)).indexRawChunks(captor.capture());
        assertEquals("chunk-1", captor.getAllValues().get(0).get(0).chunkId());
        assertEquals("collection-1", captor.getAllValues().get(1).get(0).collectionName());
        assertEquals("doc-1", captor.getAllValues().get(1).get(0).docId());
    }

    @Test
    void shouldExposeFailedDocumentWithoutBlockingOtherJobs() {
        KeywordReindexServiceImpl service = newService(command -> command.run());
        KnowledgeBaseDO knowledgeBase = KnowledgeBaseDO.builder()
                .id("kb-1")
                .collectionName("collection-1")
                .build();
        KnowledgeDocumentDO document = KnowledgeDocumentDO.builder()
                .id("doc-1")
                .kbId("kb-1")
                .enabled(1)
                .status("success")
                .build();
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(knowledgeBase);
        when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of(document));
        when(knowledgeChunkMapper.selectList(any())).thenReturn(List.of(
                KnowledgeChunkDO.builder().id("chunk-1").docId("doc-1").chunkIndex(0).content("first").enabled(1).build()));
        doThrow(new RuntimeException("es unavailable"))
                .when(keywordIndexService).indexRawChunks(any());

        KeywordReindexRequest request = new KeywordReindexRequest();
        request.setKnowledgeBaseId("kb-1");

        KeywordReindexJobVO result = service.get(service.create(request).getJobId());

        assertEquals(KeywordReindexJobStatus.FAILED.name(), result.getStatus());
        assertEquals(1, result.getFailedDocuments());
        assertEquals(0, result.getIndexedChunks());
        assertTrue(result.getErrorMessage().contains("doc-1"));
    }

    private KeywordReindexServiceImpl newService(Executor executor) {
        return new KeywordReindexServiceImpl(
                keywordIndexService,
                knowledgeBaseMapper,
                knowledgeDocumentMapper,
                knowledgeChunkMapper,
                executor);
    }
}
