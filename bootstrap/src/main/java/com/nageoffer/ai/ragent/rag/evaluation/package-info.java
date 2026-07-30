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

/**
 * RAG 评测工作台包。
 * <p>
 * 与旁路包 {@code com.nageoffer.ai.ragent.rag.eval} 分离：本包承载数据集 / Run / 录制 / 评分 / 报告。
 * API 前缀 {@code /admin/evaluations}；由 {@code app.eval.workbench-enabled} 控制任务资源注册。
 * 阶段 1 仅落持久化与骨架，业务 CRUD 自阶段 2 起实现。
 */
package com.nageoffer.ai.ragent.rag.evaluation;
