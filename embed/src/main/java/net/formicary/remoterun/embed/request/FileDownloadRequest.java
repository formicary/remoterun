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

package net.formicary.remoterun.embed.request;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import net.formicary.remoterun.common.FileReceiver;
import net.formicary.remoterun.common.IoUtils;
import net.formicary.remoterun.common.proto.RemoteRun;
import net.formicary.remoterun.embed.AgentConnection;
import net.formicary.remoterun.embed.RemoteRunMaster;
import net.formicary.remoterun.embed.callback.FileDownloadCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Chris Pearson
 */
public class FileDownloadRequest implements AgentRequest {
  private static final Logger log = LoggerFactory.getLogger(FileDownloadRequest.class);
  private final CountDownLatch completionLatch = new CountDownLatch(1);
  private final String remoteSource;
  private final Path localTargetRoot;
  private final FileDownloadCallback callback;
  private final RemoteRun.MasterToAgent message;
  private final FileReceiver receiver;
  private final Thread receiverThread;

  public FileDownloadRequest(String remoteSource, Path localTargetRoot, FileDownloadCallback callback) {
    this.remoteSource = remoteSource;
    this.localTargetRoot = localTargetRoot;
    this.callback = callback;
    message = RemoteRun.MasterToAgent.newBuilder()
      .setMessageType(RemoteRun.MasterToAgent.MessageType.REQUEST_DATA)
      .setPath(remoteSource)
      .setRequestId(RemoteRunMaster.getNextRequestId())
      .build();
    receiver = new FileReceiver(localTargetRoot);
    receiverThread = new Thread(receiver, "FileReceiver:" + localTargetRoot);
    receiverThread.start();
  }

  @Override
  public RemoteRun.MasterToAgent getMessage() {
    return message;
  }

  @Override
  public CountDownLatch getCompletionLatch() {
    return completionLatch;
  }

  public void close() {
    IoUtils.closeQuietly(receiver);
    receiverThread.interrupt();
  }

  @Override
  public void receivedMessage(AgentConnection agent, RemoteRun.AgentToMaster message) {
    if(message.getMessageType() == RemoteRun.AgentToMaster.MessageType.REQUESTED_DATA) {
      if(message.hasFragment()) {
        // data fragment
        try {
          message.getFragment().writeTo(receiver.getPipedOutputStream());
        } catch(IOException e) {
          log.error("Failed to receive data for " + remoteSource, e);
          close();
          callback.onExit(-1, "Failed streaming data internally: " + e.getMessage());
        }
      }
      if(message.hasExitCode()) {
        // last message of the file
        receiver.waitUntilFinishedUninterruptably();
        close();
        if(message.getExitCode() == 0) {
          log.info("Successfully received {} to {}", remoteSource, localTargetRoot);
        } else {
          log.info("Failed whilst receiving {} to {}: {}", remoteSource, localTargetRoot, message.getExitReason());
        }
        callback.onExit(message.getExitCode(), message.getExitReason());
        close();
      }
    }
  }
}
