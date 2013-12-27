package com.twock.remoterun.server;

import java.net.InetSocketAddress;
import javax.net.ssl.*;

import com.twock.remoterun.common.KeyStoreUtil;
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
 * @author Chris Pearson
 */
public class NettyServer extends SimpleChannelHandler implements ChannelFutureListener {
  private static final Logger log = LoggerFactory.getLogger(NettyServer.class);
  private static final int DEFAULT_PORT = 1081;
  private final InetSocketAddress address;
  private final ServerBootstrap bootstrap;

  public NettyServer(InetSocketAddress address) {
    this.address = address;
    NioServerSocketChannelFactory factory = new NioServerSocketChannelFactory();
    bootstrap = new ServerBootstrap(factory);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          new SslHandler(createSslEngine()),

          new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4),
          new LengthFieldPrepender(4),

          new ProtobufDecoder(RemoteRun.ClientToServer.getDefaultInstance()),
          new ProtobufEncoder(),

          NettyServer.this
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

  public static void main(String[] args) {
    new NettyServer(new InetSocketAddress(DEFAULT_PORT)).bind();
  }

  public void bind() {
    bootstrap.bind(address);
    log.info("Listening for connections on " + address.toString());
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    ctx.sendUpstream(e);
    ctx.getChannel().close();
    log.info("Exception caught, closing channel to client from " + ctx.getChannel().getRemoteAddress().toString(), e.getCause());
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    log.debug("Received " + e.getMessage());
    ctx.sendUpstream(e);
  }

  @Override
  public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    final SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
    sslHandler.handshake().addListener(this);
    log.info("Client connected from " + ctx.getChannel().getRemoteAddress().toString());
    ctx.sendUpstream(e);
  }

  @Override
  public void operationComplete(ChannelFuture future) throws Exception {
    if(future.isSuccess()) {
      log.info("Client connection complete from " + future.getChannel().getRemoteAddress());
    } else {
      future.getChannel().close();
    }
  }

  @Override
  public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    ctx.sendUpstream(e);
    log.info("Client disconnected from " + ctx.getChannel().getRemoteAddress().toString());
  }
}
