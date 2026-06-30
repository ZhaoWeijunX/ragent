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

package com.nageoffer.ai.ragent.ingestion.strategy.fetcher;

import com.nageoffer.ai.ragent.ingestion.util.HttpClientHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuWikiClientListNodesTest {

    @Mock
    private HttpClientHelper httpClientHelper;

    @InjectMocks
    private FeishuWikiClient feishuWikiClient;

    @Test
    void shouldParseListNodesResponse() {
        String json = """
                {
                  "code": 0,
                  "data": {
                    "items": [
                      {
                        "node_token": "wikcnA",
                        "title": "页面A",
                        "obj_type": "docx",
                        "obj_token": "doccnA",
                        "space_id": "space1",
                        "parent_node_token": "",
                        "has_child": false,
                        "node_type": "origin"
                      }
                    ],
                    "page_token": "next1",
                    "has_more": true
                  }
                }
                """;
        when(httpClientHelper.get(anyString(), any())).thenReturn(
                new HttpClientHelper.HttpFetchResponse(json.getBytes(StandardCharsets.UTF_8), null, null, null, null, null));

        WikiListNodesResult result = feishuWikiClient.listNodes("space1", null, null, 50, Map.of());

        assertEquals(1, result.items().size());
        assertEquals("wikcnA", result.items().get(0).nodeToken());
        assertEquals("docx", result.items().get(0).objType());
        assertEquals("next1", result.nextPageToken());
        assertTrue(result.hasMore());
    }

    @Test
    void shouldParseEmptyListNodes() {
        String json = """
                {"code":0,"data":{"items":[],"has_more":false}}
                """;
        when(httpClientHelper.get(anyString(), any())).thenReturn(
                new HttpClientHelper.HttpFetchResponse(json.getBytes(StandardCharsets.UTF_8), null, null, null, null, null));

        WikiListNodesResult result = feishuWikiClient.listNodes("space1", "parent", null, 10, Map.of());

        assertTrue(result.items().isEmpty());
        assertFalse(result.hasMore());
    }
}
