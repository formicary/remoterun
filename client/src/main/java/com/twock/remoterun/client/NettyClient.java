package com.twock.remoterun.client;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Executor;
import javax.net.ssl.*;

import com.google.protobuf.ByteString;
import com.twock.remoterun.client.process.ProcessHelper;
import com.twock.remoterun.client.process.ReadCallback;
import com.twock.remoterun.common.KeyStoreUtil;
import com.twock.remoterun.common.NettyLoggingHandler;
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

import static com.twock.remoterun.common.proto.RemoteRun.ClientToServer.MessageType;
import static com.twock.remoterun.common.proto.RemoteRun.ClientToServer.MessageType.*;

/**
 * @author Chris Pearson
 */
public class NettyClient extends SimpleChannelHandler implements ChannelFutureListener, ReadCallback {
  private static final Logger log = LoggerFactory.getLogger(NettyClient.class);
  private static final int RECONNECT_DELAY = 10000;
  private final InetSocketAddress address;
  private final Map<Long, ProcessHelper> processes = Collections.synchronizedMap(new TreeMap<Long, ProcessHelper>());
  private final ClientBootstrap bootstrap;
  private boolean shutdown = false;
  private Channel channel;
  private ChannelFuture handshakeFuture;
  private Timer timer;
  private ChannelFuture lastWriteFuture;

  public NettyClient(InetSocketAddress address, Executor bossExecutor, Executor workerExecutor) {
    this.address = address;
    bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(bossExecutor, workerExecutor));
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          new SslHandler(createSslEngine()),

          new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4),
          new LengthFieldPrepender(4),

          new ProtobufDecoder(RemoteRun.ServerToClient.getDefaultInstance()),
          new ProtobufEncoder(),

          new NettyLoggingHandler(),
          NettyClient.this
        );
      }
    });
    bootstrap.setOption("tcpNoDelay", true);
    bootstrap.setOption("keepAlive", true);
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
      log.info("Client SSL handshake completed, connected to " + future.getChannel().getRemoteAddress());
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
    RemoteRun.ServerToClient message = (RemoteRun.ServerToClient)e.getMessage();
    switch(message.getMessageType()) {
      case RUN_COMMAND:
        RemoteRun.ServerToClient.RunCommand runCommand = message.getRunCommand();
        long requestId = runCommand.getRequestId();
        try {
          // start the process
          ProcessHelper processHelper = new ProcessHelper(requestId, runCommand.getCmd(), runCommand.getArgsList(), this);
          processes.put(requestId, processHelper);
          // write a success reply
          write(RemoteRun.ClientToServer.newBuilder().setMessageType(STARTED).setRequestId(requestId).build());
          processHelper.startReadingOutput();
        } catch(Exception e1) {
          log.info("Failed to start process " + runCommand, e1);
          // write a failure reply
          write(RemoteRun.ClientToServer.newBuilder().setMessageType(EXITED)
            .setRequestId(requestId).setExitCode(-1)
            .setExitReason(e1.getClass().getName() + ": " + e1.getMessage())
            .build());
        }
    }
    ctx.sendUpstream(e);
  }

  private void write(RemoteRun.ClientToServer message) {
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
  public void dataAvailable(ByteBuffer buffer, long serverId, MessageType type) {
    waitUntilWritable();
    write(RemoteRun.ClientToServer.newBuilder().setMessageType(type)
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
  public void finished(long serverId, MessageType type) {
    ProcessHelper processHelper = processes.get(serverId);
    if(processHelper != null && processHelper.isFinished()) {
      waitUntilWritable();
      ProcessHelper process = processes.remove(serverId);
      if(process != null) {
        write(RemoteRun.ClientToServer.newBuilder().setMessageType(EXITED)
          .setRequestId(serverId)
          .setExitCode(processHelper.getProcess().exitValue()).build());
      }
    }
  }
}
