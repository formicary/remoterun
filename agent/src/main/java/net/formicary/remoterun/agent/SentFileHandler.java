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

package net.formicary.remoterun.agent;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

import net.formicary.remoterun.common.FileReceiver;
import net.formicary.remoterun.common.IoUtils;
import net.formicary.remoterun.common.proto.RemoteRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.formicary.remoterun.common.proto.RemoteRun.MasterToAgent.MessageType.*;

/**
 * Receive fragments of the specified file, and return whether or not receive was successful afterwards.
 *
 * @author Chris Pearson
 */
public class SentFileHandler {
  private static final Logger log = LoggerFactory.getLogger(SentFileHandler.class);
  private final Map<Long, ReceivingFile> inProgress = new TreeMap<>();

  public void handle(RemoteRun.MasterToAgent message, RemoteRunAgent agent) {
    log.debug("Handle " + message.getMessageType());
    ReceivingFile receivingFile = null;
    if(message.getMessageType() == SEND_DATA_NOTIFICATION) {
      receivingFile = new ReceivingFile(message.getPath());
      inProgress.put(message.getRequestId(), receivingFile);
      try {
        new Thread(receivingFile.receiver = new FileReceiver(Paths.get("."))).start();
      } catch(Exception e) {
        receivingFile.fail("Failed to create FileReceiver", e, 2);
      }

    } else if(message.getMessageType() == SEND_DATA_FRAGMENT) {
      receivingFile = inProgress.get(message.getRequestId());
      if(message.hasFragment() && !receivingFile.finished) {
        try {
          message.getFragment().writeTo(receivingFile.receiver.getPipedOutputStream());
        } catch(IOException e) {
          receivingFile.fail("Failed whilst writing to " + receivingFile.path, e, 3);
        }
      }
      if(message.hasDataSuccess() && !receivingFile.finished) {
        try {
          if(message.getDataSuccess() && receivingFile.receiver.success()) {
            receivingFile.succeed();
          } else if (message.getDataSuccess()) {
            receivingFile.fail(receivingFile.receiver.getFailureMessage(), receivingFile.receiver.getFailure(), 5);
          } else {
            receivingFile.fail("Server failed to stream file", null, 4);
          }
        } catch(Exception e) {
          receivingFile.fail("Failed to close piped output stream", e, 1);
        }
      }
    }

    if(receivingFile != null && !receivingFile.sentResponse && receivingFile.finished) {
      receivingFile.sentResponse = true;
      IoUtils.closeQuietly(receivingFile.receiver.getPipedOutputStream());
      receivingFile.receiver.waitUntilFinishedUninterruptably();
      try {
        receivingFile.receiver.close();
      } catch(IOException e) {
        log.trace("Failed to close receiver", e);
      }
      RemoteRun.AgentToMaster.Builder builder = RemoteRun.AgentToMaster.newBuilder()
        .setRequestId(message.getRequestId())
        .setMessageType(RemoteRun.AgentToMaster.MessageType.RECEIVED_DATA);
      if(receivingFile.success) {
        builder = builder.setExitCode(0);
      } else {
        builder = builder.setExitCode(receivingFile.exitCode).setExitReason(receivingFile.failureMessage);
      }
      agent.write(builder.build());
    }
  }

  private static final class ReceivingFile {
    private final String path;
    private boolean sentResponse = false;
    private int exitCode = 0;
    private boolean finished;
    private boolean success;
    private String failureMessage;
    public FileReceiver receiver;

    public ReceivingFile(String path) {
      this.path = path;
    }

    public void succeed() {
      finished = true;
      success = true;
    }

    public void fail(String message, Throwable throwable, int exitCode) {
      finished = true;
      success = false;
      failureMessage = throwable == null ? message : (message + " : " + throwable.toString());
      log.warn("Failed to unpack " + path, throwable);
      this.exitCode = exitCode;
    }
  }
}
