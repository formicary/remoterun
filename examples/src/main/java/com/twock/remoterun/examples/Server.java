package com.twock.remoterun.examples;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import com.google.protobuf.ByteString;
import com.twock.remoterun.common.RemoteRunException;
import com.twock.remoterun.embed.AgentConnection;
import com.twock.remoterun.embed.AgentConnectionCallback;
import com.twock.remoterun.embed.RemoteRunMaster;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.twock.remoterun.common.proto.RemoteRun.MasterToAgent;
import static com.twock.remoterun.common.proto.RemoteRun.MasterToAgent.MessageType.*;

/**
 * A dummy server to illustrate the usage of NettyServer.  To run the examples you first need to generate certificates
 * by running ssl/certs.sh.<p/>
 * The commands available are:
 * <ul>
 * <li>l - list connections</li>
 * <li>r &lt;cmd&gt; [arg [arg [...]]] - run a remote command</li>
 * <li>i &lt;requestId&gt; &lt;stdin text&gt; - send input to stdin of process</li>
 * <li>c &lt;requestId&gt; - close stdin of process</li>
 * </ul>
 * <pre>
 * 21:56:09.607 [main] INFO  c.twock.remoterun.server.NettyServer - Listening for connections on 0.0.0.0/0.0.0.0:1081
 * 21:56:17.525 [New I/O worker #1] INFO  c.twock.remoterun.server.NettyServer - Client connected from /127.0.0.1:50495 (1 open connections)
 * 21:56:17.742 [New I/O worker #1] INFO  c.twock.remoterun.server.NettyServer - Client connection complete from /127.0.0.1:50495 (CN=client1, O=Twock, L=Potters Bar, ST=Hertfordshire, C=GB)
 * l
 * 21:56:23.800 [main] INFO  com.twock.remoterun.server.Server - 1: CONNECTED /127.0.0.1:50495
 * 21:56:23.800 [main] INFO  com.twock.remoterun.server.Server - Finished listing 1 agent connections
 * r cat
 * 21:56:27.768 [main] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] WRITE: messageType: RUN_COMMAND requestId: 1 runCommand {   cmd: "cat" }
 * 21:56:27.899 [New I/O worker #1] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] RECEIVED: messageType: STARTED requestId: 1
 * i 1 hello\n
 * 21:56:40.697 [main] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] WRITE: messageType: STDIN_FRAGMENT requestId: 1 stdinFragment: "hello\n"
 * 21:56:40.701 [New I/O worker #1] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] RECEIVED: messageType: STDOUT_FRAGMENT requestId: 1 fragment: "hello\n"
 * c 1
 * 21:56:46.610 [main] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] WRITE: messageType: CLOSE_STDIN requestId: 1
 * 21:56:46.614 [New I/O worker #1] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] RECEIVED: messageType: EXITED requestId: 1 exitCode: 0
 * </pre>
 *
 * @author Chris Pearson
 */
public class Server implements AgentConnectionCallback {
  private static final Logger log = LoggerFactory.getLogger(Server.class);
  private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();
  private RemoteRunMaster remoteRunMaster;

  public static void main(String[] args) {
    new Server().run();
  }

  public void run() {
    Executor bossExecutor = Executors.newCachedThreadPool();
    Executor workerExecutor = Executors.newCachedThreadPool();
    remoteRunMaster = new RemoteRunMaster(bossExecutor, workerExecutor, this);
    InetSocketAddress bindAddress = new InetSocketAddress(1081);
    remoteRunMaster.bind(bindAddress);

    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, Charsets.UTF_8));
      String line;
      while((line = reader.readLine()) != null) {
        try {
          if(line.startsWith("l")) {
            listClientConnections();
          } else if(line.startsWith("r")) {
            runCommand(line);
          } else if(line.startsWith("i")) {
            sendInput(line);
          } else if(line.startsWith("c")) {
            closeInput(line);
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
      remoteRunMaster.shutdown();
    }
  }

  private void listClientConnections() {
    Set<AgentConnection> agentConnections = remoteRunMaster.getAgentConnections();
    int count = 0;
    for(AgentConnection agentConnection : agentConnections) {
      log.info((++count) + ": " + agentConnection.getConnectionState().name() + " " + agentConnection.getChannel().getRemoteAddress());
    }
    log.info("Finished listing " + agentConnections.size() + " agent connections");
  }

  private void runCommand(String line) {
    StrTokenizer tokenizer = new StrTokenizer(line, ' ', '"');
    @SuppressWarnings("unchecked")
    List<String> tokens = tokenizer.getTokenList();
    tokens.remove(0); // first token is the run command
    String command = tokens.remove(0);

    Collection<AgentConnection> connectedClients = remoteRunMaster.getConnectedClients();
    if(connectedClients.isEmpty()) {
      log.error("Unable to send command: no agent connections");
    } else {
      AgentConnection connection = connectedClients.iterator().next();

      MasterToAgent.Builder builder = MasterToAgent.newBuilder()
        .setMessageType(RUN_COMMAND)
        .setRequestId(NEXT_REQUEST_ID.incrementAndGet());
      builder.getRunCommandBuilder().setCmd(command).addAllArgs(tokens);
      connection.getChannel().write(builder.build());
    }
  }

  private void sendInput(String line) {
    StrTokenizer tokenizer = new StrTokenizer(line, ' ', '"');
    @SuppressWarnings("unchecked")
    List<String> tokens = tokenizer.getTokenList();
    tokens.remove(0); // first token is the run command
    long id = Long.parseLong(tokens.remove(0));
    String input = StringUtils.join(tokens, ' ').replaceAll("\\\\n", "\n");

    Collection<AgentConnection> connectedClients = remoteRunMaster.getConnectedClients();
    if(connectedClients.isEmpty()) {
      log.error("Unable to send command: no agent connections");
    } else {
      AgentConnection connection = connectedClients.iterator().next();
      MasterToAgent.Builder builder = MasterToAgent.newBuilder()
        .setMessageType(STDIN_FRAGMENT)
        .setRequestId(id)
        .setStdinFragment(ByteString.copyFromUtf8(input));
      connection.getChannel().write(builder.build());
    }
  }

  private void closeInput(String line) {
    StrTokenizer tokenizer = new StrTokenizer(line, ' ', '"');
    @SuppressWarnings("unchecked")
    List<String> tokens = tokenizer.getTokenList();
    tokens.remove(0); // first token is the run command
    long id = Long.parseLong(tokens.remove(0));

    Collection<AgentConnection> connectedClients = remoteRunMaster.getConnectedClients();
    if(connectedClients.isEmpty()) {
      log.error("Unable to send command: no agent connections");
    } else {
      AgentConnection connection = connectedClients.iterator().next();
      MasterToAgent.Builder builder = MasterToAgent.newBuilder().setMessageType(CLOSE_STDIN).setRequestId(id);
      connection.getChannel().write(builder.build());
    }
  }

  @Override
  public void agentConnected(AgentConnection agentConnection) {

  }

  @Override
  public void agentDisconnected(AgentConnection agentConnection) {

  }
}
