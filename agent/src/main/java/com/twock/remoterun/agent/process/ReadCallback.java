package com.twock.remoterun.agent.process;

import java.nio.ByteBuffer;

import com.twock.remoterun.common.proto.RemoteRun;

/**
 * @author Chris Pearson
 */
public interface ReadCallback {
  void dataAvailable(ByteBuffer buffer, long serverId, RemoteRun.AgentToMaster.MessageType type);

  void finished(long serverId);
}
