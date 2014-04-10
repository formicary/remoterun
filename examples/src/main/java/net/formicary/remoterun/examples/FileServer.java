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

import java.io.BufferedOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import net.formicary.remoterun.common.FileStreamer;
import net.formicary.remoterun.common.proto.RemoteRun;
import net.formicary.remoterun.embed.AgentConnection;
import net.formicary.remoterun.embed.AgentConnectionCallback;
import net.formicary.remoterun.embed.RemoteRunMaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.formicary.remoterun.common.proto.RemoteRun.AgentToMaster.MessageType.*;
import static net.formicary.remoterun.common.proto.RemoteRun.MasterToAgent;
import static net.formicary.remoterun.common.proto.RemoteRun.MasterToAgent.MessageType.REQUEST_DATA;

/**
 * Demonstrate sending and receiving data to/from the agent.
 *
 * @author Chris Pearson
 */
public class FileServer implements AgentConnectionCallback {
  private static final String DEMO_REQUEST_PATH = "/var/tmp/test";
  private static final Logger log = LoggerFactory.getLogger(FileServer.class);
  private static final int PORT = 1222;
  private final AtomicLong atomicLong = new AtomicLong();
  private final Map<Long, FileStreamer> dataRequests = new TreeMap<>();
  private long uploadId;
  private BufferedOutputStream outputStream;
  private Path tempFile;

  public static void main(String[] args) {
    new FileServer().run();
  }

  private void run() {
    new RemoteRunMaster(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), this).bind(new InetSocketAddress(PORT));
  }

  @Override
  public void agentConnected(final AgentConnection agentConnection) {
    // sending a file
    uploadId = agentConnection.upload(Paths.get("/var/tmp/test"), "/var/tmp/system.log", null);
  }

  @Override
  public void messageReceived(AgentConnection agentConnection, RemoteRun.AgentToMaster message) throws Exception {
    if(message.getMessageType() == RECEIVED_DATA && message.getRequestId() == uploadId) {
      log.info("Completed receipt of system.log, re-downloading...");
      // now we've sent a file to the agent, re-download

      // how to initiate a receive from the agent
      agentConnection.write(MasterToAgent.newBuilder()
        .setRequestId(RemoteRunMaster.getNextRequestId())
        .setMessageType(REQUEST_DATA)
        .setPath("/var/tmp/system.log")
        .build());
      tempFile = Files.createTempFile("received_", ".zip");
      outputStream = new BufferedOutputStream(Files.newOutputStream(tempFile));

    } else if(message.getMessageType() == REQUESTED_DATA) {
      // receiving a file/finishing
      if(message.hasExitCode()) {
        outputStream.close();
        log.info("Written zip {}", tempFile);
      } else {
        message.getFragment().writeTo(outputStream);
      }
    }
  }

  @Override
  public void agentDisconnected(AgentConnection agentConnection) {
  }
}
