package com.twock.remoterun.client.process;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Chris Pearson
 */
public class InputWriter extends Thread {
  private static final Logger log = LoggerFactory.getLogger(InputWriter.class);
  private final LinkedBlockingQueue<byte[]> toWrite = new LinkedBlockingQueue<>();
  private final OutputStream stream;
  private boolean finished = false;

  public InputWriter(OutputStream stream, long serverId) {
    this.stream = stream;
    setName("Process " + serverId + " STDIN writer");
  }

  @Override
  public void run() {
    while(!finished) {
      try {
        byte[] next = toWrite.take();
        stream.write(next);
        stream.flush();
      } catch(InterruptedException ignored) {
      } catch(IOException e) {
        log.warn("Failed to write to stream, ignoring", e);
      }
    }
    IOUtils.closeQuietly(stream);
  }

  public void write(byte[] data) {
    while(true) {
      try {
        toWrite.put(data);
        return;
      } catch(InterruptedException ignored) {
      }
    }
  }

  public void shutdown() {
    finished = true;
    interrupt();
  }
}
