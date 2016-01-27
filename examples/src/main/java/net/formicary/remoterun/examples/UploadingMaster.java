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

package net.formicary.remoterun.examples;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.formicary.remoterun.embed.IAgentConnection;
import net.formicary.remoterun.embed.RemoteRunMaster;
import net.formicary.remoterun.embed.callback.AbstractAgentConnectionCallback;
import net.formicary.remoterun.embed.callback.UploadCompleteCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Chris Pearson
 */
public class UploadingMaster {
  private static final String LOCAL_SOURCE_FILE = "README.md";
  private static final Logger log = LoggerFactory.getLogger(UploadingMaster.class);

  public static void main(String[] args) {
    new RemoteRunMaster(new AbstractAgentConnectionCallback() {
      @Override
      public void agentConnected(IAgentConnection agentConnection) {
        // on connect upload a file
        uploadTo(agentConnection);
      }
    }).bind(new InetSocketAddress(1081));
  }

  private static void uploadTo(IAgentConnection agentConnection) {
    try {
      final Path tempDirectory = Files.createTempDirectory("RemoteRunExample_UploadingMaster");
      agentConnection.upload(Paths.get(LOCAL_SOURCE_FILE), tempDirectory.toString(), new UploadCompleteCallback() {
        @Override
        public void uploadComplete(IAgentConnection agent, long requestId, String targetPath, boolean success) {
          log.info("File upload of {} to {} complete, success={}", LOCAL_SOURCE_FILE, tempDirectory, success);
        }
      });
    } catch(Exception e) {
      log.error("Failed to upload to agent " + agentConnection, e);
      agentConnection.shutdown();
    }
  }
}
