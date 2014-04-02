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

import net.formicary.remoterun.common.proto.RemoteRun;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.MessageEvent;
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
    if(e instanceof MessageEvent) {
      Object message = ((MessageEvent)e).getMessage();
      if(message instanceof RemoteRun.AgentToMaster) {
        log.debug("{} {}: {}", e.getChannel().toString(), e instanceof DownstreamMessageEvent ? "WRITE" : "RECEIVED", toString((RemoteRun.AgentToMaster)message));
      } else {
        log.debug("{}", e);
      }
    }
  }

  private String toString(RemoteRun.AgentToMaster message) {
    // todo: is it possible to suppress fragment from protobuf default toString?
    StringBuilder sb = new StringBuilder();
    sb.append("messageType=").append(message.getMessageType());
    if(message.hasRequestId()) {
      sb.append(" requestId=").append(message.getRequestId());
    }
    if(message.hasFragment()) {
      sb.append(" fragment=[").append(message.getFragment().size()).append(" bytes]");
    }
    if(message.hasExitCode()) {
      sb.append(" exitCode=").append(message.getExitCode());
    }
    if(message.hasExitReason()) {
      sb.append(" exitReason=").append(message.getExitReason());
    }
    return sb.toString();
  }
}
