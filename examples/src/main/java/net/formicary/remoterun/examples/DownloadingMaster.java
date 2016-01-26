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

import net.formicary.remoterun.embed.IAgentConnection;
import net.formicary.remoterun.embed.RemoteRunMaster;
import net.formicary.remoterun.embed.callback.AbstractAgentConnectionCallback;
import net.formicary.remoterun.embed.callback.FileDownloadCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Chris Pearson
 */
public class DownloadingMaster {
  private static final String REMOTE_SOURCE_FILE = "/var/tmp/demo.file";
  private static final Logger log = LoggerFactory.getLogger(DownloadingMaster.class);

  public static void main(String[] args) {
    new RemoteRunMaster(new AbstractAgentConnectionCallback() {
      @Override
      public void agentConnected(IAgentConnection agentConnection) {
        // on connect download a file
        downloadFrom(agentConnection);
      }
    }).bind(new InetSocketAddress(1081));
  }

  private static void downloadFrom(IAgentConnection agentConnection) {
    try {
      final Path tempDirectory = Files.createTempDirectory("RemoteRunExample_DownloadingMaster");
      agentConnection.download(REMOTE_SOURCE_FILE, tempDirectory, new FileDownloadCallback() {
        @Override
        public void onExit(int exitCode, String exitReason) {
          log.info("File download to {} complete, exitCode={}, exitReason={}", tempDirectory, exitCode, exitReason);
        }
      });
    } catch(Exception e) {
      log.error("Failed to download from agent " + agentConnection, e);
      agentConnection.shutdown();
    }
  }
}
