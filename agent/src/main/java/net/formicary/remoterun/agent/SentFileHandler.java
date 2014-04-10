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
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Map;
import java.util.TreeMap;

import com.sun.nio.zipfs.ZipFileSystem;
import net.formicary.remoterun.common.proto.RemoteRun;

import static net.formicary.remoterun.common.proto.RemoteRun.MasterToAgent.MessageType.*;

/**
 * Receive fragments of the specified file, and return whether or not receive was successful afterwards.
 *
 * @author Chris Pearson
 */
public class SentFileHandler {
  private final Map<Long, ReceivingFile> inProgress = new TreeMap<>();

  public void handle(RemoteRun.MasterToAgent message, RemoteRunAgent agent) {
    ReceivingFile receivingFile = null;
    if(message.getMessageType() == SEND_DATA_NOTIFICATION) {
      receivingFile = new ReceivingFile(message.getPath());
      inProgress.put(message.getRequestId(), receivingFile);
        try {
          receivingFile.tempFile = Files.createTempFile("received_", ".zip");
          receivingFile.outputStream = Files.newOutputStream(receivingFile.tempFile);
        } catch(Exception e) {
          receivingFile.fail("Failed to create temp directory output stream for " + receivingFile.tempFile + ": " + e.getMessage(), 2);
        }

    } else if(message.getMessageType() == SEND_DATA_FRAGMENT) {
      receivingFile = inProgress.get(message.getRequestId());
      if(message.hasFragment() && !receivingFile.finished) {
        try {
          message.getFragment().writeTo(receivingFile.outputStream);
        } catch(IOException e) {
          receivingFile.fail("Failed whilst writing to " + receivingFile.path + ": " + e.getMessage(), 3);
        }
      }
      if(message.hasDataSuccess() && !receivingFile.finished) {
        if(message.getDataSuccess()) {
          receivingFile.succeed();
        } else {
          receivingFile.fail("Server failed to stream file", 4);
        }
      }
    }

    if(receivingFile != null && !receivingFile.sentResponse && receivingFile.finished) {
      receivingFile.closeAndUnzip();

      receivingFile.sentResponse = true;
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
    public Path tempFile;
    public OutputStream outputStream;

    public ReceivingFile(String path) {
      this.path = path;
    }

    public void succeed() {
      finished = true;
      success = true;
    }

    public void fail(String message, int exitCode) {
      finished = true;
      success = false;
      failureMessage = message;
      this.exitCode = exitCode;
    }

    public void closeAndUnzip() {
      // close output stream
      try {
        outputStream.close();
      } catch(IOException e) {
        fail("Failed to close output stream " + tempFile + ": " + e.getMessage(), 5);
        return;
      }

      // delete the destination path/location
      Path path = Paths.get(this.path);
      try {
        Files.deleteIfExists(path);
      } catch(Exception e) {
        fail("Failed to remove existing path " + path + ": " + e.getMessage(), 1);
        return;
      }
      try (FileSystem zipFs = FileSystems.newFileSystem(path, null);
        DirectoryStream<Path> directoryStream = Files.newDirectoryStream(zipFs.getPath("/"))) {
        int count = 0;
        for(Path child : directoryStream) {
          count++;
          if (count > 1) break;
        }
        if (count > 1) {
          // directory, unzip
        }
      } catch (Exception e) {
        fail("Failed to unpack " + tempFile + ": " + e.getMessage(), 6);
      }
    }
  }
}
