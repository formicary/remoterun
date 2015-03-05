/*
 * Copyright 2015 Formicary Ltd
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

package net.formicary.remoterun.embed;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.*;
import javax.security.cert.X509Certificate;

import net.formicary.remoterun.common.KeyStoreUtil;
import net.formicary.remoterun.common.NettyLoggingHandler;
import net.formicary.remoterun.common.RemoteRunException;
import net.formicary.remoterun.common.proto.RemoteRun;
import net.formicary.remoterun.embed.callback.AgentConnectionCallback;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.handler.codec.protobuf.ProtobufDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.formicary.remoterun.embed.ConnectionState.*;

/**
 * Create a RemoteRunMaster then call {@link #bind} to start a TCP server to listen for agent connections.
 *
 * @author Chris Pearson
 */
public class RemoteRunMaster extends SimpleChannelHandler implements ChannelFutureListener {
  private static final Logger log = LoggerFactory.getLogger(RemoteRunMaster.class);
  private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();
  private final Set<AgentConnection> agentConnections = new CopyOnWriteArraySet<>();
  private final ServerBootstrap bootstrap;
  private AgentConnectionCallback callback;

  /**
   * The same as new RemoteRunMaster(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), null).
   */
  public RemoteRunMaster() {
    this(null);
  }

  /**
   * The same as new RemoteRunMaster(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), callback).
   */
  public RemoteRunMaster(AgentConnectionCallback callback) {
    this(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), callback);
  }

  /**
   * Creates a new RemoteRunMaster.
   *
   * @param bossExecutor the {@link Executor} which will execute the boss threads, see
   * {@link org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory(Executor, Executor)}
   * @param workerExecutor the {@link Executor} which will execute the I/O worker threads, see
   * {@link org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory(Executor, Executor)}
   * @param callback optional callback when agents connect/send messages
   */
  public RemoteRunMaster(Executor bossExecutor, Executor workerExecutor, AgentConnectionCallback callback) {
    this.callback = callback;
    NioServerSocketChannelFactory factory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor);
    bootstrap = new ServerBootstrap(factory);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          new SslHandler(createSslEngine()),

          new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4),
          new LengthFieldPrepender(4),

          new ProtobufDecoder(RemoteRun.AgentToMaster.getDefaultInstance()),
          new ProtobufEncoder(),

          new NettyLoggingHandler(),
          RemoteRunMaster.this
        );
      }
    });
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);
  }

  public static long getNextRequestId() {
    return NEXT_REQUEST_ID.getAndIncrement();
  }

  public AgentConnectionCallback getCallback() {
    return callback;
  }

  public void setCallback(AgentConnectionCallback callback) {
    this.callback = callback;
  }

  public static SSLEngine createSslEngine() {
    try {
      SSLContext sslContext = SSLContext.getInstance("TLSv1.1");
      KeyManager[] keyManagers = KeyStoreUtil.createKeyStore("JKS", "ssl/server-keystore.jks", "123456");
      TrustManager[] trustManagers = KeyStoreUtil.createTrustStore("JKS", "ssl/ca-truststore.jks", "123456");
      trustManagers[0] = new ServerTrustManager((X509TrustManager)trustManagers[0]); // wrap trust manager
      sslContext.init(keyManagers, trustManagers, null);
      SSLEngine sslEngine = sslContext.createSSLEngine();
      sslEngine.setNeedClientAuth(true);
      sslEngine.setUseClientMode(false);
      return sslEngine;
    } catch(Exception e) {
      throw new RemoteRunException("Failed to create server SSLEngine", e);
    }
  }

  /**
   * Bind and return the bound address.
   *
   * @param address socket address to bind to
   * @return where the socket was bound - useful to determine random port if a port of 0 was passed in
   */
  public InetSocketAddress bind(InetSocketAddress address) {
    Channel channel = bootstrap.bind(address);
    log.info("Listening for connections on " + channel.getLocalAddress());
    return (InetSocketAddress)channel.getLocalAddress();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    ctx.sendUpstream(e);
    ctx.getChannel().close();
    log.info("Exception caught, closing channel to agent from " + ctx.getChannel().getRemoteAddress().toString(), e.getCause());
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, final MessageEvent message) throws Exception {
    AgentConnection agent = (AgentConnection)ctx.getChannel().getAttachment();
    RemoteRun.AgentToMaster agentToMaster = (RemoteRun.AgentToMaster)message.getMessage();
    if(agentToMaster.hasAgentInfo()) {
      agent.setAgentInfo(agentToMaster.getAgentInfo());
      agent.setConnectionState(CONNECTED);
      String description = agent.getChannel().getRemoteAddress() + " (" + getPeerDn(message.getChannel()) + ")";
      log.info("Agent connection complete from " + description);
      log.debug("Agent information: {}", agentToMaster.getAgentInfo());
      if(callback != null) {
        try {
          callback.agentConnected(agent);
        } catch(Exception e) {
          log.error("Failed to process connected callback, closing connection to " + description, e);
          agent.shutdown();
        }
      }
    }
    try {
      agent.messageReceived(agent, agentToMaster);
      if(callback != null) {
        callback.messageReceived(agent, agentToMaster);
      }
    } catch(Exception e) {
      String description = message.getChannel().getRemoteAddress() + " (" + getPeerDn(message.getChannel()) + ")";
      log.error("Failed to process " + agentToMaster.getMessageType() + " message, closing connection to " + description, e);
      message.getChannel().close();
    }
    ctx.sendUpstream(message);
  }

  private static final String getPeerDn(Channel channel) {
    String peerDn;
    try {
      SSLEngine engine = channel.getPipeline().get(SslHandler.class).getEngine();
      X509Certificate peerCertificate = engine.getSession().getPeerCertificateChain()[0];
      peerDn = peerCertificate.getSubjectDN().toString();
    } catch(SSLPeerUnverifiedException e1) {
      log.trace("Unable to extract peer certificate DN", e1);
      peerDn = "unknown";
    }
    return peerDn;
  }

  @Override
  public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    AgentConnection connection = new AgentConnection(ctx.getChannel());
    ctx.getChannel().setAttachment(connection);
    agentConnections.add(connection);
    final SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
    sslHandler.handshake().addListener(this);
    log.info("Agent connected from " + ctx.getChannel().getRemoteAddress().toString() + " (" + agentConnections.size() + " open connections)");
    ctx.sendUpstream(e);
  }

  @Override
  public void operationComplete(ChannelFuture future) throws Exception {
    if(future.isSuccess()) {
      AgentConnection connection = (AgentConnection)future.getChannel().getAttachment();
      connection.setConnectionState(PENDING_AGENTINFO);
      SSLEngine engine = future.getChannel().getPipeline().get(SslHandler.class).getEngine();
      X509Certificate peerCertificate = engine.getSession().getPeerCertificateChain()[0];
      String description = future.getChannel().getRemoteAddress() + " (" + peerCertificate.getSubjectDN().toString() + ")";
      log.info("Agent connected (pending receipt of env info etc) from " + description);
    } else {
      future.getChannel().close();
    }
  }

  @Override
  public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    AgentConnection connection = (AgentConnection)ctx.getChannel().getAttachment();
    connection.setConnectionState(CLOSED);
    agentConnections.remove(connection);
    ctx.sendUpstream(e);
    log.info("Agent disconnected from " + ctx.getChannel().getRemoteAddress().toString() + " (" + agentConnections.size() + " open connections)");
    if(callback != null) {
      callback.agentDisconnected(connection);
    }
  }

  public Set<AgentConnection> getAgentConnections() {
    return agentConnections;
  }

  public void shutdown() {
    for(AgentConnection agentConnection : agentConnections) {
      agentConnection.shutdown();
    }
    bootstrap.shutdown();

  }

  /**
   * Get a new copy of a collection containing all the actively connected clients.
   *
   * @return a fresh list, can be modified as you wish
   */
  public Collection<AgentConnection> getConnectedClients() {
    List<AgentConnection> result = new ArrayList<>();
    for(AgentConnection agentConnection : agentConnections) {
      if(agentConnection.getConnectionState() == CONNECTED) {
        result.add(agentConnection);
      }
    }
    return result;
  }

  @Override
  public String toString() {
    return "RemoteRunMaster{" +
      "agentConnections=" + agentConnections +
      ", bootstrap=" + bootstrap +
      ", callback=" + callback +
      '}';
  }
}
