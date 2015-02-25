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

package net.formicary.remoterun.embed.test;

import java.util.concurrent.TimeUnit;

import com.google.protobuf.ByteString;
import net.formicary.remoterun.common.proto.RemoteRun.AgentToMaster;
import net.formicary.remoterun.embed.AgentConnection;
import net.formicary.remoterun.embed.callback.TextOutputCallback;
import net.formicary.remoterun.embed.request.TextOutputRequest;
import org.jboss.netty.channel.Channel;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import static net.formicary.remoterun.embed.request.MessageHelper.runCommand;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Chris Pearson
 */
public class TestRequestMessage {
  @Test
  public void testRequestMessage() throws InterruptedException {
    Channel channel = mock(Channel.class);
    AgentConnection agent = new AgentConnection(channel);
    TextOutputCallback callback = mock(TextOutputCallback.class);
    TextOutputRequest request = new TextOutputRequest(runCommand("/bin/true"), callback);
    long requestId = request.getRequest().getRequestId();
    Assert.assertNotEquals(requestId, 0);

    // "send" the request - should trigger nothing on the callback
    agent.request(request);
    verify(callback, times(0)).onStdErr(anyString());
    verify(callback, times(0)).onStdOut(anyString());
    verify(callback, times(0)).onExit(anyInt(), anyString());

    // "receive" half a line of input - no callbacks still
    agent.messageReceived(agent, AgentToMaster.newBuilder()
      .setMessageType(AgentToMaster.MessageType.STDOUT_FRAGMENT)
      .setRequestId(requestId)
      .setFragment(ByteString.copyFromUtf8("start of in"))
      .build());
    verify(callback, times(0)).onStdErr(anyString());
    verify(callback, times(0)).onStdOut(anyString());
    verify(callback, times(0)).onExit(anyInt(), anyString());

    // "receive" the other half, along with a bit of another - verify the first line is parsed
    agent.messageReceived(agent, AgentToMaster.newBuilder()
      .setMessageType(AgentToMaster.MessageType.STDOUT_FRAGMENT)
      .setRequestId(requestId)
      .setFragment(ByteString.copyFromUtf8("put\nstart of next "))
      .build());
    verify(callback, times(0)).onStdErr(anyString());
    verify(callback, times(1)).onStdOut("start of input");
    verify(callback, times(0)).onExit(anyInt(), anyString());

    // exit the process, check it was successful
    agent.messageReceived(agent, AgentToMaster.newBuilder()
      .setMessageType(AgentToMaster.MessageType.EXITED)
      .setRequestId(requestId)
      .setExitCode(0)
      .build());
    request.getCompletionLatch().await(0, TimeUnit.MILLISECONDS);
  }

  @Test
  public void testLineLengthOverflow() {
    Channel channel = Mockito.mock(Channel.class);
    AgentConnection agent = new AgentConnection(channel);
    // send a run command to the agent
    final int bufferLength = 20;
    TextOutputCallback callback = mock(TextOutputCallback.class);
    TextOutputRequest request = Mockito.spy(new TextOutputRequest(runCommand("/bin/true"), bufferLength, callback));
    agent.request(request);
    // receive a couple of lines of stdout, but the first is longer than the line length buffer
    agent.messageReceived(agent, AgentToMaster.newBuilder()
      .setMessageType(AgentToMaster.MessageType.STDOUT_FRAGMENT)
      .setRequestId(request.getRequest().getRequestId())
      .setFragment(ByteString.copyFromUtf8("This is a long line that overflows the buffer\nAnd a second\n"))
      .build());
    // first few should be the length of the buffer
    verify(callback).onStdOut("This is a long line ");
    verify(callback).onStdOut("that overflows the b");
    verify(callback).onStdOut("uffer");
    verify(callback).onStdOut("And a second");
    verifyNoMoreInteractions(callback);
  }
}
