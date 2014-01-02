package com.twock.remoterun.server;

import org.jboss.netty.channel.Channel;

/**
 * @author Chris Pearson
 */
public class ClientConnection {
  private Channel channel;
  private ConnectionState connectionState;

  public ClientConnection(Channel channel) {
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
    return "ClientConnection{" +
      "channel=" + channel +
      ", connectionState=" + connectionState +
      '}';
  }

  public static enum ConnectionState {
    CLOSED,
    HANDSHAKING,
    CONNECTED
  }

}
