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

import java.net.InetSocketAddress;

import net.formicary.remoterun.common.proto.RemoteRun;
import net.formicary.remoterun.embed.IAgentConnection;
import net.formicary.remoterun.embed.RemoteRunMaster;
import net.formicary.remoterun.embed.callback.AbstractAgentConnectionCallback;
import net.formicary.remoterun.embed.callback.AbstractTextOutputCallback;
import net.formicary.remoterun.embed.request.TextOutputRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.formicary.remoterun.common.proto.RemoteRun.MasterToAgent.MessageType.RUN_COMMAND;
import static net.formicary.remoterun.embed.request.MessageHelper.runCommand;

/**
 * Simple demonstration of the TextOutputRequest class.
 * Starts a server that causes any agents to echo hello world, then quits after 15 seconds, again making the agents
 * execute a command.
 *
 * @author Chris Pearson
 */
public class SimpleRemoteRunMaster extends AbstractAgentConnectionCallback {
  private static final Logger log = LoggerFactory.getLogger(SimpleRemoteRunMaster.class);

  public static void main(String[] args) {
    new SimpleRemoteRunMaster().run();
  }

  private void run() {
    // initialise remoterun
    RemoteRunMaster master = new RemoteRunMaster(this);
    master.bind(new InetSocketAddress(1081));
    // wait 15 seconds
    try {
      Thread.sleep(15000);
    } catch(InterruptedException ignored) {
    }
    // run a command on all connected clients
    for(IAgentConnection connection : master.getConnectedClients()) {
      connection.write(RemoteRun.MasterToAgent.newBuilder()
        .setMessageType(RUN_COMMAND)
        .setRequestId(RemoteRunMaster.getNextRequestId())
        .setRunCommand(RemoteRun.MasterToAgent.RunCommand.newBuilder().setCmd("echo").addArgs("Thanks for connecting!"))
        .build());
    }
    // wait 1 second
    try {
      Thread.sleep(1000);
    } catch(InterruptedException ignored) {
    }
    log.info("Shutting down SimpleRemoteRunMaster");
    master.shutdown();
  }

  @Override
  public void agentConnected(final IAgentConnection agentConnection) {
    // as soon as an agent connects, run a command
    agentConnection.request(new TextOutputRequest(runCommand("echo", "Hello World!"), new AbstractTextOutputCallback() {
      // nothing overridden because we don't actually want to do anything with the command - pretty unusual, we'd
      // normally want to check at least the exit code to check if it succeeded or failed
    }));
  }
}
