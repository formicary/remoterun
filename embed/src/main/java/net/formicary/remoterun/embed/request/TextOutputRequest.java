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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import net.formicary.remoterun.common.proto.RemoteRun;
import net.formicary.remoterun.embed.AgentConnection;
import net.formicary.remoterun.embed.DecodingBuffer;
import net.formicary.remoterun.embed.RemoteRunMaster;
import net.formicary.remoterun.embed.callback.TextOutputCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience class to wrap a command that expects text output.  Override onStdOut and onStdErr to capture each line as
 * it's received.
 *
 * @author Chris Pearson
 */
public class TextOutputRequest implements AgentRequest {
  public static final int DEFAULT_MAX_LINE_LENGTH = 4096;
  private static final Logger log = LoggerFactory.getLogger(TextOutputRequest.class);
  private final TextOutputCallback callback;
  private final CountDownLatch completionLatch = new CountDownLatch(1);
  private final RemoteRun.MasterToAgent request;
  private final DecodingBuffer stdOutBuffer;
  private final DecodingBuffer stdErrBuffer;

  /**
   * Same as new TextOutputRequest(requestBuilder, 4096, UTF_8).
   *
   * @param requestBuilder builder for the run command
   */
  public TextOutputRequest(RemoteRun.MasterToAgent.Builder requestBuilder, TextOutputCallback callback) {
    this(requestBuilder, DEFAULT_MAX_LINE_LENGTH, StandardCharsets.UTF_8, callback);
  }

  /**
   * Same as new TextOutputRequest(requestBuilder, maxLineLength, UTF_8).
   *
   * @param requestBuilder builder for the run command
   * @param maxLineLength max number of characters that can form a line of output
   */
  public TextOutputRequest(RemoteRun.MasterToAgent.Builder requestBuilder, int maxLineLength, TextOutputCallback callback) {
    this(requestBuilder, maxLineLength, StandardCharsets.UTF_8, callback);
  }

  public TextOutputRequest(RemoteRun.MasterToAgent.Builder requestBuilder, int maxLineLength, Charset charset, TextOutputCallback callback) {
    this.callback = callback;
    this.request = requestBuilder.setRequestId(RemoteRunMaster.getNextRequestId()).build();
    this.stdOutBuffer = new DecodingBuffer(new DecodingBuffer.DataCallback() {
      @Override
      public void lineRead(String line) {
        TextOutputRequest.this.callback.onStdOut(line);
      }
    }, charset, maxLineLength);
    this.stdErrBuffer = new DecodingBuffer(new DecodingBuffer.DataCallback() {
      @Override
      public void lineRead(String line) {
        TextOutputRequest.this.callback.onStdErr(line);
      }
    }, charset, maxLineLength);
  }

  @Override
  public RemoteRun.MasterToAgent getMessage() {
    return request;
  }

  public RemoteRun.MasterToAgent getRequest() {
    return request;
  }

  @Override
  public CountDownLatch getCompletionLatch() {
    return completionLatch;
  }

  @Override
  public final void receivedMessage(AgentConnection agent, RemoteRun.AgentToMaster message) {
    byte[] fragment = message.hasFragment() ? message.getFragment().toByteArray() : null;
    switch(message.getMessageType()) {
      case STDOUT_FRAGMENT:
        stdOutBuffer.write(fragment);
        break;
      case STDERR_FRAGMENT:
        stdErrBuffer.write(fragment);
        break;
      case EXITED:
        try {
          callback.onExit(message.getExitCode(), message.hasExitReason() ? message.getExitReason() : null);
        } catch(Exception e) {
          log.error("Call to onExit failed", e);
        } finally {
          completionLatch.countDown();
        }
        break;
    }
  }

}
