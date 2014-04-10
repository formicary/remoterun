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

package net.formicary.remoterun.embed;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import com.google.protobuf.ByteString;
import net.formicary.remoterun.common.FileStreamer;
import net.formicary.remoterun.common.proto.RemoteRun;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.formicary.remoterun.common.proto.RemoteRun.MasterToAgent.MessageType.*;

/**
 * @author Chris Pearson
 */
public class AgentConnection {
  private static final Logger log = LoggerFactory.getLogger(AgentConnection.class);
  private final ReentrantLock writeLock = new ReentrantLock(true);
  private ChannelFuture lastWriteFuture;
  private Channel channel;
  private ConnectionState connectionState;

  public AgentConnection(Channel channel) {
    this.channel = channel;
    this.connectionState = ConnectionState.HANDSHAKING;
  }

  public Channel getChannel() {
    return channel;
  }

  public void setChannel(Channel channel) {
    this.channel = channel;
  }

  public ConnectionState getConnectionState() {
    return connectionState;
  }

  public void setConnectionState(ConnectionState connectionState) {
    this.connectionState = connectionState;
  }

  public void shutdown() {
    channel.close();
  }

  /**
   * Initiate the upload of a file from master to agent.
   *
   * @param sourcePath path to read and send on this host
   * @param targetPath where to try and store the data on the target
   * @param callback callback when the send is complete, can be null
   * @return unique request ID
   */
  public long upload(Path sourcePath, final String targetPath, final UploadCompleteCallback callback) {
    final long requestId = RemoteRunMaster.getNextRequestId();
    write(RemoteRun.MasterToAgent.newBuilder()
      .setRequestId(requestId)
      .setMessageType(SEND_DATA_NOTIFICATION)
      .setPath(targetPath)
      .build());
    new FileStreamer(sourcePath, new FileStreamer.FileStreamerCallback() {
      @Override
      public void writeDataChunk(byte[] data, int offset, int length) {
        write(RemoteRun.MasterToAgent.newBuilder()
          .setRequestId(requestId)
          .setMessageType(SEND_DATA_FRAGMENT)
          .setFragment(ByteString.copyFrom(data, offset, length))
          .build());
      }

      @Override
      public void finished(boolean success, String errorMessage, Throwable cause) {
        if(!success) {
          log.error("Failed to send data to " + getChannel().getRemoteAddress() + " - targetPath=" + targetPath, cause);
        }
        write(RemoteRun.MasterToAgent.newBuilder()
          .setRequestId(requestId)
          .setMessageType(SEND_DATA_FRAGMENT)
          .setDataSuccess(success)
          .build());
        if (callback != null) {
          callback.uploadComplete(AgentConnection.this, requestId, targetPath, success);
        }
      }
    }).run();
    return requestId;
  }

  @Override
  public String toString() {
    return "AgentConnection{" +
      "channel=" + channel +
      ", connectionState=" + connectionState +
      '}';
  }

  public void write(RemoteRun.MasterToAgent message) {
    // fair write lock to ensure every thread gets a chance to write data, and avoid a single thread hogging the writes
    // if write buffer is full, only one thread will be within the write/waitUntilWriteable call
    // Given that data is written a chunk at a time, the fair lock should ensure everything gets a look in
    writeLock.lock();
    try {
      if(lastWriteFuture != null) {
        try {
          lastWriteFuture.await();
        } catch(InterruptedException e) {
        }
      }
      lastWriteFuture = channel.write(message);
    } finally {
      writeLock.unlock();
    }
  }

  public static interface UploadCompleteCallback {
    void uploadComplete(AgentConnection agent, long requestId, String targetPath, boolean success);
  }
}
