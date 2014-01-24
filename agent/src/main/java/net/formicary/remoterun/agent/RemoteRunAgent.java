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

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.net.ssl.*;

import com.google.protobuf.ByteString;
import net.formicary.remoterun.agent.process.ProcessHelper;
import net.formicary.remoterun.agent.process.ReadCallback;
import net.formicary.remoterun.common.KeyStoreUtil;
import net.formicary.remoterun.common.NettyLoggingHandler;
import net.formicary.remoterun.common.RemoteRunException;
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

/**
 * The "agent" is a TCP client that connects to a TCP server hosted by an embedded "master" server for the issuing of
 * remote instructions etc.
 *
 * @author Chris Pearson
 */
public class RemoteRunAgent extends SimpleChannelHandler implements ChannelFutureListener, ReadCallback {
  private static final Logger log = LoggerFactory.getLogger(RemoteRunAgent.class);
  private static final int RECONNECT_DELAY = 10000;
  private final InetSocketAddress address;
  private final Map<Long, ProcessHelper> processes = Collections.synchronizedMap(new TreeMap<Long, ProcessHelper>());
  private final ClientBootstrap bootstrap;
  private boolean shutdown = false;
  private Channel channel;
  private ChannelFuture handshakeFuture;
  private Timer timer;
  private ChannelFuture lastWriteFuture;

  public RemoteRunAgent(InetSocketAddress address, Executor bossExecutor, Executor workerExecutor) {
    this.address = address;
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
    int port = args.length >= 2 ? Integer.parseInt(args[0]) : 1081;
    InetSocketAddress serverAddress = new InetSocketAddress(hostname, port);
    Executor bossExecutor = Executors.newCachedThreadPool();
    Executor workerExecutor = Executors.newCachedThreadPool();
    RemoteRunAgent agent = new RemoteRunAgent(serverAddress, bossExecutor, workerExecutor);
    agent.connect();
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

  public ChannelFuture connect() {
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
      // todo: connected successfully
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
    RemoteRun.MasterToAgent message = (RemoteRun.MasterToAgent)e.getMessage();
    long requestId = message.getRequestId();
    if(RemoteRun.MasterToAgent.MessageType.RUN_COMMAND == message.getMessageType()) {
      RemoteRun.MasterToAgent.RunCommand runCommand = message.getRunCommand();
      try {
        // start the process
        ProcessHelper processHelper = new ProcessHelper(requestId, runCommand.getCmd(), runCommand.getArgsList(), this);
        processes.put(requestId, processHelper);
        // write a success reply
        write(RemoteRun.AgentToMaster.newBuilder().setMessageType(RemoteRun.AgentToMaster.MessageType.STARTED).setRequestId(requestId).build());
        processHelper.start();
      } catch(Exception e1) {
        log.info("Failed to start process " + runCommand, e1);
        // write a failure reply
        write(RemoteRun.AgentToMaster.newBuilder().setMessageType(RemoteRun.AgentToMaster.MessageType.EXITED)
          .setRequestId(requestId).setExitCode(-1)
          .setExitReason(e1.getClass().getName() + ": " + e1.getMessage())
          .build());
      }

    } else if(RemoteRun.MasterToAgent.MessageType.STDIN_FRAGMENT == message.getMessageType()) {
      ProcessHelper processHelper = processes.get(requestId);
      if(processHelper == null) {
        log.warn("Ignoring STDIN fragment for invalid request ID " + requestId);
      } else {
        processHelper.writeStdIn(message.getStdinFragment().toByteArray());
      }

    } else if(RemoteRun.MasterToAgent.MessageType.CLOSE_STDIN == message.getMessageType()) {
      ProcessHelper processHelper = processes.get(requestId);
      if(processHelper == null) {
        log.warn("Ignoring CLOSE_STDIN request for invalid request ID " + requestId);
      } else {
        processHelper.closeStdIn();
      }
    }
    ctx.sendUpstream(e);
  }

  private void write(RemoteRun.AgentToMaster message) {
    lastWriteFuture = channel.write(message);
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
          connect();
          timer.cancel();
        }
      }, RECONNECT_DELAY);
    }
  }

  public void shutdown() {
    shutdown = true;
    bootstrap.shutdown();
  }

  @Override
  public void dataAvailable(ByteBuffer buffer, long serverId, RemoteRun.AgentToMaster.MessageType type) {
    waitUntilWritable();
    write(RemoteRun.AgentToMaster.newBuilder().setMessageType(type)
      .setRequestId(serverId)
      .setFragment(ByteString.copyFrom(buffer)).build());
  }

  private void waitUntilWritable() {
    if(!channel.isWritable()) {
      try {
        lastWriteFuture.await();
      } catch(InterruptedException e) {
      }
    }
  }

  @Override
  public void finished(long serverId) {
    ProcessHelper processHelper = processes.get(serverId);
    if(processHelper != null && processHelper.isFinished()) {
      waitUntilWritable();
      ProcessHelper process = processes.remove(serverId);
      if(process != null) {
        write(RemoteRun.AgentToMaster.newBuilder().setMessageType(RemoteRun.AgentToMaster.MessageType.EXITED)
          .setRequestId(serverId)
          .setExitCode(processHelper.getProcess().exitValue()).build());
      }
    }
  }
}
