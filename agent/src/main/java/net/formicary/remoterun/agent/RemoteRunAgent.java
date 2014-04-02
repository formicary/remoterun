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
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.net.ssl.*;

import net.formicary.remoterun.agent.handler.Handler;
import net.formicary.remoterun.agent.handler.MasterToAgentHandler;
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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

/**
 * The "agent" is a TCP client that connects to a TCP server hosted by an embedded "master" server for the issuing of
 * remote instructions etc.
 *
 * @author Chris Pearson
 */
@Component
public class RemoteRunAgent extends SimpleChannelHandler implements ChannelFutureListener, MessageWriter {
  private static final Logger log = LoggerFactory.getLogger(RemoteRunAgent.class);
  private static final int RECONNECT_DELAY = 10000;
  private final Executor writePool;
  private final AnnotationConfigApplicationContext beanFactory;
  private final ClientBootstrap bootstrap;
  private final ReentrantLock writeLock = new ReentrantLock(true);
  private InetSocketAddress address;
  private boolean shutdown = false;
  private Channel channel;
  private ChannelFuture handshakeFuture;
  private Timer timer;
  private ChannelFuture lastWriteFuture;

  @Inject
  public RemoteRunAgent(Executor bossExecutor, Executor workerExecutor, Executor writePool, AnnotationConfigApplicationContext beanFactory) {
    this.writePool = writePool;
    this.beanFactory = beanFactory;
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
    new AnnotationConfigApplicationContext(AgentSpringConfig.class).getBean(RemoteRunAgent.class).connect(serverAddress);
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
    writePool.execute(new Runnable() {
      @Override
      public void run() {
        Map<String, Object> handlers = beanFactory.getBeansWithAnnotation(Handler.class);
        for(Object o : handlers.values()) {
          Handler handlerSpec = o.getClass().getAnnotation(Handler.class);
          for(RemoteRun.MasterToAgent.MessageType type : handlerSpec.value()) {
            if(type.equals(message.getMessageType())) {
              ((MasterToAgentHandler)o).handle(type, message, RemoteRunAgent.this);
            }
          }
        }
      }
    });
    ctx.sendUpstream(e);
  }

  @Override
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
