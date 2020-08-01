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

package com.github.sonus21.rqueue.broker.config;

import com.github.sonus21.rqueue.config.RqueueConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SystemConfig {
  private final RqueueConfig rqueueConfig;

  @Autowired
  public SystemConfig(RqueueConfig rqueueConfig) {
    this.rqueueConfig = rqueueConfig;
  }

  @Value("${rqueue.root.user.key.suffix:auth:root}")
  private String rootUserKeySuffix;

  @Value("${rqueue.authentication.token.key.prefix:auth::token::}")
  private String tokenKeyPrefix;

  @Value("${rqueue.authentication.tokens.key.suffix:auth::tokens}")
  private String tokensKeySuffix;

  @Value("${rqueue.authentication.token.names.key.suffix:auth::token::names}")
  private String tokenNamesSuffix;

  @Value("${rqueue.authentication.session.key.suffix:auth:session:}")
  private String sessionKeySuffix;

  @Value("${rqueue.authentication.user.session.key.suffix:auth:user:session:}")
  private String userSessionKeySuffix;


  public String getRootUserKey() {
    return rqueueConfig.getPrefix() + rootUserKeySuffix;
  }

  public String getTokensKey() {
    return rqueueConfig.getPrefix() + tokensKeySuffix;
  }

  public String getSessionKey(String sessionId) {
    return rqueueConfig.getPrefix() + sessionKeySuffix + sessionId;
  }

  public String getUserSessionKey(String userName) {
    return rqueueConfig.getPrefix() + userSessionKeySuffix + userName;
  }

  public String getTokenKey(String tokenName) {
    return rqueueConfig.getPrefix() + tokenKeyPrefix + tokenName;
  }

  public String getTokenNamesKey() {
    return rqueueConfig.getPrefix() + tokenNamesSuffix;
  }
}
