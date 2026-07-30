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

import java.util.Date;

/**
 * 评估集版本
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_eval_dataset_version")
public class EvalDatasetVersionDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 评估集ID
     */
    private String datasetId;

    /**
     * 版本号
     */
    private String version;

    /**
     * DRAFT / PUBLISHED / ARCHIVED
     */
    private String status;

    /**
     * 样本数
     */
    private Integer sampleCount;

    /**
     * 内容哈希
     */
    private String contentHash;

    /**
     * 发布人
     */
    private String publishedBy;

    /**
     * 发布时间
     */
    private Date publishedAt;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
