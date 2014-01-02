package com.twock.remoterun.common;

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
