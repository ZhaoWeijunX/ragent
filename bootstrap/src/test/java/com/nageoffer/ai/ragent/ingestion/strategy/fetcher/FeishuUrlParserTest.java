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

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeishuUrlParserTest {

    @Test
    void shouldParseDocxUrl() {
        FeishuUrlParser.ParseResult result = FeishuUrlParser.parse(
                "https://example.feishu.cn/docx/doccnABCDEF?from=share");
        assertEquals(FeishuUrlParser.LinkType.DOCX, result.linkType());
        assertEquals("doccnABCDEF", result.token());
    }

    @Test
    void shouldParseLegacyDocsUrl() {
        FeishuUrlParser.ParseResult result = FeishuUrlParser.parse(
                "https://example.feishu.cn/docs/doccnLegacy");
        assertEquals(FeishuUrlParser.LinkType.DOCX, result.linkType());
        assertEquals("doccnLegacy", result.token());
    }

    @Test
    void shouldParseWikiUrl() {
        FeishuUrlParser.ParseResult result = FeishuUrlParser.parse(
                "https://hcnuu3eqvd8k.feishu.cn/wiki/wikcnXYZ123?from=space");
        assertEquals(FeishuUrlParser.LinkType.WIKI, result.linkType());
        assertEquals("wikcnXYZ123", result.token());
        assertEquals(null, result.wikiSpaceId());
    }

    @Test
    void shouldParseWikiSpaceNodeUrl() {
        FeishuUrlParser.ParseResult result = FeishuUrlParser.parse(
                "https://example.feishu.cn/wiki/space/space123/nodes/EpMmw5WZQi7tYRk73gBc7Dabcef");
        assertEquals(FeishuUrlParser.LinkType.WIKI, result.linkType());
        assertEquals("EpMmw5WZQi7tYRk73gBc7Dabcef", result.token());
        assertEquals("space123", result.wikiSpaceId());
    }

    @Test
    void shouldParseWikiSpaceHomeUrl() {
        FeishuUrlParser.ParseResult result = FeishuUrlParser.parse(
                "https://example.feishu.cn/wiki/space/space123");
        assertEquals(FeishuUrlParser.LinkType.WIKI, result.linkType());
        assertEquals(null, result.token());
        assertEquals("space123", result.wikiSpaceId());
    }

    @Test
    void shouldParseWikiSettingsUrl() {
        FeishuUrlParser.ParseResult result = FeishuUrlParser.parse(
                "https://example.feishu.cn/wiki/settings/space456");
        assertEquals(FeishuUrlParser.LinkType.WIKI, result.linkType());
        assertEquals(null, result.token());
        assertEquals("space456", result.wikiSpaceId());
    }

    @Test
    void shouldNotTreatSpaceSegmentAsWikiToken() {
        FeishuUrlParser.ParseResult result = FeishuUrlParser.parse(
                "https://example.feishu.cn/wiki/space/space123");
        assertEquals(null, result.token());
    }

    @Test
    void shouldRejectWikiHomeWithoutToken() {
        assertThrows(ClientException.class, () -> FeishuUrlParser.parse(
                "https://hcnuu3eqvd8k.feishu.cn/wiki/"));
    }

    @Test
    void shouldMarkUnknownFeishuUrlAsUnsupported() {
        FeishuUrlParser.ParseResult result = FeishuUrlParser.parse(
                "https://example.feishu.cn/drive/folder/abc");
        assertEquals(FeishuUrlParser.LinkType.UNSUPPORTED, result.linkType());
    }

    @Test
    void shouldDetectFeishuHost() {
        assertEquals(true, FeishuUrlParser.isFeishuHost("https://example.feishu.cn/docx/doccnABC"));
        assertEquals(true, FeishuUrlParser.isFeishuHost("https://foo.larksuite.com/wiki/wikcnXYZ"));
        assertEquals(false, FeishuUrlParser.isFeishuHost("https://github.com/foo/bar"));
    }

    @Test
    void shouldDetectSupportedDocumentUrl() {
        assertEquals(true, FeishuUrlParser.isSupportedDocumentUrl(
                "https://example.feishu.cn/docx/doccnABC"));
        assertEquals(true, FeishuUrlParser.isSupportedDocumentUrl(
                "https://example.feishu.cn/wiki/wikcnXYZ"));
        assertEquals(true, FeishuUrlParser.isSupportedDocumentUrl(
                "https://example.feishu.cn/wiki/space/space123"));
        assertEquals(false, FeishuUrlParser.isSupportedDocumentUrl(
                "https://example.feishu.cn/drive/folder/abc"));
        assertEquals(false, FeishuUrlParser.isSupportedDocumentUrl(
                "https://example.feishu.cn/wiki/"));
    }

    @Test
    void shouldBuildWikiUrl() {
        assertEquals("https://example.feishu.cn/wiki/wikcnABC",
                FeishuUrlParser.buildWikiUrl("example.feishu.cn", "wikcnABC"));
    }
}
