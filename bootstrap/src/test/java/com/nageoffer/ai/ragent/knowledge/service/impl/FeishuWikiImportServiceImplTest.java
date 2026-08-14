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

package com.nageoffer.ai.ragent.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nageoffer.ai.ragent.audit.support.BizChangeLogContext;
import com.nageoffer.ai.ragent.framework.mq.producer.MessageQueueProducer;
import com.nageoffer.ai.ragent.ingestion.strategy.fetcher.FeishuAuthService;
import com.nageoffer.ai.ragent.knowledge.config.FeishuCredentialsProvider;
import com.nageoffer.ai.ragent.knowledge.controller.vo.KnowledgeDocumentVO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.FeishuWikiImportItemDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.FeishuWikiImportJobDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.FeishuWikiImportItemMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.FeishuWikiImportJobMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.knowledge.feishu.FeishuWikiImportItemStatus;
import com.nageoffer.ai.ragent.knowledge.feishu.FeishuWikiImportJobStatus;
import com.nageoffer.ai.ragent.knowledge.feishu.FeishuWikiTreeWalker;
import com.nageoffer.ai.ragent.knowledge.service.FeishuWikiPageImportResult;
import com.nageoffer.ai.ragent.knowledge.service.KnowledgeDocumentService;
import com.nageoffer.ai.ragent.knowledge.support.IngestionSpecCodec;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuWikiImportServiceImplTest {

    private static final String JOB_ID = "job-1";
    private static final String ITEM_ID = "item-1";

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock
    private FeishuWikiImportJobMapper jobMapper;
    @Mock
    private FeishuWikiImportItemMapper itemMapper;
    @Mock
    private FeishuCredentialsProvider feishuCredentialsProvider;
    @Mock
    private FeishuAuthService feishuAuthService;
    @Mock
    private FeishuWikiTreeWalker treeWalker;
    @Mock
    private KnowledgeDocumentService documentService;
    @Mock
    private IngestionSpecCodec ingestionSpecCodec;
    @Mock
    private MessageQueueProducer messageQueueProducer;
    @Mock
    private BizChangeLogContext bizChangeLogContext;

    private FeishuWikiImportServiceImpl importService;

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, FeishuWikiImportItemDO.class);
        TableInfoHelper.initTableInfo(assistant, FeishuWikiImportJobDO.class);
    }

    @BeforeEach
    void setUp() {
        importService = new FeishuWikiImportServiceImpl(
                knowledgeBaseMapper,
                jobMapper,
                itemMapper,
                feishuCredentialsProvider,
                feishuAuthService,
                treeWalker,
                documentService,
                ingestionSpecCodec,
                messageQueueProducer,
                bizChangeLogContext
        );
        ReflectionTestUtils.setField(importService, "importTopic", "feishu-wiki-import_topic");
    }

    @Test
    void shouldStopWhenPendingItemClaimFails() {
        when(jobMapper.selectById(JOB_ID)).thenReturn(importingJob());
        when(itemMapper.selectOne(any())).thenReturn(pendingItem());
        when(itemMapper.update(isNull(), any())).thenReturn(0);

        importService.processNextItem(JOB_ID);

        verify(itemMapper).update(isNull(), any());
        verifyNoInteractions(documentService, messageQueueProducer);
    }

    @Test
    void shouldNotCountSuccessWhenImportingItemCompletionFails() {
        KnowledgeDocumentVO document = new KnowledgeDocumentVO();
        document.setId("doc-1");
        when(jobMapper.selectById(JOB_ID)).thenReturn(importingJob());
        when(itemMapper.selectOne(any())).thenReturn(pendingItem());
        when(itemMapper.update(isNull(), any())).thenReturn(1, 0);
        when(documentService.importFeishuWikiPage(any(), any(), any(), any()))
                .thenReturn(new FeishuWikiPageImportResult(document, false));

        importService.processNextItem(JOB_ID);

        verify(itemMapper, times(2)).update(isNull(), any());
        verify(jobMapper, never()).update(isNull(), any());
        verify(messageQueueProducer).send(any(), any(), any(), any());
    }

    @Test
    void shouldNotFinalizeWhenImportingItemExists() {
        when(jobMapper.selectById(JOB_ID)).thenReturn(importingJob());
        when(itemMapper.selectOne(any())).thenReturn(null);
        when(itemMapper.selectCount(any())).thenReturn(1L);

        importService.processNextItem(JOB_ID);

        verify(jobMapper, never()).update(isNull(), any());
        verifyNoInteractions(messageQueueProducer);
    }

    @Test
    void shouldFinalizeWhenNoActiveItemExists() {
        when(jobMapper.selectById(JOB_ID)).thenReturn(importingJob());
        when(itemMapper.selectOne(any())).thenReturn(null);
        when(itemMapper.selectCount(any())).thenReturn(0L);

        importService.processNextItem(JOB_ID);

        verify(jobMapper, times(2)).selectById(JOB_ID);
        verify(jobMapper).update(isNull(), any());
        verifyNoInteractions(messageQueueProducer);
    }

    private FeishuWikiImportJobDO importingJob() {
        return FeishuWikiImportJobDO.builder()
                .id(JOB_ID)
                .kbId("kb-1")
                .status(FeishuWikiImportJobStatus.IMPORTING.getCode())
                .successCount(0)
                .failedCount(0)
                .autoChunk(0)
                .createdBy("tester")
                .build();
    }

    private FeishuWikiImportItemDO pendingItem() {
        return FeishuWikiImportItemDO.builder()
                .id(ITEM_ID)
                .jobId(JOB_ID)
                .nodeToken("node-1")
                .wikiUrl("https://example.feishu.cn/wiki/node-1")
                .status(FeishuWikiImportItemStatus.PENDING.getCode())
                .sortOrder(0)
                .build();
    }
}
