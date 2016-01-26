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

package net.formicary.remoterun.examples;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import com.google.protobuf.ByteString;
import net.formicary.remoterun.common.RemoteRunException;
import net.formicary.remoterun.common.proto.RemoteRun;
import net.formicary.remoterun.embed.IAgentConnection;
import net.formicary.remoterun.embed.RemoteRunMaster;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrTokenizer;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * 21:56:09.607 [main] INFO  c.formicary.remoterun.server.NettyServer - Listening for connections on 0.0.0.0/0.0.0.0:1081
 * 21:56:17.525 [New I/O worker #1] INFO  c.formicary.remoterun.server.NettyServer - Client connected from /127.0.0.1:50495 (1 open connections)
 * 21:56:17.742 [New I/O worker #1] INFO  c.formicary.remoterun.server.NettyServer - Client connection complete from /127.0.0.1:50495 (CN=client1, O=Formicary Ltd, L=London, ST=Greater London, C=GB)
 * l
 * 21:56:23.800 [main] INFO  com.formicary.remoterun.server.Server - 1: CONNECTED /127.0.0.1:50495
 * 21:56:23.800 [main] INFO  com.formicary.remoterun.server.Server - Finished listing 1 agent connections
 * r cat
 * 21:56:27.768 [main] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] WRITE: messageType: RUN_COMMAND requestId: 1 runCommand {   cmd: "cat" }
 * 21:56:27.899 [New I/O worker #1] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] RECEIVED: messageType: STARTED requestId: 1
 * i 1 hello\n
 * 21:56:40.697 [main] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] WRITE: messageType: STDIN_FRAGMENT requestId: 1 stdinFragment: "hello\n"
 * 21:56:40.701 [New I/O worker #1] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] RECEIVED: messageType: STDOUT_FRAGMENT requestId: 1 fragment: "hello\n"
 * c 1
 * 21:56:46.610 [main] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] WRITE: messageType: CLOSE_STDIN requestId: 1
 * 21:56:46.614 [New I/O worker #1] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] RECEIVED: messageType: EXITED requestId: 1 exitCode: 0
 * </pre>
 *
 * @author Chris Pearson
 */
public class Server {
  private static final Logger log = LoggerFactory.getLogger(Server.class);
  private RemoteRunMaster remoteRunMaster;

  public static void main(String[] args) {
    new Server().run();
  }

  public void run() {
    remoteRunMaster = new RemoteRunMaster(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), null);
    InetSocketAddress bindAddress = new InetSocketAddress(1081);
    remoteRunMaster.bind(bindAddress);

    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, CharsetUtil.UTF_8));
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
    Set<IAgentConnection> agentConnections = remoteRunMaster.getAgentConnections();
    int count = 0;
    for(IAgentConnection agentConnection : agentConnections) {
      log.info((++count) + ": " + agentConnection.getConnectionState().name() + " " + agentConnection.getAgentInfo().getHostname());
    }
    log.info("Finished listing " + agentConnections.size() + " agent connections");
  }

  private void runCommand(String line) {
    StrTokenizer tokenizer = new StrTokenizer(line, ' ', '"');
    @SuppressWarnings("unchecked")
    List<String> tokens = tokenizer.getTokenList();
    tokens.remove(0); // first token is the run command
    String command = tokens.remove(0);

    Collection<IAgentConnection> connectedClients = remoteRunMaster.getConnectedClients();
    if(connectedClients.isEmpty()) {
      log.error("Unable to send command: no agent connections");
    } else {
      IAgentConnection connection = connectedClients.iterator().next();

      RemoteRun.MasterToAgent.Builder builder = RemoteRun.MasterToAgent.newBuilder()
        .setMessageType(RemoteRun.MasterToAgent.MessageType.RUN_COMMAND)
        .setRequestId(RemoteRunMaster.getNextRequestId());
      builder.getRunCommandBuilder().setCmd(command).addAllArgs(tokens);
      connection.write(builder.build());
    }
  }

  private void sendInput(String line) {
    StrTokenizer tokenizer = new StrTokenizer(line, ' ', '"');
    @SuppressWarnings("unchecked")
    List<String> tokens = tokenizer.getTokenList();
    tokens.remove(0); // first token is the run command
    long id = Long.parseLong(tokens.remove(0));
    String input = StringUtils.join(tokens, ' ').replaceAll("\\\\n", "\n");

    Collection<IAgentConnection> connectedClients = remoteRunMaster.getConnectedClients();
    if(connectedClients.isEmpty()) {
      log.error("Unable to send command: no agent connections");
    } else {
      IAgentConnection connection = connectedClients.iterator().next();
      RemoteRun.MasterToAgent.Builder builder = RemoteRun.MasterToAgent.newBuilder()
        .setMessageType(RemoteRun.MasterToAgent.MessageType.STDIN_FRAGMENT)
        .setRequestId(id)
        .setFragment(ByteString.copyFromUtf8(input));
      connection.write(builder.build());
    }
  }

  private void closeInput(String line) {
    StrTokenizer tokenizer = new StrTokenizer(line, ' ', '"');
    @SuppressWarnings("unchecked")
    List<String> tokens = tokenizer.getTokenList();
    tokens.remove(0); // first token is the run command
    long id = Long.parseLong(tokens.remove(0));

    Collection<IAgentConnection> connectedClients = remoteRunMaster.getConnectedClients();
    if(connectedClients.isEmpty()) {
      log.error("Unable to send command: no agent connections");
    } else {
      IAgentConnection connection = connectedClients.iterator().next();
      RemoteRun.MasterToAgent.Builder builder = RemoteRun.MasterToAgent.newBuilder().setMessageType(RemoteRun.MasterToAgent.MessageType.CLOSE_STDIN).setRequestId(id);
      connection.write(builder.build());
    }
  }
}
