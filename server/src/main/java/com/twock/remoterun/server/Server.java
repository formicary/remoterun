package com.twock.remoterun.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import com.twock.remoterun.common.RemoteRunException;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang.text.StrTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.twock.remoterun.common.proto.RemoteRun.ServerToClient;

/**
 * @author Chris Pearson
 */
public class Server implements NettyServer.ServerConnectionCallback {
  private static final Logger log = LoggerFactory.getLogger(Server.class);
  private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();
  private NettyServer nettyServer;

  public static void main(String[] args) {
    new Server().run();
  }

  public void run() {
    Executor bossExecutor = Executors.newCachedThreadPool();
    Executor workerExecutor = Executors.newCachedThreadPool();
    nettyServer = new NettyServer(bossExecutor, workerExecutor, this);
    InetSocketAddress bindAddress = new InetSocketAddress(1081);
    nettyServer.bind(bindAddress);

    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, Charsets.UTF_8));
      String line;
      while((line = reader.readLine()) != null) {
        try {
          if(line.startsWith("l")) {
            listClientConnections();
          } else if(line.startsWith("r")) {
            runCommand(line);
          } else {
            log.warn("Unhandled command: " + line);
          }
        } catch(Exception e) {
          log.error("Failed to process command: " + line, e);
        }
      }
    } catch(Exception e) {
      throw new RemoteRunException("Failed whilst processing user input, shutting down", e);
    } finally {
      nettyServer.shutdown();
    }
  }

  private void listClientConnections() {
    Set<ClientConnection> clientConnections = nettyServer.getClientConnections();
    int count = 0;
    for(ClientConnection clientConnection : clientConnections) {
      log.info((++count) + ": " + clientConnection.getConnectionState().name() + " " + clientConnection.getChannel().getRemoteAddress());
    }
    log.info("Finished listing " + clientConnections.size() + " client connections");
  }

  private void runCommand(String line) {
    StrTokenizer tokenizer = new StrTokenizer(line, ' ', '"');
    @SuppressWarnings("unchecked")
    List<String> tokens = tokenizer.getTokenList();
    tokens.remove(0); // first token is the run command
    String command = tokens.remove(0);

    ServerToClient.Builder builder = ServerToClient.newBuilder().setMessageType(ServerToClient.MessageType.RUN_COMMAND);
    builder.getRunCommandBuilder().setRequestId(NEXT_REQUEST_ID.incrementAndGet()).setCmd(command).addAllArgs(tokens);
    ServerToClient message = builder.build();

    Collection<ClientConnection> connectedClients = nettyServer.getConnectedClients();
    if(connectedClients.isEmpty()) {
      log.error("Unable to send command: no client connections");
    } else {
      ClientConnection connection = connectedClients.iterator().next();
      connection.getChannel().write(message);
    }
  }

  @Override
  public void clientConnected(ClientConnection clientConnection) {

  }

  @Override
  public void clientDisconnected(ClientConnection clientConnection) {

  }
}
