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

package com.github.sonus21.rqueue.example;

import com.github.sonus21.rqueue.core.RqueueMessageSender;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthAggregator;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicatorRegistry;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class Controller {

  private final RqueueMessageSender rqueueMessageSender;
  private final HealthEndpoint healthEndpoint;
  private final HealthAggregator healthAggregator;
  private final HealthIndicatorRegistry healthIndicatorRegistry;
  private final ReactiveHealthIndicator reactiveHealthIndicator;
  private final RedisTemplate<String, String> redisTemplate;
  private final LightRepository lightRepository;
  private final AtomicInteger atomicInteger = new AtomicInteger(0);
  private int statusIdx = 0;

  @GetMapping("health")
  public Health health() {
    Map<String, Health> health = new HashMap<>();
    for (Entry<String, HealthIndicator> entry : healthIndicatorRegistry.getAll().entrySet()) {
      health.put(entry.getKey(), entry.getValue().health());
    }

    return healthAggregator.aggregate(health);
  }

  @GetMapping("rhealth")
  public Mono<Health> rhealth() {
    return reactiveHealthIndicator.health();
  }

  @GetMapping(value = "/push")
  public String push(
      String q,
      String msg,
      @RequestParam(required = false) Integer numRetries,
      @RequestParam(required = false) Long delay) {
    if (numRetries == null && delay == null) {
      rqueueMessageSender.enqueue(q, msg);
    } else if (numRetries == null) {
      rqueueMessageSender.enqueueIn(q, msg, delay);
    } else {
      rqueueMessageSender.enqueueInWithRetry(q, msg, numRetries, delay);
    }
    log.info("Message {}", msg);
    return "Message sent successfully";
  }

  @GetMapping("job")
  public String sendJobNotification() {
    Job job = new Job();
    job.setId(UUID.randomUUID().toString());
    job.setMessage("Hi this is " + job.getId());
    rqueueMessageSender.enqueue("job-queue", job);
    log.info("{}", job);
    return job.toString();
  }

  @GetMapping("job-delay")
  public String sendJobNotificationWithDelay() {
    Job job = new Job();
    job.setId(UUID.randomUUID().toString());
    job.setMessage("Hi this is " + job.getId());
    rqueueMessageSender.enqueueIn("job-queue", job, 2000L);
    return job.toString();
  }

  @GetMapping(value = "/light/create", produces = MediaType.APPLICATION_JSON_VALUE)
  public Flux<Light> createTestLight() {
    String status = (++statusIdx % 2 == 0) ? "on" : "off";
    Light light = new Light(String.valueOf(atomicInteger.incrementAndGet()), "1", status);
    return lightRepository.save(light).flux();
  }

  @GetMapping(value = "/light/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<Light> lightLive() {
    return lightRepository.getLights();
  }
}
