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
 * 人工覆盖
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_eval_manual_override")
public class EvalManualOverrideDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 运行ID
     */
    private String runId;

    /**
     * 录制ID
     */
    private String recordId;

    /**
     * 指标名
     */
    private String metricName;

    /**
     * 自动分
     */
    private BigDecimal automaticScore;

    /**
     * 人工分
     */
    private BigDecimal manualScore;

    /**
     * 理由
     */
    private String reason;

    /**
     * ACTIVE / REVOKED
     */
    private String status;

    /**
     * 操作人
     */
    private String operatorId;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
