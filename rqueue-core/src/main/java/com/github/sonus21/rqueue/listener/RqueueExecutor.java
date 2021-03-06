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

package com.github.sonus21.rqueue.listener;

import static com.github.sonus21.rqueue.listener.RqueueMessageHeaders.buildMessageHeaders;
import static com.github.sonus21.rqueue.utils.Constants.DELTA_BETWEEN_RE_ENQUEUE_TIME;
import static com.github.sonus21.rqueue.utils.Constants.ONE_MILLI;
import static com.github.sonus21.rqueue.utils.Constants.REDIS_KEY_SEPARATOR;

import com.github.sonus21.rqueue.config.RqueueConfig;
import com.github.sonus21.rqueue.core.Job;
import com.github.sonus21.rqueue.core.RqueueMessage;
import com.github.sonus21.rqueue.core.middleware.HandlerMiddleware;
import com.github.sonus21.rqueue.core.middleware.Middleware;
import com.github.sonus21.rqueue.core.support.RqueueMessageUtils;
import com.github.sonus21.rqueue.metrics.RqueueMetricsCounter;
import com.github.sonus21.rqueue.models.db.MessageMetadata;
import com.github.sonus21.rqueue.models.enums.ExecutionStatus;
import com.github.sonus21.rqueue.models.enums.MessageStatus;
import com.github.sonus21.rqueue.web.service.RqueueMessageMetadataService;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.MessageBuilder;

@Slf4j
class RqueueExecutor extends MessageContainerBase {
  private final RqueueMessageHandler rqueueMessageHandler;
  private final RqueueMessageMetadataService rqueueMessageMetadataService;
  private final PostProcessingHandler postProcessingHandler;
  private final Semaphore semaphore;
  private final RqueueConfig rqueueConfig;
  private boolean updatedToProcessing;
  private JobImpl job;
  private ExecutionStatus status;
  private Throwable error;
  private int failureCount;

  RqueueExecutor(
      WeakReference<RqueueMessageListenerContainer> container,
      RqueueConfig rqueueConfig,
      PostProcessingHandler postProcessingHandler,
      RqueueMessage rqueueMessage,
      QueueDetail queueDetail,
      Semaphore semaphore) {
    super(log, queueDetail.getName(), container);
    this.rqueueConfig = rqueueConfig;
    this.postProcessingHandler = postProcessingHandler;
    this.rqueueMessageMetadataService =
        Objects.requireNonNull(container.get()).rqueueMessageMetadataService();
    this.rqueueMessageHandler = Objects.requireNonNull(container.get()).getRqueueMessageHandler();
    this.semaphore = semaphore;
    init(rqueueMessage, queueDetail);
  }

  private void init(RqueueMessage rqueueMessage, QueueDetail queueDetail) {
    Message<String> tmpMessage =
        MessageBuilder.createMessage(
            rqueueMessage.getMessage(),
            buildMessageHeaders(queueDetail.getName(), rqueueMessage, null, null));
    MessageMetadata messageMetadata =
        rqueueMessageMetadataService.getOrCreateMessageMetadata(rqueueMessage);
    Throwable t = null;
    Object userMessage = null;
    try {
      userMessage =
          RqueueMessageUtils.convertMessageToObject(
              tmpMessage, rqueueMessageHandler.getMessageConverter());
    } catch (Exception e) {
      log(Level.ERROR, "Unable to convert message {}", e, rqueueMessage.getMessage());
      t = e;
      throw e;
    } finally {
      this.job =
          new JobImpl(
              rqueueConfig,
              Objects.requireNonNull(container.get()).rqueueMessageMetadataService(),
              Objects.requireNonNull(container.get()).rqueueJobDao(),
              queueDetail,
              messageMetadata,
              rqueueMessage,
              userMessage,
              t,
              postProcessingHandler);
    }
    this.failureCount = job.getRqueueMessage().getFailureCount();
  }

  private int getMaxRetryCount() {
    return job.getRqueueMessage().getRetryCount() == null
        ? job.getQueueDetail().getNumRetry()
        : job.getRqueueMessage().getRetryCount();
  }

  private void updateCounter(boolean fail) {
    RqueueMetricsCounter counter =
        Objects.requireNonNull(container.get()).getRqueueMetricsCounter();
    if (counter == null) {
      return;
    }
    if (fail) {
      counter.updateFailureCount(job.getQueueDetail().getName());
    } else {
      counter.updateExecutionCount(job.getQueueDetail().getName());
    }
  }

  private long maxExecutionTime() {
    return job.getQueueDetail().getVisibilityTimeout() - DELTA_BETWEEN_RE_ENQUEUE_TIME;
  }

  private long getMaxProcessingTime() {
    return System.currentTimeMillis() + maxExecutionTime();
  }

  private boolean isMessageDeleted() {
    if (job.getMessageMetadata().isDeleted()) {
      return true;
    }
    MessageMetadata newMessageMetadata =
        rqueueMessageMetadataService.getOrCreateMessageMetadata(job.getRqueueMessage());
    if (!newMessageMetadata.equals(job.getMessageMetadata())) {
      // TODO what happens to the current execution data
      job.setMessageMetadata(newMessageMetadata);
    }
    return job.getMessageMetadata().isDeleted();
  }

  private boolean shouldIgnore() {
    return !Objects.requireNonNull(container.get()).getPreExecutionMessageProcessor().process(job);
  }

  private boolean isOldMessage() {
    return job.getMessageMetadata().getRqueueMessage() != null
        && job.getMessageMetadata().getRqueueMessage().getQueuedTime()
            != job.getRqueueMessage().getQueuedTime();
  }

  private int getRetryCount() {
    int maxRetry = getMaxRetryCount();
    if (rqueueConfig.getRetryPerPoll() == -1) {
      return maxRetry;
    }
    return Math.min(rqueueConfig.getRetryPerPoll(), maxRetry);
  }

