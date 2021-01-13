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

import com.github.sonus21.rqueue.config.SimpleRqueueListenerContainerFactory;
import com.github.sonus21.rqueue.converter.RqueueRedisSerializer;
import com.github.sonus21.rqueue.listener.RqueueMessageHandler;
import com.github.sonus21.rqueue.utils.Constants;
import com.github.sonus21.test.ControllerProfiler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.actuate.redis.RedisReactiveHealthIndicator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@SpringBootApplication
@EnableRedisRepositories
@EnableCaching
public class RQueueApplication {
  @Value("${workers.count:3}")
  private int workersCount;

  public static void main(String[] args) {
    SpringApplication.run(RQueueApplication.class, args);
  }

  @Bean
  public SimpleRqueueListenerContainerFactory simpleRqueueListenerContainerFactory(
      RqueueMessageHandler rqueueMessageHandler) {
    SimpleRqueueListenerContainerFactory simpleRqueueListenerContainerFactory =
        new SimpleRqueueListenerContainerFactory();
    simpleRqueueListenerContainerFactory.setMaxNumWorkers(workersCount);
    simpleRqueueListenerContainerFactory.setPollingInterval(Constants.ONE_MILLI);
    simpleRqueueListenerContainerFactory.setRqueueMessageHandler(rqueueMessageHandler);
    return simpleRqueueListenerContainerFactory;
  }

  @Bean
  public ControllerProfiler controllerProfiler() {
    return new ControllerProfiler();
  }

  @Bean
  public MessageListenerProfiler messageListenerProfiler() {
    return new MessageListenerProfiler();
  }

  @Bean
  public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
    return new LettuceConnectionFactory();
  }

  @Bean
  @SuppressWarnings("unchecked")
  public ReactiveRedisTemplate<Object, Object> reactiveRedisTemplate(
      ReactiveRedisConnectionFactory reactiveRedisConnectionFactory) {
    RqueueRedisSerializer serializer = new RqueueRedisSerializer();
    RedisSerializationContext<Object, Object> serializationContext =
        RedisSerializationContext.newSerializationContext()
            .key((RedisSerializer) new StringRedisSerializer())
            .value(serializer)
            .hashKey(StringRedisSerializer.UTF_8)
            .hashValue(serializer)
            .build();
    return new ReactiveRedisTemplate<>(reactiveRedisConnectionFactory, serializationContext);
  }

  @Bean
  public ReactiveHealthIndicator reactiveHealthIndicator(
      ReactiveRedisConnectionFactory reactiveRedisConnectionFactory) {
    return new RedisReactiveHealthIndicator(reactiveRedisConnectionFactory);
  }
}
