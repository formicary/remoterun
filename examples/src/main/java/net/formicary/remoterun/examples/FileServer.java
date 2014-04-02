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
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import net.formicary.remoterun.common.proto.RemoteRun;
import net.formicary.remoterun.embed.AgentConnection;
import net.formicary.remoterun.embed.AgentConnectionCallback;
import net.formicary.remoterun.embed.RemoteRunMaster;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.formicary.remoterun.common.proto.RemoteRun.AgentToMaster.MessageType.REQUESTED_DATA;
import static net.formicary.remoterun.common.proto.RemoteRun.MasterToAgent;
import static net.formicary.remoterun.common.proto.RemoteRun.MasterToAgent.MessageType.REQUEST_DATA;
import static net.formicary.remoterun.common.proto.RemoteRun.MasterToAgent.RequestData;

/**
 * @author Chris Pearson
 */
public class FileServer implements AgentConnectionCallback {
  private static final String DEMO_REQUEST_PATH = "/var/tmp/test";
  private static final Logger log = LoggerFactory.getLogger(FileServer.class);
  private static final int PORT = 1222;
  private final AtomicLong atomicLong = new AtomicLong();
  private final Map<Long, DataRequest> dataRequests = new TreeMap<Long, DataRequest>();

  public static void main(String[] args) {
    new FileServer().run();
  }

  private void run() {
    new RemoteRunMaster(Executors.newFixedThreadPool(10), Executors.newFixedThreadPool(10), this).bind(new InetSocketAddress(PORT));
  }

  @Override
  public void agentConnected(AgentConnection agentConnection) {
    long requestId = atomicLong.incrementAndGet();
    DataRequest dataRequest = null;
    try {
      dataRequest = new DataRequest(requestId);
      dataRequests.put(requestId, dataRequest);
      agentConnection.getChannel().write(MasterToAgent.newBuilder()
        .setRequestId(requestId)
        .setMessageType(REQUEST_DATA)
        .setRequestData(RequestData.newBuilder().setFullPath(DEMO_REQUEST_PATH))
        .build()
      );
    } catch(Exception e) {
      if(dataRequest != null) {
        dataRequests.remove(requestId);
        dataRequest.close();
      }
      throw new RuntimeException("Failed to process agent connection", e);
    }
  }

  @Override
  public void messageReceived(AgentConnection agentConnection, RemoteRun.AgentToMaster message) throws Exception {
    if(REQUESTED_DATA.equals(message.getMessageType())) {
      DataRequest dataRequest = dataRequests.get(message.getRequestId());
      if(message.hasExitCode()) {
        dataRequest.close();
        log.info("Written {}", dataRequest.path);
      } else {
        message.getFragment().writeTo(dataRequest.outputStream);
      }
    }
  }

  @Override
  public void agentDisconnected(AgentConnection agentConnection) {

  }

  public static class DataRequest {
    private long id;
    private Path path;
    private OutputStream outputStream;

    public DataRequest(long id) throws IOException {
      this.id = id;
      this.path = Files.createTempFile("FileServer_Request" + id + "_", ".tmp");
      this.outputStream = new BufferedOutputStream(Files.newOutputStream(path));
    }

    public void close() {
      IOUtils.closeQuietly(outputStream);
    }
  }
}
