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

package com.github.sonus21.rqueue.converter;

import static org.springframework.util.Assert.notNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.sonus21.rqueue.utils.SerializationUtils;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.support.GenericMessage;

/**
 * A converter to turn the payload of a {@link Message} from serialized form to a typed String and
 * vice versa. This class does not support generic class except {@link List},even for list the
 * entries should be non generic.
 */
@Slf4j
public class GenericMessageConverter implements SmartMessageConverter {
  private final ObjectMapper objectMapper;

  public GenericMessageConverter() {
    this(new ObjectMapper());
  }

  public GenericMessageConverter(ObjectMapper objectMapper) {
    notNull(objectMapper, "objectMapper cannot be null");
    this.objectMapper = objectMapper;
  }

  private JavaType getTargetType(Msg msg, Class<?> targetClass) throws ClassNotFoundException {
    // do not use target class information due to class hierarchy
    // use case a listener consumes messages of multiple subclass type
    String[] classNames = splitClassNames(msg.getName());
    if (classNames.length == 1) {
      Class<?> c = Thread.currentThread().getContextClassLoader().loadClass(msg.getName());
      return objectMapper.getTypeFactory().constructType(c);
    }
    Class<?> envelopeClass =
        Thread.currentThread().getContextClassLoader().loadClass(classNames[0]);
    Class<?>[] classes = new Class<?>[classNames.length - 1];
    for (int i = 1; i < classNames.length; i++) {
      classes[i - 1] = Thread.currentThread().getContextClassLoader().loadClass(classNames[i]);
    }
    return objectMapper.getTypeFactory().constructParametricType(envelopeClass, classes);
  }

  /**
   * Convert the payload of a {@link Message} from a serialized form to a typed Object of type
   * stored in message it self.
   *
   * <p>If the converter cannot perform the conversion it returns {@code null}.
   *
   * @param message the input message
   * @param targetClass the target class for the conversion
   * @return the result of the conversion, or {@code null} if the converter cannot perform the
   *     conversion.
   */
  @Override
  public Object fromMessage(Message<?> message, Class<?> targetClass) {
    log.debug("Message: {} class: {}", message, targetClass);
    try {
      String payload = (String) message.getPayload();
      if (SerializationUtils.isJson(payload)) {
        Msg msg = objectMapper.readValue(payload, Msg.class);
        JavaType type = getTargetType(msg, targetClass);
        return objectMapper.readValue(msg.msg, type);
      }
    } catch (Exception e) {
      log.warn("Deserialization of message {} failed", message, e);
    }
    return null;
  }

  private String[] splitClassNames(String name) {
    return name.split("#");
  }

  private String getClassNameForCollection(String name, Collection<?> payload) {
    if (payload instanceof List) {
      if (payload.isEmpty()) {
        return null;
      }
      String itemClassName = getClassName(((List<?>) payload).get(0));
      if (itemClassName == null) {
        return null;
      }
      return name + '#' + itemClassName;
    }
    return null;
  }

  private String getGenericFieldBasedClassName(Class<?> clazz) {
    TypeVariable<?>[] typeVariables = clazz.getTypeParameters();
    if (typeVariables.length == 0) {
      return clazz.getName();
    }
    return null;
  }

  private String getClassName(Object payload) {
    Class<?> payloadClass = payload.getClass();
    String name = payloadClass.getName();
    if (payload instanceof Collection) {
      return getClassNameForCollection(name, (Collection<?>) payload);
    }
    return getGenericFieldBasedClassName(payloadClass);
  }

  /**
   * Create a {@link Message} whose payload is the result of converting the given payload Object to
   * serialized form. It ignores all headers components.
   *
   * <p>If the converter cannot perform the conversion, it return {@code null}.
   *
   * @param payload the Object to convert
   * @param headers optional headers for the message (may be {@code null})
   * @return the new message, or {@code null}
   */
  @Override
  public Message<?> toMessage(Object payload, MessageHeaders headers) {
    log.debug("Payload: {} headers: {}", payload, headers);
    String name = getClassName(payload);
    if (name == null) {
      return null;
    }
    try {
      String msg = objectMapper.writeValueAsString(payload);
      Msg message = new Msg(name, msg);
      return new GenericMessage<>(objectMapper.writeValueAsString(message));
    } catch (JsonProcessingException e) {
      log.warn("Serialisation failed", e);
      return null;
    }
  }

  @Override
  public Object fromMessage(Message<?> message, Class<?> targetClass, Object conversionHint) {
    log.debug("Message: {} class: {} hint: {}", message, targetClass, conversionHint);
    return fromMessage(message, targetClass);
  }

  @Override
  public Message<?> toMessage(Object payload, MessageHeaders headers, Object conversionHint) {
    log.debug("Payload: {} headers: {} hint: {}", payload, headers, conversionHint);
    return toMessage(payload, headers);
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Msg {
    private String name;
    private String msg;
  }
}
