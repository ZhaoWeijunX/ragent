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
import java.math.BigDecimal;

import java.util.Date;

/**
 * 指标分数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "t_eval_score", autoResultMap = true)
public class EvalScoreDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 批次ID
     */
    private String scoreBatchId;

    /**
     * 运行ID
     */
    private String runId;

    /**
     * 录制ID；聚合可空
     */
    private String recordId;

    /**
     * 指标名
     */
    private String metricName;

    /**
     * 维度类型
     */
    private String dimensionType;

    /**
     * 维度值
     */
    private String dimensionValue;

    /**
     * 分值
     */
    private BigDecimal scoreValue;

    /**
     * 计入样本数
     */
    private Integer sampleCount;

    /**
     * 明细
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
