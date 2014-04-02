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

package net.formicary.remoterun.agent.handler;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.formicary.remoterun.agent.AgentOutputStream;
import net.formicary.remoterun.agent.MessageWriter;
import net.formicary.remoterun.common.proto.RemoteRun;
import org.apache.commons.io.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static java.nio.file.attribute.PosixFilePermission.*;
import static net.formicary.remoterun.common.proto.RemoteRun.AgentToMaster.MessageType.REQUESTED_DATA;
import static net.formicary.remoterun.common.proto.RemoteRun.MasterToAgent.MessageType;

/**
 * @author Chris Pearson
 */
@Handler(MessageType.REQUEST_DATA)
@Component
public class RequestDataHandler implements MasterToAgentHandler {
  private static final Logger log = LoggerFactory.getLogger(RequestDataHandler.class);
  private static final String IS_DIRECTORY = "isDirectory";
  private static final String LAST_MODIFIED = "lastModifiedTime";
  private static final PosixFilePermission[] POSIX_ORDER = {
    OWNER_READ, OWNER_WRITE, OWNER_EXECUTE,
    GROUP_READ, GROUP_WRITE, GROUP_EXECUTE,
    OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE
  };

  @Override
  public void handle(MessageType type, RemoteRun.MasterToAgent message, MessageWriter messageWriter) {
    String fullPath = message.getRequestData().getFullPath();
    Path path = Paths.get(fullPath);

    AgentOutputStream out = new AgentOutputStream(message.getRequestId(), messageWriter);
    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(out, 1000000 /*TODO: work out best size*/);
    ZipOutputStream zipOutput = new ZipOutputStream(bufferedOutputStream);

    try {
      Map<String, Object> attributes = Files.readAttributes(path, IS_DIRECTORY, LinkOption.NOFOLLOW_LINKS);
      send(path, Boolean.TRUE.equals(attributes.get(IS_DIRECTORY)) ? path : path.getParent(), zipOutput);
      zipOutput.close();
      bufferedOutputStream.close();
      out.close();
    } catch(Exception e) {
      log.error("Failed to compress and send " + path.toString(), e);
      messageWriter.write(RemoteRun.AgentToMaster.newBuilder()
        .setMessageType(REQUESTED_DATA)
        .setRequestId(message.getRequestId())
        .setExitCode(-1)
        .setExitReason("Failed to compress and send " + path.toString() + ": " + e.toString())
        .build());
    }
  }

  private void send(Path path, Path relativeTo, ZipOutputStream zipOutput) throws IOException {
    Map<String, Object> attributes = Files.readAttributes(path, IS_DIRECTORY + ',' + LAST_MODIFIED, LinkOption.NOFOLLOW_LINKS);
    boolean isDirectory = Boolean.TRUE.equals(attributes.get(IS_DIRECTORY));
    FileTime lastModifiedTime = (FileTime)attributes.get(LAST_MODIFIED);

    String relativePath = relativeTo.relativize(path).toString();
    ZipEntry entry = new ZipEntry(isDirectory ? relativePath + '/' : relativePath);
    if(path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      // add "<owner> <group> <permissions>" as a UTF-8 string in extra data
      PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
      PosixFileAttributes posix = view.readAttributes();
      StringBuilder sb = new StringBuilder();
      sb.append(posix.owner().getName()).append(' ');
      sb.append(posix.group().getName()).append(' ');
      int permissions = 0;
      for(int i = 1; i <= POSIX_ORDER.length; i++) {
        if(posix.permissions().contains(POSIX_ORDER[POSIX_ORDER.length - i])) {
          permissions = permissions | (int)Math.pow(2, i - 1);
        }
      }
      sb.append(Integer.toOctalString(permissions));
      entry.setExtra(sb.toString().getBytes(Charsets.UTF_8));
    }
    entry.setTime(lastModifiedTime.toMillis());
    if(!Files.isSameFile(path, relativeTo)) {
      zipOutput.putNextEntry(entry);
    }
    if(isDirectory) {
      try (DirectoryStream<Path> dir = Files.newDirectoryStream(path)) {
        for(Path child : dir) {
          send(child, relativeTo, zipOutput);
        }
      }
    } else {
      Files.copy(path, zipOutput);
    }
  }
}
