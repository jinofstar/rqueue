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

package com.github.sonus21.rqueue.broker.service;

import com.github.sonus21.rqueue.broker.models.request.BatchMessageEnqueueRequest;
import com.github.sonus21.rqueue.broker.models.request.CreateQueueRequest;
import com.github.sonus21.rqueue.broker.models.request.DeleteQueueRequest;
import com.github.sonus21.rqueue.broker.models.request.MessageRequest;
import com.github.sonus21.rqueue.broker.models.request.UpdateQueueRequest;
import com.github.sonus21.rqueue.broker.models.response.CreateQueueResponse;
import com.github.sonus21.rqueue.broker.models.response.DeleteQueueResponse;
import com.github.sonus21.rqueue.broker.models.response.MessageEnqueueResponse;
import com.github.sonus21.rqueue.broker.models.response.MessageResponse;
import com.github.sonus21.rqueue.broker.models.response.UpdateQueueResponse;
import com.github.sonus21.rqueue.exception.LockException;
import com.github.sonus21.rqueue.exception.ProcessingException;
import com.github.sonus21.rqueue.exception.ValidationException;

public interface QueueService {
  CreateQueueResponse create(CreateQueueRequest request)
      throws LockException, ValidationException, ProcessingException;

  UpdateQueueResponse update(UpdateQueueRequest request)
      throws ValidationException, LockException, ProcessingException;

  DeleteQueueResponse delete(DeleteQueueRequest request)
      throws ProcessingException, ValidationException, LockException;

  MessageEnqueueResponse enqueue(BatchMessageEnqueueRequest request) throws ProcessingException;

  MessageResponse dequeue(MessageRequest messageRequest) throws ValidationException;
}
