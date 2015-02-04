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

package net.formicary.remoterun.examples;

import java.net.InetSocketAddress;

import net.formicary.remoterun.common.proto.RemoteRun;
import net.formicary.remoterun.embed.AgentConnection;
import net.formicary.remoterun.embed.RemoteRunMaster;
import net.formicary.remoterun.embed.simple.AgentCallback;
import net.formicary.remoterun.embed.simple.AgentStateFactory;
import net.formicary.remoterun.embed.simple.SimpleRemoteRun;

import static net.formicary.remoterun.common.proto.RemoteRun.AgentToMaster.MessageType.AGENT_INFO;
import static net.formicary.remoterun.common.proto.RemoteRun.MasterToAgent.MessageType.RUN_COMMAND;

/**
 * Simple demonstration of the SimpleRemoteRun class.
 * Starts a server that causes any agents to echo hello world, then quits after 15 seconds, again making the agents
 * execute a command.
 *
 * @author Chris Pearson
 */
public class SimpleRemoteRunClient implements AgentCallback {
  private final AgentConnection connection;

  public SimpleRemoteRunClient(AgentConnection connection) {
    this.connection = connection;
  }

  public static void main(String[] args) {
    // initialise remoterun
    SimpleRemoteRun<SimpleRemoteRunClient> simpleRemoteRun = SimpleRemoteRun.start(new InetSocketAddress(1081), new AgentStateFactory<SimpleRemoteRunClient>() {
      @Override
      public SimpleRemoteRunClient newConnection(AgentConnection connection) {
        return new SimpleRemoteRunClient(connection);
      }
    });
    // wait 15 seconds, then send a message to all connected agents
    try {
      Thread.sleep(15000);
    } catch(InterruptedException ignored) {
    }
    for(AgentConnection connection : simpleRemoteRun.getAgents().keySet()) {
      connection.write(RemoteRun.MasterToAgent.newBuilder()
        .setMessageType(RUN_COMMAND)
        .setRequestId(RemoteRunMaster.getNextRequestId())
        .setRunCommand(RemoteRun.MasterToAgent.RunCommand.newBuilder().setCmd("echo").addArgs("Thanks for connecting!"))
        .build());
    }
    // now quit
    simpleRemoteRun.shutdown();
  }

  @Override
  public void messageReceived(AgentConnection agentConnection, RemoteRun.AgentToMaster message) throws Exception {
    if(message.getMessageType() == AGENT_INFO) {
      agentConnection.write(RemoteRun.MasterToAgent.newBuilder()
        .setMessageType(RUN_COMMAND)
        .setRequestId(RemoteRunMaster.getNextRequestId())
        .setRunCommand(RemoteRun.MasterToAgent.RunCommand.newBuilder().setCmd("echo").addArgs("Hello World!"))
        .build());
    }
  }

  @Override
  public void close(AgentConnection agentConnection) throws Exception {
  }
}
