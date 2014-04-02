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

import com.google.protobuf.ByteString;
import net.formicary.remoterun.common.proto.RemoteRun;

import static net.formicary.remoterun.common.proto.RemoteRun.AgentToMaster.MessageType.REQUESTED_DATA;

/**
 * For optimum usage wrap in a BufferedOutputStream with (1048576 - overhead) buffer size.  Haven't done the work to
 * determine what the overhead is though.
 *
 * @author Chris Pearson
 */
public class AgentOutputStream extends OutputStream {
  private final long requestId;
  private final MessageWriter messageWriter;
  private boolean closed = false;

  public AgentOutputStream(long requestId, MessageWriter messageWriter) {
    this.requestId = requestId;
    this.messageWriter = messageWriter;
  }

  @Override
  public void write(int b) throws IOException {
    write(new byte[]{(byte)(b & 0xFF)}, 0, 1);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    messageWriter.write(RemoteRun.AgentToMaster.newBuilder()
      .setRequestId(requestId)
      .setMessageType(REQUESTED_DATA)
      .setFragment(ByteString.copyFrom(b, off, len))
      .build());
  }

  @Override
  public void close() throws IOException {
    if(!closed) {
      closed = true;
      messageWriter.write(RemoteRun.AgentToMaster.newBuilder()
        .setRequestId(requestId)
        .setMessageType(REQUESTED_DATA)
        .setExitCode(0)
        .build());
    }
  }
}
