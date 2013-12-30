package com.twock.remoterun.server;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author Chris Pearson
 */
public class Server implements NettyServer.ServerConnectionCallback {
  private NettyServer nettyServer;

  public static void main(String[] args) {
    new Server().run();
  }

  public void run() {
    Executor bossExecutor = Executors.newCachedThreadPool();
    Executor workerExecutor = Executors.newCachedThreadPool();
    nettyServer = new NettyServer(bossExecutor, workerExecutor, this);
    InetSocketAddress bindAddress = new InetSocketAddress(1081);
    nettyServer.bind(bindAddress);
  }

  @Override
  public void clientConnected(ClientConnection clientConnection) {

  }

  @Override
  public void clientDisconnected(ClientConnection clientConnection) {

  }
}
