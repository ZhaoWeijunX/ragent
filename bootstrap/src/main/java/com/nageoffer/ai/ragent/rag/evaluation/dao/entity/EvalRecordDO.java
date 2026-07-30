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

package com.nageoffer.ai.ragent.rag.evaluation.dao.entity;

import com.nageoffer.ai.ragent.knowledge.dao.handler.JsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 评测录制记录
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "t_eval_record", autoResultMap = true)
public class EvalRecordDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 运行ID
     */
    private String runId;

    /**
     * 样本ID
     */
    private String caseId;

    /**
     * 录制状态
     */
    private String status;

    /**
     * 问题
     */
    private String question;

    /**
     * 回答
     */
    private String response;

    /**
     * 思考内容；MVP默认null
     */
    private String thinking;

    /**
     * 召回文档业务码
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String retrievedDocIds;

    /**
     * 召回chunkId
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String retrievedChunkIds;

    /**
     * 召回上下文
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String retrievedContexts;

    /**
     * 上下文对应文档码
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String retrievedContextDocIds;

    /**
     * 预测意图列表
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String predictedIntents;

    /**
     * Top-1意图
     */
    private String intentPred;

    /**
     * 是否走KB
     */
    private Boolean hasKb;

    /**
     * 是否走MCP
     */
    private Boolean hasMcp;

    /**
     * 是否跳过检索
     */
    private Boolean retrievalSkipped;

    /**
     * 跳过原因
     */
    private String skipReason;

    /**
     * TTFT毫秒
     */
    private Long ttftMs;

    /**
     * Chat总耗时
     */
    private Long totalLatencyMs;

    /**
     * 旁路耗时
     */
    private Long evalLatencyMs;

    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * Trace ID
     */
    private String traceId;

    /**
     * 证据来源
     */
    private String evidenceSource;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 原始载荷
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String rawPayload;

    /**
     * 开始时间
     */
    private Date startedAt;

    /**
     * 结束时间
     */
    private Date finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
