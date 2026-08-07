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
 * 评测运行
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "t_eval_run", autoResultMap = true)
public class EvalRunDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 运行名称
     */
    private String name;

    /**
     * 数据集版本ID
     */
    private String datasetVersionId;

    /**
     * 基线Run ID
     */
    private String baselineRunId;

    /**
     * 运行状态
     */
    private String status;

    /**
     * 当前阶段
     */
    private String currentPhase;

    /**
     * 是否请求取消
     */
    private Integer cancelRequested;

    /**
     * 配置快照
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String configSnapshot;

    /**
     * 标签快照
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String tags;

    /**
     * 总样本数
     */
    private Integer totalCount;

    /**
     * 成功数
     */
    private Integer successCount;

    /**
     * 失败数
     */
    private Integer failedCount;

    /**
     * 进度0-100
     */
    private Integer progress;

    /**
     * 是否启用RAGAS
     */
    private Boolean ragasEnabled;

    /**
     * 租约持有者
     */
    private String leaseOwner;

    /**
     * 租约过期时间
     */
    private Date leaseExpireAt;

    /**
     * 创建人
     */
    private String createdBy;

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
