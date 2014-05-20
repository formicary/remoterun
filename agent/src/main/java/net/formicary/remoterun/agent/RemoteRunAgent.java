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

package net.formicary.remoterun.agent;

import java.net.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PreDestroy;
import javax.net.ssl.*;

import com.google.protobuf.ByteString;
import net.formicary.remoterun.common.*;
import net.formicary.remoterun.common.proto.RemoteRun;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.handler.codec.protobuf.ProtobufDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.formicary.remoterun.common.proto.RemoteRun.AgentToMaster.AgentInfo;
import static net.formicary.remoterun.common.proto.RemoteRun.AgentToMaster.MessageType.AGENT_INFO;
import static net.formicary.remoterun.common.proto.RemoteRun.AgentToMaster.MessageType.REQUESTED_DATA;
import static net.formicary.remoterun.common.proto.RemoteRun.MasterToAgent.MessageType.*;

/**
 * The "agent" is a TCP client that connects to a TCP server hosted by an embedded "master" server for the issuing of
 * remote instructions etc.
 *
 * @author Chris Pearson
 */
public class RemoteRunAgent extends SimpleChannelHandler implements ChannelFutureListener {
  private static final Logger log = LoggerFactory.getLogger(RemoteRunAgent.class);
  private static final int RECONNECT_DELAY = 10000;
  private final ProcessHandler processHandler = new ProcessHandler();
  private final SentFileHandler sentFileHandler = new SentFileHandler();
  private final Executor writePool;
  private final ClientBootstrap bootstrap;
  private final ReentrantLock writeLock = new ReentrantLock(true);
  private InetSocketAddress address;
  private boolean shutdown = false;
  private Channel channel;
  private ChannelFuture handshakeFuture;
  private Timer timer;
  private ChannelFuture lastWriteFuture;
  private AgentInfo agentInfo;

  public RemoteRunAgent(Executor bossExecutor, Executor workerExecutor, Executor writePool) {
    this.writePool = writePool;
    bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(bossExecutor, workerExecutor));
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          new SslHandler(createSslEngine()),

