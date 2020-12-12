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

package com.github.sonus21.rqueue.core;

import com.github.sonus21.rqueue.listener.QueueDetail;
import com.github.sonus21.rqueue.models.db.MessageMetadata;
import com.github.sonus21.rqueue.models.enums.JobStatus;

public interface Job {

  /**
   * Get job id corresponding to a message
   *
   * @return string job id
   */
  String getId();

  /**
   * RqueueMessage that's consumed by this job
   *
   * @return an object of RqueueMessage
   */
  RqueueMessage getRqueueMessage();

  /**
   * Checkin allows you to display a message for long running tasks, so that you can see the
   * progress
   *
   * @param message a serializable message, it could be a simple string like PING or some context
   *     data that says about the current execution state.
   */
  void checkIn(Object message);

  /**
   * A message that was enqueued
   *
   * @return an object could be null if deserialization fail.
   */
  Object getMessage();

  /**
   * MessageMetadata corresponding the enqueued message
   *
   * @return message metadata object
   */
  MessageMetadata getMessageMetadata();

  /**
   * The current status of this job, if you would like to know about the message status then check
   * {@link MessageMetadata#getStatus()}
   *
   * @return job status
   */
  JobStatus getStatus();

  /**
   * Any error detail, if it fails during execution
   *
   * @return an error object
   */
  Throwable getException();

  /**
   * Total execution time of the fetched RqueueMessages
   *
   * @return total execution time, that's sum of all listener method calls.
   */
  long getExecutionTime();

  QueueDetail getQueueDetail();
}