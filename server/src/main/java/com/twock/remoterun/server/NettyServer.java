package com.twock.remoterun.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.CertificateException;
import javax.net.ssl.*;

import com.twock.remoterun.common.RemoteRunException;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
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
public class NettyServer {
  private static final Logger log = LoggerFactory.getLogger(NettyServer.class);

  public static void main(String[] args) throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
    defaultSystemProperty("javax.net.ssl.trustStore", "ssl/ca-trustStore.jks");
    defaultSystemProperty("javax.net.ssl.trustStorePassword", "123456");
    defaultSystemProperty("javax.net.ssl.keyStore", "ssl/server-keystore.jks");
    defaultSystemProperty("javax.net.ssl.keyStorePassword", "123456");
    NioServerSocketChannelFactory factory = new NioServerSocketChannelFactory();
    ServerBootstrap bootstrap = new ServerBootstrap(factory);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          new SslHandler(createSslEngine()),
          new LineBasedFrameDecoder(1024),
          new StringDecoder(),
          new StringEncoder(),
          new LoggingHandler(),
          new SimpleChannelHandler() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
              ctx.sendUpstream(e);
              log.info("Exception caught, closing channel to client from " + ctx.getChannel().getRemoteAddress().toString(), e.getCause());
              ctx.getChannel().close();
            }

            @Override
            public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
              log.debug("Received " + e.getMessage());
              ctx.sendUpstream(e);
            }

            @Override
            public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
              ctx.sendUpstream(e);
              log.info("Client connected from " + ctx.getChannel().getRemoteAddress().toString());
            }

            @Override
            public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
              ctx.sendUpstream(e);
              log.info("Client disconnected from " + ctx.getChannel().getRemoteAddress().toString());
            }
          }
        );
      }
    });
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);
    InetSocketAddress localAddress = new InetSocketAddress(1081);
    bootstrap.bind(localAddress);
    log.info("Listening for connections on " + localAddress.toString());
  }

  public static SSLEngine createSslEngine() {
    try {
      KeyStore keyStore = KeyStore.getInstance("JKS");
      keyStore.load(new FileInputStream("ssl/server-keystore.jks"), "123456".toCharArray());
      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
      keyManagerFactory.init(keyStore, "123456".toCharArray());

      SSLContext sslContext = SSLContext.getInstance("TLSv1.1");
      sslContext.init(keyManagerFactory.getKeyManagers(), new TrustManager[]{new ServerTrustManager()}, null);
      SSLEngine sslEngine = sslContext.createSSLEngine();
      sslEngine.setNeedClientAuth(true);
      sslEngine.setUseClientMode(false);
      return sslEngine;
    } catch(Exception e) {
      throw new RemoteRunException("Failed to create server SSLEngine", e);
    }
  }

  private static void defaultSystemProperty(String property, String value) {
    if(!System.getProperties().containsKey(property)) {
      System.setProperty(property, value);
    }
  }
}
