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

package com.nageoffer.ai.ragent.knowledge.mq;

import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.mq.MessageWrapper;
import com.nageoffer.ai.ragent.knowledge.mq.event.FeishuWikiImportEvent;
import com.nageoffer.ai.ragent.knowledge.service.FeishuWikiImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 飞书 Wiki 批量导入 MQ 消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "feishu-wiki-import_topic${unique-name:}",
        consumerGroup = "feishu-wiki-import_cg${unique-name:}"
)
public class FeishuWikiImportConsumer implements RocketMQListener<MessageWrapper<FeishuWikiImportEvent>> {

    private final FeishuWikiImportService importService;

    @Override
    public void onMessage(MessageWrapper<FeishuWikiImportEvent> message) {
        FeishuWikiImportEvent event = message.getBody();
        log.info("[消费者] 飞书 Wiki 批量导入, jobId={}, keys={}", event.getJobId(), message.getKeys());

        UserContext.set(LoginUser.builder().username(event.getOperator()).build());
        try {
            importService.processNextItem(event.getJobId());
        } finally {
            UserContext.clear();
        }
    }
}
