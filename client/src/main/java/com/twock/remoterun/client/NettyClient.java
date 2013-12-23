package com.twock.remoterun.client;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import com.twock.remoterun.common.KeyStoreUtil;
import com.twock.remoterun.common.RemoteRunException;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LineBasedFrameDecoder;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.logging.LoggingHandler;
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
        SslHandler sslHandler = new SslHandler(createSslEngine());
        return Channels.pipeline(
          new LoggingHandler(),
          sslHandler,
          new LoggingHandler(),
          new LineBasedFrameDecoder(1024),
          new StringDecoder(),
          new StringEncoder(),
          new LoggingHandler(),
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
                  channelFuture.getChannel().write("Boo, this is from the client\r\n");
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
    bootstrap.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), 1081));
  }

  public static SSLEngine createSslEngine() {
    try {
      SSLContext sslContext = SSLContext.getInstance("TLSv1.1");
      sslContext.init(KeyStoreUtil.createKeyStore(), null, null);
      SSLEngine sslEngine = sslContext.createSSLEngine();
      sslEngine.setUseClientMode(true);
      return sslEngine;
    } catch(Exception e) {
      throw new RemoteRunException("Failed to create client SSLEngine", e);
    }
  }
}
