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

import com.github.sonus21.rqueue.common.RqueueRedisTemplate;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

@Repository
@Slf4j
public class LightRepository {

  private final ReactiveRedisTemplate<Object, Object> redisTemplate;
  private final RqueueRedisTemplate<Serializable> rqueueRedisTemplate;

  @Autowired
  public LightRepository(
      ReactiveRedisTemplate<Object, Object> reactiveRedisTemplate,
      RedisConnectionFactory redisConnectionFactory) {
    this.redisTemplate = reactiveRedisTemplate;
    this.rqueueRedisTemplate = new RqueueRedisTemplate<>(redisConnectionFactory);
  }

  public Mono<Light> save(Light light) {
    Map<String, String> lightMap = new HashMap<>();
    lightMap.put("key", light.getKey());
    lightMap.put("status", light.getStatus());
    return redisTemplate.opsForStream().add("mystream", lightMap).map(e -> light);
  }

  private List<Light> readNonReactive() {
    StreamOffset<String> offset = StreamOffset.fromStart("mystream"); // fromStart or Latest
    log.info("Reading");
    return rqueueRedisTemplate.getRedisTemplate().opsForStream().read(offset).stream()
        .map(
            e -> {
              Map<Object, Object> kvp = e.getValue();
              String key = (String) kvp.get("key");
              String id = (String) kvp.get("id");
              String status = (String) kvp.get("status");
              Light light = new Light(id, key, status);
              log.info("{}", light);
              return light;
            })
        .collect(Collectors.toList());
  }

  private List<Light> reactiveReadToList() {
    log.info("reactiveReadToList");
    return read().collectList().block();
  }

  private Flux<Light> read() {
    StreamOffset<Object> offset = StreamOffset.fromStart("mystream");
    return redisTemplate
        .opsForStream()
        .read(offset)
        .flatMap(
            e -> {
              Map<Object, Object> kvp = e.getValue();
              String key = (String) kvp.get("key");
              String id = (String) kvp.get("id");
              String status = (String) kvp.get("status");
              Light light = new Light(id, key, status);
              log.info("{}", light);
              return Flux.just(light);
            });
  }

  public Flux<Light> getLights() {
    return Flux.create(e -> new DataReader(e).register());
  }

  @RequiredArgsConstructor
  class DataReader {

    @NonNull FluxSink<Light> sink;
    private List<Light> readLights = null;
    private int currentOffset = 0;

    void register() {
      readLights = reactiveReadToList();
      sink.onRequest(
          e -> {
            long demand = sink.requestedFromDownstream();
            for (int i = 0; i < demand && currentOffset < readLights.size(); i++, currentOffset++) {
              sink.next(readLights.get(currentOffset));
            }
            if (currentOffset == readLights.size()) {
              readLights = reactiveReadToList();
              currentOffset = 0;
            }
          });
    }
  }
}
