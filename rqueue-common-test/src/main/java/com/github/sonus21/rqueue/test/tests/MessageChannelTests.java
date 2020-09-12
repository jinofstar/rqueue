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

package com.github.sonus21.rqueue.test.tests;

import static com.github.sonus21.rqueue.utils.TimeoutUtils.waitFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.sonus21.rqueue.exception.TimedOutException;
import com.github.sonus21.rqueue.test.common.SpringTestBase;
import com.github.sonus21.rqueue.test.dto.Email;
import com.github.sonus21.rqueue.test.dto.Job;
import com.github.sonus21.rqueue.utils.Constants;
import com.github.sonus21.rqueue.utils.TimeoutUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class MessageChannelTests extends SpringTestBase {
  private final int messageCount = 200;
  /**
   * This test verifies whether any pending message in the delayed queue are moved or not whenever a
   * delayed message is pushed. During enqueue of delayed message we check whether there are any
   * pending messages on the delay queue, if expired delayed messages are found on the head then a
   * message is published on delayed channel.
   */
  protected void verifyPublishMessageIsTriggeredOnMessageAddition() throws TimedOutException {
    String delayedQueueName = rqueueConfig.getDelayedQueueName(emailQueue);
    enqueueIn(delayedQueueName, i -> Email.newInstance(), i -> -1000L, messageCount);
    Email email = Email.newInstance();
    log.info("adding new message {}", email);
    enqueueIn(emailQueue, email, Duration.ofMillis(1000));
    waitFor(
        () -> stringRqueueRedisTemplate.getZsetSize(delayedQueueName) <= 1,
        "one or zero messages in zset");
    assertTrue(
        stringRqueueRedisTemplate.getListSize(rqueueConfig.getQueueName(emailQueue))
            >= messageCount,
        "Messages are correctly moved");
    assertEquals(messageCount + 1L, getMessageCount(emailQueue));
  }

  /**
   * This test verifies whether any pending message in the processing queue are moved or not
   * whenever a message is pop. During pop of simple message we check whether there are any pending
   * messages on the processing queue, if expired messages are found on the head then a message is
   * published on processing channel.
   */
  protected void verifyPublishMessageIsTriggeredOnMessageRemoval() throws TimedOutException {
    List<Job> jobs = new ArrayList<>();
    List<String> ids = new ArrayList<>();
    int maxDelay = 2000;
    String processingQueue = rqueueConfig.getProcessingQueueName(jobQueue);
    for (int i = 0; i < messageCount; i++) {
      Job job = Job.newInstance();
      jobs.add(job);
      ids.add(job.getId());
      int delay = random.nextInt(maxDelay);
      if (random.nextBoolean()) {
        delay = delay * -1;
      }
      enqueueIn(job, processingQueue, delay);
    }
    TimeoutUtils.sleep(maxDelay);
    waitFor(
        () -> 0 == getMessageCount(jobQueue),
        30 * Constants.ONE_MILLI,
        "messages to be consumed");
    waitFor(
        () -> messageCount == consumedMessageService.getMessages(ids, Job.class).size(),
        30 * Constants.ONE_MILLI,
        "message count to be matched");
    waitFor(
        () -> jobs.containsAll(consumedMessageService.getMessages(ids, Job.class).values()),
        30 * Constants.ONE_MILLI,
        "All jobs to be executed");
  }
}
