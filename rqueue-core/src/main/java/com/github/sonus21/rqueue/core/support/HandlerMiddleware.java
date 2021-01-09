/*
 *  Copyright 2021 Sonu Kumar
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 *
 */

package com.github.sonus21.rqueue.core.support;

import static com.github.sonus21.rqueue.listener.RqueueMessageHeaders.buildMessageHeaders;

import com.github.sonus21.rqueue.core.Job;
import com.github.sonus21.rqueue.core.RqueueMessage;
import com.github.sonus21.rqueue.listener.RqueueMessageHandler;
import com.github.sonus21.rqueue.models.db.Execution;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public class HandlerMiddleware implements Middleware {

  private final RqueueMessageHandler rqueueMessageHandler;

  public HandlerMiddleware(RqueueMessageHandler rqueueMessageHandler) {
    this.rqueueMessageHandler = rqueueMessageHandler;
  }

  @Override
  public Middleware handle(Job job, Middleware next) {
    Execution execution = job.getLatestExecution();
    RqueueMessage rqueueMessage = job.getRqueueMessage();
    Message<?> message =
        MessageBuilder.createMessage(
            rqueueMessage.getMessage(),
            buildMessageHeaders(job.getQueueDetail().getName(), rqueueMessage, job, execution));
    rqueueMessageHandler.handleMessage(message);
    return next;
  }
}
