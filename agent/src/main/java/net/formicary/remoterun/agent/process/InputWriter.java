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

package net.formicary.remoterun.agent.process;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.LinkedBlockingQueue;

import net.formicary.remoterun.common.IoUtils;
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
    IoUtils.closeQuietly(stream);
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