  private boolean queueInActive() {
    return !isQueueActive(job.getQueueDetail().getName());
  }

  private ExecutionStatus getStatus() {
    if (queueInActive()) {
      return ExecutionStatus.QUEUE_INACTIVE;
    }
    if (shouldIgnore()) {
      return ExecutionStatus.IGNORED;
    }
    if (isMessageDeleted()) {
      return ExecutionStatus.DELETED;
    }
    if (isOldMessage()) {
      return ExecutionStatus.OLD_MESSAGE;
    }
    return null;
  }

  private void updateToProcessing() {
    if (updatedToProcessing) {
      return;
    }
    this.updatedToProcessing = true;
    this.job.updateMessageStatus(MessageStatus.PROCESSING);
  }

  private void logExecutionTimeWarning(
      long maxProcessingTime, long startTime, ExecutionStatus status) {
    if (System.currentTimeMillis() > maxProcessingTime) {
      long maxAllowedTime = maxExecutionTime();
      long executionTime = System.currentTimeMillis() - startTime;
      log(
          Level.WARN,
          "Message listener is taking longer time [Queue: {}, TaskStatus: {}] MaxAllowedTime: {}, ExecutionTime: {}",
          null,
          job.getQueueDetail().getName(),
          status,
          maxAllowedTime,
          executionTime);
    }
  }

  private void begin() {
    job.execute();
    this.error = null;
    this.status = getStatus();
  }

  private void end() {
    if (status == null) {
      job.updateExecutionStatus(ExecutionStatus.FAILED, error);
    } else {
      job.updateExecutionStatus(status, error);
    }
  }

  private void callMiddlewares(int currentIndex, List<Middleware> middlewares, Job job)
      throws Exception {
    if (currentIndex == middlewares.size()) {
      new HandlerMiddleware(rqueueMessageHandler).handle(job, null);
    } else {
      middlewares
          .get(currentIndex)
          .handle(
              job,
              () -> {
                callMiddlewares(currentIndex + 1, middlewares, job);
                return null;
              });
    }
  }

  private void processMessage() throws Exception {
    List<Middleware> middlewareList = Objects.requireNonNull(container.get()).getMiddleWares();
    if (middlewareList == null) {
      callMiddlewares(0, Collections.emptyList(), job);
    } else {
      callMiddlewares(0, middlewareList, job);
    }
    status = ExecutionStatus.SUCCESSFUL;
  }

  private void execute() {
    try {
      updateToProcessing();
      updateCounter(false);
      processMessage();
    } catch (MessagingException e) {
      updateCounter(true);
      failureCount += 1;
      error = e;
    } catch (Exception e) {
      updateCounter(true);
      failureCount += 1;
      error = e;
      log(Level.ERROR, "Message execution failed, RqueueMessage: {}", e, job.getRqueueMessage());
    }
  }

  private void handleMessage() {
    long maxProcessingTime = getMaxProcessingTime();
    long startTime = System.currentTimeMillis();
    int retryCount = getRetryCount();
    int attempt = 1;
    try {
      do {
        log(Level.DEBUG, "Attempt {} message: {}", null, attempt, job.getMessage());
        begin();
        if (status == null) {
          execute();
        }
        retryCount -= 1;
        attempt += 1;
        end();
      } while (retryCount > 0 && status == null && System.currentTimeMillis() < maxProcessingTime);
      postProcessingHandler.handle(
          job, (status == null ? ExecutionStatus.FAILED : status), failureCount);
      logExecutionTimeWarning(maxProcessingTime, startTime, status);
    } finally {
      semaphore.release();
    }
  }

  private long getTtlForScheduledMessageKey(RqueueMessage message) {
    // Assume a message can be executing for at most 2x of their visibility timeout
    // due to failure in some other job same message should not be enqueued
    long expiryInSeconds = 2 * job.getQueueDetail().getVisibilityTimeout() / ONE_MILLI;
    // A message wil be processed after period, so it must stay in the system till that time
    // how many more seconds are left to process this message
    long remainingTime = (message.getProcessAt() - System.currentTimeMillis()) / ONE_MILLI;
    if (remainingTime > 0) {
      expiryInSeconds += remainingTime;
    }
    return expiryInSeconds;
  }

  private String getScheduledMessageKey(RqueueMessage message) {
    // avoid duplicate message enqueue due to retry by checking the message key
    // avoid cross slot error by using tagged queue name in the key
    // enqueuing duplicate message can lead to duplicate consumption when one job is executing task
    // at the same time this message was enqueued.
    return String.format(
        "%s%s%s%ssch%s%d",
        job.getQueueDetail().getQueueName(),
        REDIS_KEY_SEPARATOR,
        job.getRqueueMessage().getId(),
        REDIS_KEY_SEPARATOR,
        REDIS_KEY_SEPARATOR,
        message.getProcessAt());
  }

  private void processPeriodicMessage() {
    RqueueMessage newMessage =
        job.getRqueueMessage().toBuilder()
            .processAt(job.getRqueueMessage().nextProcessAt())
            .build();
    String messageKey = getScheduledMessageKey(newMessage);
    long expiryInSeconds = getTtlForScheduledMessageKey(newMessage);
    log.debug(
        "Schedule periodic message: {} Status: {}",
        job.getRqueueMessage(),
        getRqueueMessageTemplate()
            .scheduleMessage(
                job.getQueueDetail().getDelayedQueueName(),
                messageKey,
                newMessage,
                expiryInSeconds));
    handleMessage();
  }

  @Override
  public void start() {
    if (job.getRqueueMessage().isPeriodicTask()) {
      processPeriodicMessage();
    } else {
      handleMessage();
    }
  }
}
