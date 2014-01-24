/*
 * Copyright 2014 Formicary Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.formicary.remoterun.common;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.UpstreamMessageEvent;
import org.jboss.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Netty logging handler that logs only sent and received messages.
 *
 * @author Chris Pearson
 */
public class NettyLoggingHandler extends LoggingHandler {
  private static final Logger log = LoggerFactory.getLogger(NettyLoggingHandler.class);

  @Override
  public void log(ChannelEvent e) {
    if(e instanceof UpstreamMessageEvent || e instanceof DownstreamMessageEvent) {
      log.debug(String.valueOf(e));
    }
  }
}
