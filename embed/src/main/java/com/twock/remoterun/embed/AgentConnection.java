package com.twock.remoterun.embed;

import org.jboss.netty.channel.Channel;

/**
 * @author Chris Pearson
 */
public class AgentConnection {
  private Channel channel;
  private ConnectionState connectionState;

  public AgentConnection(Channel channel) {
    this.channel = channel;
    this.connectionState = ConnectionState.HANDSHAKING;
  }

  public Channel getChannel() {
    return channel;
  }

  public void setChannel(Channel channel) {
    this.channel = channel;
  }

  public ConnectionState getConnectionState() {
    return connectionState;
  }

  public void setConnectionState(ConnectionState connectionState) {
    this.connectionState = connectionState;
  }

  public void shutdown() {
    channel.close();
  }

  @Override
  public String toString() {
    return "AgentConnection{" +
      "channel=" + channel +
      ", connectionState=" + connectionState +
      '}';
  }
}
