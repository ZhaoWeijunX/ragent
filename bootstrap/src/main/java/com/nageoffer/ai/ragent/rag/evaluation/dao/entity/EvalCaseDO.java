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
 * 评估样本
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "t_eval_case", autoResultMap = true)
public class EvalCaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 版本ID
     */
    private String datasetVersionId;

    /**
     * 样本业务ID
     */
    private String queryId;

    /**
     * 问题
     */
    private String query;

    /**
     * 一级意图
     */
    private String intentL1;

    /**
     * 二级意图
     */
    private String intentL2;

    /**
     * easy/medium/hard
     */
    private String difficulty;

    /**
     * 是否应走RAG
     */
    private Boolean requiresRag;

    /**
     * 期望答案类型
     */
    private String expectedAnswerType;

    /**
     * must文档业务码JSON数组
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String expectedDocIds;

    /**
     * nice文档业务码JSON数组
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String niceToHaveDocIds;

    /**
     * 标准答案
     */
    private String groundTruth;

    /**
     * 陷阱类型
     */
    private String trapType;

    /**
     * 启用指标JSON数组
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String enabledMetrics;

    /**
     * 标签JSON数组
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String tags;

    /**
     * 扩展元数据JSON
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metadata;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
