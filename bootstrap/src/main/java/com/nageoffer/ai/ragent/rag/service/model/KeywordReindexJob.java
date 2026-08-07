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

package com.nageoffer.ai.ragent.rag.service.model;

import com.nageoffer.ai.ragent.rag.controller.vo.KeywordReindexJobVO;
import com.nageoffer.ai.ragent.rag.enums.KeywordReindexJobStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class KeywordReindexJob {

    private final String jobId;
    private final String knowledgeBaseId;
    private final String documentId;
    private final int batchSize;
    private final AtomicInteger totalDocuments = new AtomicInteger();
    private final AtomicInteger successDocuments = new AtomicInteger();
    private final AtomicInteger failedDocuments = new AtomicInteger();
    private final AtomicInteger totalChunks = new AtomicInteger();
    private final AtomicInteger indexedChunks = new AtomicInteger();
    private final List<String> failures = Collections.synchronizedList(new ArrayList<>());
    private volatile KeywordReindexJobStatus status = KeywordReindexJobStatus.PENDING;
    private volatile String errorMessage;

    public KeywordReindexJob(String jobId, String knowledgeBaseId, String documentId, int batchSize) {
        this.jobId = jobId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.documentId = documentId;
        this.batchSize = batchSize;
    }

    public String getJobId() {
        return jobId;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public AtomicInteger getTotalDocuments() {
        return totalDocuments;
    }

    public AtomicInteger getSuccessDocuments() {
        return successDocuments;
    }

    public AtomicInteger getFailedDocuments() {
        return failedDocuments;
    }

    public AtomicInteger getTotalChunks() {
        return totalChunks;
    }

    public AtomicInteger getIndexedChunks() {
        return indexedChunks;
    }

    public KeywordReindexJobStatus getStatus() {
        return status;
    }

    public void setStatus(KeywordReindexJobStatus status) {
        this.status = status;
    }

    public void addFailure(String message) {
        if (message == null) {
            return;
        }
        synchronized (failures) {
            if (failures.size() < 5) {
                failures.add(message);
            }
        }
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public KeywordReindexJobVO toVO() {
        KeywordReindexJobVO vo = new KeywordReindexJobVO();
        vo.setJobId(jobId);
        vo.setStatus(status.name());
        vo.setTotalDocuments(totalDocuments.get());
        vo.setSuccessDocuments(successDocuments.get());
        vo.setFailedDocuments(failedDocuments.get());
        vo.setTotalChunks(totalChunks.get());
        vo.setIndexedChunks(indexedChunks.get());
        String summary = errorMessage;
        synchronized (failures) {
            if (!failures.isEmpty()) {
                String failureSummary = String.join("; ", failures);
                summary = summary == null ? failureSummary : summary + "; " + failureSummary;
            }
        }
        vo.setErrorMessage(summary);
        return vo;
    }
}
