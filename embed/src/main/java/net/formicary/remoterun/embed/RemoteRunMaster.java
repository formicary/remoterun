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

package net.formicary.remoterun.embed;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.*;
import javax.security.cert.X509Certificate;

import net.formicary.remoterun.common.KeyStoreUtil;
import net.formicary.remoterun.common.NettyLoggingHandler;
import net.formicary.remoterun.common.RemoteRunException;
import net.formicary.remoterun.common.proto.RemoteRun;
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

/**
 * Create a RemoteRunMaster then call {@link #bind} to start a TCP server to listen for agent connections.
 *
 * @author Chris Pearson
 */
public class RemoteRunMaster extends SimpleChannelHandler implements ChannelFutureListener {
  private static final Logger log = LoggerFactory.getLogger(RemoteRunMaster.class);
  private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();
  private final Set<AgentConnection> agentConnections = Collections.synchronizedSet(new HashSet<AgentConnection>());
  private final ServerBootstrap bootstrap;
  private AgentConnectionCallback callback;

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

  public void bind(InetSocketAddress address) {
    bootstrap.bind(address);
    log.info("Listening for connections on " + address.toString());
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
    try {
      callback.messageReceived(agent, (RemoteRun.AgentToMaster)message.getMessage());
    } catch(Exception e) {
      String peerDn;
      try {
        SSLEngine engine = message.getChannel().getPipeline().get(SslHandler.class).getEngine();
        X509Certificate peerCertificate = engine.getSession().getPeerCertificateChain()[0];
        peerDn = peerCertificate.getSubjectDN().toString();
      } catch(SSLPeerUnverifiedException e1) {
        log.trace("Unable to extract peer certificate DN", e1);
        peerDn = "unknown";
      }
      String description = message.getChannel().getRemoteAddress() + " (" + peerDn + ")";
      log.error("Failed to process " + ((RemoteRun.AgentToMaster)message.getMessage()).getMessageType() + " message, closing connection to " + description, e);
      message.getChannel().close();
    }
    ctx.sendUpstream(message);
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
      connection.setConnectionState(ConnectionState.CONNECTED);
      SSLEngine engine = future.getChannel().getPipeline().get(SslHandler.class).getEngine();
      X509Certificate peerCertificate = engine.getSession().getPeerCertificateChain()[0];
      String description = future.getChannel().getRemoteAddress() + " (" + peerCertificate.getSubjectDN().toString() + ")";
      log.info("Agent connection complete from " + description);
      if(callback != null) {
        try {
          callback.agentConnected(connection);
        } catch(Exception e) {
          log.error("Failed to process connected callback, closing connection to " + description, e);
          future.getChannel().close();
        }
      }
    } else {
      future.getChannel().close();
    }
  }

  @Override
  public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    AgentConnection connection = (AgentConnection)ctx.getChannel().getAttachment();
    connection.setConnectionState(ConnectionState.CLOSED);
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
    synchronized(agentConnections) {
      for(AgentConnection agentConnection : agentConnections) {
        agentConnection.shutdown();
      }
    }
    bootstrap.shutdown();
  }

  public Collection<AgentConnection> getConnectedClients() {
    List<AgentConnection> result = new ArrayList<>();
    for(AgentConnection agentConnection : agentConnections) {
      if(agentConnection.getConnectionState() == ConnectionState.CONNECTED) {
        result.add(agentConnection);
      }
    }
    return result;
  }

  @Override
  public String toString() {
    return "NettyServer{" +
      "agentConnections=" + agentConnections +
      ", bootstrap=" + bootstrap +
      ", callback=" + callback +
      '}';
  }
}
