package com.twock.remoterun.embed;

/**
 * @author Chris Pearson
 */
public interface AgentConnectionCallback {
  void agentConnected(AgentConnection agentConnection);

  void agentDisconnected(AgentConnection agentConnection);
}
