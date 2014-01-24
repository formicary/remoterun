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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import net.formicary.remoterun.common.proto.RemoteRun;

/**
 * @author Chris Pearson
 */
public class ProcessHelper {
  private final long serverId;
  private final Process process;
  private final OutputReader stdout;
  private final OutputReader stderr;
  private final InputWriter stdin;
  private final ReadCallback callback;
  private boolean finished = false;

  public ProcessHelper(long serverId, String cmd, List<String> argsList, ReadCallback callback) throws IOException {
    this.serverId = serverId;
    this.callback = callback;

    List<String> command = new ArrayList<String>(argsList.size());
    command.add(cmd);
    command.addAll(argsList);
    String[] cmdArray = command.toArray(new String[command.size()]);

    process = Runtime.getRuntime().exec(cmdArray);
    ReadCallback wrapper = new ReadCallback() {
      @Override
      public void dataAvailable(ByteBuffer buffer, long serverId, RemoteRun.AgentToMaster.MessageType type) {
        ProcessHelper.this.callback.dataAvailable(buffer, serverId, type);
      }

      @Override
      public synchronized void finished(long serverId) {
        if(!finished && stdout.isFinished() && stderr.isFinished()) {
          finished = true;
          stdin.shutdown();
          ProcessHelper.this.callback.finished(serverId);
        }
      }
    };
    stdout = new OutputReader(process.getInputStream(), serverId, RemoteRun.AgentToMaster.MessageType.STDOUT_FRAGMENT, wrapper);
    stderr = new OutputReader(process.getErrorStream(), serverId, RemoteRun.AgentToMaster.MessageType.STDERR_FRAGMENT, wrapper);
    stdin = new InputWriter(process.getOutputStream(), serverId);
  }

  public void start() {
    stdout.start();
    stderr.start();
    stdin.start();
  }

  public long getServerId() {
    return serverId;
  }

  public Process getProcess() {
    return process;
  }

  public boolean isFinished() {
    return finished;
  }

  public void writeStdIn(byte[] data) {
    stdin.write(data);
  }

  public void closeStdIn() {
    stdin.shutdown();
  }
}
