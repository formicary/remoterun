package com.twock.remoterun.client.process;

import java.nio.ByteBuffer;

import com.twock.remoterun.common.proto.RemoteRun;

/**
 * @author Chris Pearson
 */
public interface ReadCallback {
  void dataAvailable(ByteBuffer buffer, long serverId, RemoteRun.ClientToServer.MessageType type);

  void finished(long serverId);
}
