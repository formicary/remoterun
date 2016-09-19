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

package net.formicary.remoterun.agent;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.google.protobuf.ByteString;
import net.formicary.remoterun.agent.process.ProcessHelper;
import net.formicary.remoterun.agent.process.ReadCallback;
import net.formicary.remoterun.common.proto.RemoteRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.formicary.remoterun.common.proto.RemoteRun.MasterToAgent.MessageType.RUN_COMMAND;

/**
 * @author Chris Pearson
 */
public class ProcessHandler implements ReadCallback {
  private static final Logger log = LoggerFactory.getLogger(ProcessHandler.class);
  private final Map<Long, ProcessHelper> processes = Collections.synchronizedMap(new TreeMap<Long, ProcessHelper>());
  private RemoteRunAgent messageWriter;

  public void handle(RemoteRun.MasterToAgent message, RemoteRunAgent messageWriter) {
    this.messageWriter = messageWriter;
    long requestId = message.getRequestId();
    if(RUN_COMMAND == message.getMessageType()) {
      RemoteRun.MasterToAgent.RunCommand runCommand = message.getRunCommand();
      try {
        // start the process
        log.info("Starting process: {}", message);
        ProcessHelper processHelper = new ProcessHelper(requestId, runCommand.getCmd(), runCommand.getArgsList(), this);
        processes.put(requestId, processHelper);
        // write a success reply
        messageWriter.write(RemoteRun.AgentToMaster.newBuilder().setMessageType(RemoteRun.AgentToMaster.MessageType.STARTED).setRequestId(requestId).build());
        processHelper.start();
      } catch(Exception e1) {
        log.info("Failed to start process " + runCommand, e1);
        // write a failure reply
        messageWriter.write(RemoteRun.AgentToMaster.newBuilder().setMessageType(RemoteRun.AgentToMaster.MessageType.EXITED)
          .setRequestId(requestId).setExitCode(-1)
          .setExitReason(e1.getClass().getName() + ": " + e1.getMessage())
          .build());
      }

    } else if(RemoteRun.MasterToAgent.MessageType.STDIN_FRAGMENT == message.getMessageType()) {
      ProcessHelper processHelper = processes.get(requestId);
      if(processHelper == null) {
        log.warn("Ignoring STDIN fragment for invalid request ID " + requestId);
      } else {
        processHelper.writeStdIn(message.getFragment().toByteArray());
      }

    } else if(RemoteRun.MasterToAgent.MessageType.CLOSE_STDIN == message.getMessageType()) {
      ProcessHelper processHelper = processes.get(requestId);
      if(processHelper == null) {
        log.warn("Ignoring CLOSE_STDIN request for invalid request ID " + requestId);
      } else {
        processHelper.closeStdIn();
      }
    } else if(RemoteRun.MasterToAgent.MessageType.KILL_PROCESS == message.getMessageType()) {
      ProcessHelper processHelper = processes.get(requestId);
      if(processHelper == null) {
        log.warn("Ignoring KILL_PROCESS request for invalid request ID " + requestId);
      } else {
        processHelper.killProcess();
      }
    }
  }

  @Override
  public void dataAvailable(ByteBuffer buffer, long serverId, RemoteRun.AgentToMaster.MessageType type) {
    messageWriter.write(RemoteRun.AgentToMaster.newBuilder().setMessageType(type)
      .setRequestId(serverId)
      .setFragment(ByteString.copyFrom(buffer)).build());
  }

  @Override
  public void finished(long serverId) {
    ProcessHelper processHelper = processes.get(serverId);
    if(processHelper != null && processHelper.isFinished()) {
      ProcessHelper process = processes.remove(serverId);
      if(process != null) {
        RemoteRun.AgentToMaster.Builder msgBuilder = RemoteRun.AgentToMaster.newBuilder()
          .setMessageType(RemoteRun.AgentToMaster.MessageType.EXITED)
          .setRequestId(serverId);
        try {
          int exitCode = processHelper.getProcess().waitFor();
          log.info("Process exited: requestId={} exitCode={}", serverId, exitCode);
          msgBuilder.setExitCode(exitCode);
        } catch(InterruptedException e) {
          log.error("Interrupted whilst waiting for process exit code: requestId=" + serverId, e);
        }
        messageWriter.write(msgBuilder.build());
      }
    }
  }
}

