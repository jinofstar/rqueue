/*
 * Copyright 2020 Sonu Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.sonus21.rqueue.core.impl;

import com.github.sonus21.rqueue.common.RqueueRedisTemplate;
import com.github.sonus21.rqueue.config.RqueueConfig;
import com.github.sonus21.rqueue.core.Job;
import com.github.sonus21.rqueue.core.RqueueMessage;
import com.github.sonus21.rqueue.listener.QueueDetail;
import com.github.sonus21.rqueue.models.db.Execution;
import com.github.sonus21.rqueue.models.db.MessageMetadata;
import com.github.sonus21.rqueue.models.db.MessageStatus;
import com.github.sonus21.rqueue.models.db.RqueueJob;
import com.github.sonus21.rqueue.models.enums.ExecutionStatus;
import com.github.sonus21.rqueue.models.enums.JobStatus;
import com.github.sonus21.rqueue.web.service.RqueueMessageMetadataService;
import java.time.Duration;
import org.springframework.data.redis.RedisSystemException;

public class JobImpl implements Job {
  private final QueueDetail queueDetail;
  private final RqueueRedisTemplate<RqueueJob> redisTemplate;
  private final RqueueMessageMetadataService messageMetadataService;
  private final RqueueJob rqueueJob;
  private final RqueueConfig rqueueConfig;
  private final Object userMessage;

  public JobImpl(
      RqueueConfig rqueueConfig,
      RqueueRedisTemplate<RqueueJob> redisTemplate,
      RqueueMessageMetadataService messageMetadataService,
      QueueDetail queueDetail,
      MessageMetadata messageMetadata,
      RqueueMessage rqueueMessage,
      Object userMessage,
      Throwable exception) {
    this.redisTemplate = redisTemplate;
    this.messageMetadataService = messageMetadataService;
    this.rqueueConfig = rqueueConfig;
    this.queueDetail = queueDetail;
    this.userMessage = userMessage;
    this.rqueueJob =
        new RqueueJob(rqueueConfig.getJobId(), rqueueMessage, messageMetadata, exception);
    this.save();
  }

  private void save() {
    try {
      rqueueJob.setUpdatedAt(System.currentTimeMillis());
      redisTemplate.set(
          rqueueJob.getId(), rqueueJob, Duration.ofMillis(2 * queueDetail.getVisibilityTimeout()));
    } catch (RedisSystemException e) {
      // No op
    }
  }

  @Override
  public String getId() {
    return rqueueJob.getId();
  }

  @Override
  public RqueueMessage getRqueueMessage() {
    return rqueueJob.getRqueueMessage();
  }

  @Override
  public void checkIn(Object message) {
    this.rqueueJob.checkIn(message);
    this.save();
  }

  @Override
  public Object getMessage() {
    return userMessage;
  }

  @Override
  public MessageMetadata getMessageMetadata() {
    return rqueueJob.getMessageMetadata();
  }

  @Override
  public JobStatus getStatus() {
    return rqueueJob.getStatus();
  }

  @Override
  public Throwable getException() {
    return rqueueJob.getError();
  }

  @Override
  public long getExecutionTime() {
    long executionTime = 0;
    for (Execution execution : rqueueJob.getExecutions()) {
      executionTime += (execution.getEndTime() - execution.getStartTime());
    }
    return executionTime;
  }

  @Override
  public QueueDetail getQueueDetail() {
    return queueDetail;
  }

  public void setException(Throwable e) {
    this.rqueueJob.setError(e);
    save();
  }

  public void setMessageMetadata(MessageMetadata m) {
    this.rqueueJob.setMessageMetadata(m);
    this.save();
  }

  private void setMessageStatus(MessageStatus messageStatus) {
    rqueueJob.setStatus(messageStatus.getJobStatus());
    rqueueJob.getMessageMetadata().setStatus(messageStatus);
  }

  public void updateMessageStatus(MessageStatus messageStatus) {
    Duration expiry;
    if (messageStatus.isTerminalState()) {
      expiry = Duration.ofSeconds(rqueueConfig.getMessageDurabilityInTerminalStateInSecond());
    } else {
      expiry = Duration.ofMinutes(rqueueConfig.getMessageDurabilityInMinute());
    }
    setMessageStatus(messageStatus);
    this.messageMetadataService.save(rqueueJob.getMessageMetadata(), expiry);
    save();
  }

  public void execute() {
    rqueueJob.startNewExecution();
    save();
  }

  public void updateExecutionStatus(ExecutionStatus status, Throwable e) {
    rqueueJob.updateExecutionStatus(status, e);
    save();
  }

  public void updateExecutionTime(RqueueMessage rqueueMessage, MessageStatus messageStatus) {
    long executionTime = getExecutionTime();
    rqueueJob.getMessageMetadata().setRqueueMessage(rqueueMessage);
    if (getRqueueMessage().isPeriodicTask()) {
      this.rqueueJob.getMessageMetadata().setTotalExecutionTime(executionTime);
    } else {
      this.rqueueJob
          .getMessageMetadata()
          .setTotalExecutionTime(
              executionTime + rqueueJob.getMessageMetadata().getTotalExecutionTime());
    }
    this.updateMessageStatus(messageStatus);
  }
}
