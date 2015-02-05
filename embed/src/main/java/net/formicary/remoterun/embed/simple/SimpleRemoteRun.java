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

package net.formicary.remoterun.embed.simple;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.formicary.remoterun.common.proto.RemoteRun;
import net.formicary.remoterun.embed.AgentConnection;
import net.formicary.remoterun.embed.AgentConnectionCallback;
import net.formicary.remoterun.embed.RemoteRunMaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Chris Pearson
 */
public class SimpleRemoteRun<A extends AgentCallback> implements AgentConnectionCallback {
  private static final Logger log = LoggerFactory.getLogger(SimpleRemoteRun.class);
  private final Map<AgentConnection, A> agents = new HashMap<>();
  private final AgentStateFactory<A> connectionStateFactory;
  private final ExecutorService executorService;
  private RemoteRunMaster master;

  public SimpleRemoteRun(AgentStateFactory<A> connectionStateFactory, ExecutorService executorService) {
    this.connectionStateFactory = connectionStateFactory;
    this.executorService = executorService;
  }

  /**
   * Start a remote run server listening on the given address, and for each connection create a callback using the
   * provided factory.
   *
   * @param bindAddress local IP address and/or port that remoterun should listen on
   * @param connectionStateFactory factory to use for callback creation
   * @return the SimpleAgentContainer representing the newly created remote run master and holding established callbacks
   */
  public static <A extends AgentCallback> SimpleRemoteRun<A> start(InetSocketAddress bindAddress, final AgentStateFactory<A> connectionStateFactory) {
    ExecutorService executorService = Executors.newCachedThreadPool();
    SimpleRemoteRun<A> agentContainer = new SimpleRemoteRun<>(connectionStateFactory, executorService);
    RemoteRunMaster remoteRunMaster = new RemoteRunMaster(executorService, executorService, agentContainer);
    agentContainer.setMaster(remoteRunMaster);
    remoteRunMaster.bind(bindAddress);
    return agentContainer;
  }

  public RemoteRunMaster getMaster() {
    return master;
  }

  public void setMaster(RemoteRunMaster master) {
    this.master = master;
  }

  public Map<AgentConnection, A> getAgents() {
    return agents;
  }

  @Override
  public void agentConnected(AgentConnection agentConnection) {
    agents.put(agentConnection, connectionStateFactory.newConnection(agentConnection));
  }

  @Override
  public void messageReceived(AgentConnection agentConnection, RemoteRun.AgentToMaster message) throws Exception {
    A agentCallback = agents.get(agentConnection);
    if(agentCallback != null) {
      agentCallback.messageReceived(agentConnection, message);
    }
  }

  @Override
  public void agentDisconnected(AgentConnection agentConnection) {
    A agentCallback = agents.get(agentConnection);
    if(agentCallback != null) {
      try {
        agentCallback.close(agentConnection);
      } catch(Exception e) {
        log.warn("Ignoring error closing agent callback: {}", e);
      }
    }
  }

  public void shutdown() {
    master.shutdown();
    executorService.shutdown();
  }
}
