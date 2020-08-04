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

package com.github.sonus21.rqueue.broker.models.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.sonus21.rqueue.core.RqueueMessage;
import com.github.sonus21.rqueue.models.SerializableBase;
import com.github.sonus21.rqueue.models.request.Message;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
public class MessageResponse extends SerializableBase {

  private static final long serialVersionUID = -753705665903141703L;
  private String id;

  private Message message;

  @JsonProperty("retry_count")
  private Integer retryCount;

  @JsonProperty("queued_time")
  private long queuedTime;

  @JsonProperty("process_at")
  private long processAt;

  @JsonProperty("re_enqueued_at")
  private Long reEnqueuedAt;

  @JsonProperty("failure_count")
  private int failureCount;

  public static MessageResponse fromRqueueMessage(RqueueMessage rqueueMessage, Message message) {
    return new MessageResponse(
        rqueueMessage.getId(),
        message,
        rqueueMessage.getRetryCount(),
        rqueueMessage.getQueuedTime(),
        rqueueMessage.getProcessAt(),
        rqueueMessage.getReEnqueuedAt(),
        rqueueMessage.getFailureCount());
  }
}
