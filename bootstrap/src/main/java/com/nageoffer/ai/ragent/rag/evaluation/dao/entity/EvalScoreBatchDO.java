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
 * 评分批次
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "t_eval_score_batch", autoResultMap = true)
public class EvalScoreBatchDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 运行ID
     */
    private String runId;

    /**
     * DETERMINISTIC / RAGAS
     */
    private String scoreType;

    /**
     * 批次状态
     */
    private String status;

    /**
     * 算法版本
     */
    private String algorithmVersion;

    /**
     * Judge配置快照
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String judgeConfigSnapshot;

    /**
     * 样本数
     */
    private Integer sampleCount;

    /**
     * Token用量
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String tokenUsage;

    /**
     * 估算成本
     */
    private BigDecimal estimatedCost;

    /**
     * 外部任务ID
     */
    private String externalJobId;

    /**
     * 开始时间
     */
    private Date startedAt;

    /**
     * 结束时间
     */
    private Date finishedAt;

    /**
     * 错误信息
     */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
