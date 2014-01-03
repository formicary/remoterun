package com.twock.remoterun.agent.process;

import java.io.InputStream;
import java.nio.ByteBuffer;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.twock.remoterun.common.proto.RemoteRun.AgentToMaster.MessageType;

/**
 * @author Chris Pearson
 */
public class OutputReader extends Thread {
  private static final Logger log = LoggerFactory.getLogger(OutputReader.class);
  private static final int BUFFER_SIZE = 1048576;
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
      IOUtils.closeQuietly(stream);
      callback.finished(serverId);
    }
  }
}
