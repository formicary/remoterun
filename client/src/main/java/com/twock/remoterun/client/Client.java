package com.twock.remoterun.client;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author Chris Pearson
 */
public class Client {
  public static void main(String[] args) {
    String hostname = args.length >= 1 ? args[0] : "127.0.0.1";
    int port = args.length >= 2 ? Integer.parseInt(args[0]) : 1081;
    InetSocketAddress serverAddress = new InetSocketAddress(hostname, port);
    Executor bossExecutor = Executors.newCachedThreadPool();
    Executor workerExecutor = Executors.newCachedThreadPool();
    NettyClient nettyClient = new NettyClient(serverAddress, bossExecutor, workerExecutor);
    nettyClient.connect();
  }
}
