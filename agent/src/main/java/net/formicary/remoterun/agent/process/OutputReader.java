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

package net.formicary.remoterun.agent.process;

import java.io.InputStream;
import java.nio.ByteBuffer;

import net.formicary.remoterun.common.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.formicary.remoterun.common.proto.RemoteRun.AgentToMaster.MessageType;

/**
 * @author Chris Pearson
 */
public class OutputReader extends Thread {
  private static final Logger log = LoggerFactory.getLogger(OutputReader.class);
  private static final int BUFFER_SIZE = 1047552;
  private final ByteBuffer buffer = ByteBuffer.wrap(new byte[BUFFER_SIZE]);
  private final InputStream stream;
  private final long serverId;
  private final MessageType type;
  private final ReadCallback callback;
  private boolean finished = false;
  private Throwable error;

  public OutputReader(InputStream stream, long serverId, MessageType type, ReadCallback callback) {
    this.stream = stream;
    this.serverId = serverId;
    this.type = type;
    this.callback = callback;
    setName("Process " + serverId + " " + type.name().substring(0, 6) + " reader");
  }

  public long getServerId() {
    return serverId;
  }

  public MessageType getType() {
    return type;
  }

  public boolean isFinished() {
    return finished;
  }

  public void setFinished(boolean finished) {
    this.finished = finished;
  }

  public Throwable getError() {
    return error;
  }

  @Override
  public void run() {
    int bytesRead;
    try {
      while(!finished && (bytesRead = stream.read(buffer.array(), buffer.position(), buffer.remaining())) != -1) {
        buffer.position(buffer.position() + bytesRead);
        if(buffer.position() > 0) {
          buffer.flip();
          callback.dataAvailable(buffer, serverId, type);
          buffer.compact();
        }
      }
      finished = true;
    } catch(Exception e) {
      finished = true;
      error = e;
      log.warn("Failed whilst reading stream", e);
    } finally {
      IoUtils.closeQuietly(stream);
      callback.finished(serverId);
    }
  }
}
