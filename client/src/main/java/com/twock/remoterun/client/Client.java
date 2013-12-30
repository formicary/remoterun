package com.twock.remoterun.client;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author Chris Pearson
 */
public class Client {
  public static void main(String[] args) {
    InetSocketAddress serverAddress = new InetSocketAddress("127.0.0.1", 1081);
    Executor bossExecutor = Executors.newCachedThreadPool();
    Executor workerExecutor = Executors.newCachedThreadPool();
    NettyClient nettyClient = new NettyClient(serverAddress, bossExecutor, workerExecutor);
    nettyClient.connect();
  }
}