          new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4),
          new LengthFieldPrepender(4),

          new ProtobufDecoder(RemoteRun.MasterToAgent.getDefaultInstance()),
          new ProtobufEncoder(),

          new NettyLoggingHandler(),
          RemoteRunAgent.this
        );
      }
    });
    bootstrap.setOption("tcpNoDelay", true);
    bootstrap.setOption("keepAlive", true);
  }

  public static void main(String[] args) {
    String hostname = args.length >= 1 ? args[0] : "127.0.0.1";
    int port = args.length >= 2 ? Integer.parseInt(args[1]) : 1081;
    InetSocketAddress serverAddress = new InetSocketAddress(hostname, port);
    new RemoteRunAgent(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), Executors.newCachedThreadPool()).connect(serverAddress);
  }

  public static SSLEngine createSslEngine() {
    try {
      SSLContext sslContext = SSLContext.getInstance("TLSv1.1");
      KeyManager[] keyManagers = KeyStoreUtil.createKeyStore("JKS", "ssl/agent1-keystore.jks", "123456");
      TrustManager[] trustManagers = KeyStoreUtil.createTrustStore("JKS", "ssl/ca-truststore.jks", "123456");
      sslContext.init(keyManagers, trustManagers, null);
      SSLEngine sslEngine = sslContext.createSSLEngine();
      sslEngine.setUseClientMode(true);
      return sslEngine;
    } catch(Exception e) {
      throw new RemoteRunException("Failed to create agent SSLEngine", e);
    }
  }

  public ChannelFuture connect(InetSocketAddress address) {
    this.address = address;
    // prepare the information about the local agent's environment for transmission on connection
    AgentInfo.Builder builder = AgentInfo.newBuilder();
    try {
      InetAddress localHost = InetAddress.getLocalHost();
      builder.setIpAddress(ByteString.copyFrom(localHost.getAddress()));
      builder.setHostname(localHost.getCanonicalHostName());
    } catch(UnknownHostException e) {
      log.debug("Unable to resolve local hostname to IP", e);
    }
    Properties properties = System.getProperties();
    for(Map.Entry<Object, Object> entry : properties.entrySet()) {
      builder.addEnvironment(RemoteRun.StringStringKeyValuePair.newBuilder()
        .setKey((String)entry.getKey())
        .setValue((String)entry.getValue()).build());
    }
    this.agentInfo = builder.build();
    ChannelFuture connect = bootstrap.connect(address);
    channel = connect.getChannel();
    return connect;
  }

  @Override
  public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    log.info("Connected to " + ctx.getChannel().getRemoteAddress());
    this.channel = ctx.getChannel();
    final SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
    handshakeFuture = sslHandler.handshake();
    handshakeFuture.addListener(this);
    super.channelConnected(ctx, e);
  }

  @Override
  public void operationComplete(ChannelFuture future) throws Exception {
    if(future.isSuccess()) {
      handshakeFuture = null;
      log.info("Agent SSL handshake completed, connected to " + future.getChannel().getRemoteAddress());
      write(RemoteRun.AgentToMaster.newBuilder().setMessageType(AGENT_INFO).setAgentInfo(agentInfo).build());
    } else {
      future.getChannel().close();
    }
  }

  @Override
  public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    ctx.getChannel().close();
    ctx.sendUpstream(e);
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    if(e.getCause() != null
      && ConnectException.class.equals(e.getCause().getClass())
      && e.getCause().getMessage() != null
      && e.getCause().getMessage().startsWith("Connection refused: ")) {
      log.info(e.getCause().getMessage());
    } else {
      log.info("Exception caught, closing channel to server" + (ctx.getChannel().getRemoteAddress() == null ? "" : " at " + ctx.getChannel().getRemoteAddress()), e.getCause());
    }
    ctx.sendUpstream(e);
    ctx.getChannel().close();
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    final RemoteRun.MasterToAgent message = (RemoteRun.MasterToAgent)e.getMessage();
    final RemoteRun.MasterToAgent.MessageType type = message.getMessageType();

    if(type == REQUEST_DATA) {
      final long requestId = message.getRequestId();
      writePool.execute(new FileStreamer(Paths.get(message.getPath()), new FileStreamer.FileStreamerCallback() {
        @Override
        public void writeDataChunk(byte[] data, int offset, int length) {
          write(RemoteRun.AgentToMaster.newBuilder()
            .setMessageType(REQUESTED_DATA)
            .setRequestId(requestId)
            .setFragment(ByteString.copyFrom(data, offset, length))
            .build());
        }

        @Override
        public void finished(boolean success, String errorMessage, Throwable cause) {
          RemoteRun.AgentToMaster.Builder builder = RemoteRun.AgentToMaster.newBuilder()
            .setMessageType(REQUESTED_DATA)
            .setRequestId(requestId);
          if(success) {
            builder.setExitCode(0);
          } else {
            builder.setExitCode(1).setExitReason(errorMessage + ": " + cause.toString());
          }
          write(builder.build());
        }
      }));

    } else {
      if(type == RUN_COMMAND || type == STDIN_FRAGMENT || type == CLOSE_STDIN) {
        processHandler.handle(message, RemoteRunAgent.this);

      } else if(type == SEND_DATA_NOTIFICATION || type == SEND_DATA_FRAGMENT) {
        sentFileHandler.handle(message, RemoteRunAgent.this);

      }
    }
    ctx.sendUpstream(e);
  }

  public void write(RemoteRun.AgentToMaster message) {
    // fair write lock to ensure every thread gets a chance to write data, and avoid a single thread hogging the writes
    // if write buffer is full, only one thread will be within the write/waitUntilWriteable call
    // Given that data is written a chunk at a time, the fair lock should ensure everything gets a look in
    writeLock.lock();
    try {
      if(lastWriteFuture != null) {
        try {
          lastWriteFuture.await();
        } catch(InterruptedException e) {
        }
      }
      lastWriteFuture = channel.write(message);
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    if(ctx.getChannel().getRemoteAddress() != null) {
      log.info("Disconnected from " + ctx.getChannel().getRemoteAddress());
    }
    channel = null;
    ctx.sendUpstream(e);
    if(!shutdown) {
      timer = new Timer();
      timer.schedule(new TimerTask() {
        @Override
        public void run() {
          connect(address);
          timer.cancel();
        }
      }, RECONNECT_DELAY);
    }
  }

  @PreDestroy
  public void shutdown() {
    shutdown = true;
    bootstrap.shutdown();
  }
}
