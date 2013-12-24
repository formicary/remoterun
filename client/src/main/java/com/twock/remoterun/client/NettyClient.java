package com.twock.remoterun.client;

import java.net.InetSocketAddress;
import javax.net.ssl.*;

import com.twock.remoterun.common.KeyStoreUtil;
import com.twock.remoterun.common.RemoteRunException;
import com.twock.remoterun.common.proto.RemoteRun;
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
 * @author Chris Pearson
 */
public class NettyClient {
  private static final Logger log = LoggerFactory.getLogger(NettyClient.class);

  public static void main(String[] args) {
    ClientBootstrap bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory());
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          new SslHandler(createSslEngine()),

          new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4),
          new LengthFieldPrepender(4),

          new ProtobufDecoder(RemoteRun.RunRequest.getDefaultInstance()),
          new ProtobufEncoder(),

          new SimpleChannelHandler() {
            @Override
            public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
              log.debug("Channel open");
              super.channelConnected(ctx, e);
              final SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
              ChannelFuture handshakeFuture = sslHandler.handshake();
              handshakeFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                  channelFuture.getChannel().write(RemoteRun.RunRequest.newBuilder().setCmd("echo").addArgs("hello").build());
                }
              });
            }

            @Override
            public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
              log.debug("Received " + e.getMessage());
              super.messageReceived(ctx, e);
            }

            @Override
            public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
              log.debug("Channel closed");
              super.channelClosed(ctx, e);
            }
          }
        );
      }
    });
    bootstrap.setOption("tcpNoDelay", true);
    bootstrap.setOption("keepAlive", true);
    bootstrap.connect(new InetSocketAddress(1081));
  }

  public static SSLEngine createSslEngine() {
    try {
      SSLContext sslContext = SSLContext.getInstance("TLSv1.1");
      KeyManager[] keyManagers = KeyStoreUtil.createKeyStore("JKS", "ssl/client1-keystore.jks", "123456");
      TrustManager[] trustManagers = KeyStoreUtil.createTrustStore("JKS", "ssl/ca-truststore.jks", "123456");
      sslContext.init(keyManagers, trustManagers, null);
      SSLEngine sslEngine = sslContext.createSSLEngine();
      sslEngine.setUseClientMode(true);
      return sslEngine;
    } catch(Exception e) {
      throw new RemoteRunException("Failed to create client SSLEngine", e);
    }
  }
}
