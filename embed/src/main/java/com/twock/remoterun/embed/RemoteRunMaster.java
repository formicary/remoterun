package com.twock.remoterun.embed;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executor;
import javax.net.ssl.*;
import javax.security.cert.X509Certificate;

import com.twock.remoterun.common.KeyStoreUtil;
import com.twock.remoterun.common.NettyLoggingHandler;
import com.twock.remoterun.common.RemoteRunException;
import com.twock.remoterun.common.proto.RemoteRun;
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
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    // todo
    ctx.sendUpstream(e);
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
      log.info("Agent connection complete from " + future.getChannel().getRemoteAddress() + " (" + peerCertificate.getSubjectDN().toString() + ")");
      if(callback != null) {
        callback.agentConnected(connection);
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
    List<AgentConnection> result = new ArrayList<AgentConnection>();
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