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

import javax.security.cert.X509Certificate;

import net.formicary.remoterun.common.proto.RemoteRun;
import net.formicary.remoterun.embed.AgentConnection;
import net.formicary.remoterun.embed.ConnectionState;
import net.formicary.remoterun.embed.RemoteRunMaster;
import net.formicary.remoterun.embed.callback.AgentConnectionCallback;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.ssl.SslHandler;
import org.mockito.Matchers;
import org.testng.annotations.Test;

import static net.formicary.remoterun.common.proto.RemoteRun.AgentToMaster.MessageType.AGENT_INFO;
import static org.mockito.Mockito.*;

/**
 * @author Chris Pearson
 */
public class MasterTest {
  @Test
  public void testAgentConnectionCallback() throws Exception {
    RemoteRun.AgentToMaster agentInfoMessage = RemoteRun.AgentToMaster.newBuilder()
      .setAgentInfo(RemoteRun.AgentToMaster.AgentInfo.newBuilder().build())
      .setMessageType(AGENT_INFO)
      .build();
    // set up mocks
    ChannelHandlerContext contextMock = mock(ChannelHandlerContext.class, RETURNS_DEEP_STUBS);
    SslHandler sslHandler = mock(SslHandler.class, RETURNS_DEEP_STUBS); // linked to contextMock
    AgentConnection agentConnection = mock(AgentConnection.class, RETURNS_MOCKS); // linked to contextMock
    AgentConnectionCallback agentConnectionCallback = mock(AgentConnectionCallback.class); // RemoteRunMaster creation
    ChannelStateEvent eventMock = mock(ChannelStateEvent.class); // RemoteRunMaster.channelConnected
    ChannelFuture futureMock = mock(ChannelFuture.class, RETURNS_DEEP_STUBS); // RemoteRunMaster.operationComplete
    MessageEvent agentInfoMock = mock(MessageEvent.class, RETURNS_MOCKS); // RemoteRunMaster.messageReceived
    X509Certificate agentCertificate = mock(X509Certificate.class, RETURNS_MOCKS);
    Channel channel = mock(Channel.class, RETURNS_DEEP_STUBS);
    // agentConnectionCallback behaviour
    when(contextMock.getPipeline().get(eq(SslHandler.class))).thenReturn(sslHandler); // because return value is casted
    when(contextMock.getChannel()).thenReturn(channel);
    when(futureMock.getChannel()).thenReturn(channel);
    when(agentInfoMock.getChannel()).thenReturn(channel);
    when(channel.getPipeline().get(eq(SslHandler.class))).thenReturn(sslHandler); // because return value is casted
    when(channel.getAttachment()).thenReturn(agentConnection); // because return value is casted
    when(futureMock.isSuccess()).thenReturn(true); // because we want to pretend success
    when(agentInfoMock.getMessage()).thenReturn(agentInfoMessage); // because we want to mock a specific message
    when(sslHandler.getEngine().getSession().getPeerCertificateChain()).thenReturn(new X509Certificate[]{agentCertificate}); // because it's an array we need one in

    // actual test
    // create master
    RemoteRunMaster master = new RemoteRunMaster(agentConnectionCallback);
    // tcp connection made
    master.channelConnected(contextMock, eventMock);
    verifyZeroInteractions(agentConnectionCallback);
    // ssl connection complete
    master.operationComplete(futureMock);
    verify(agentConnection, atMost(1)).setConnectionState(Matchers.<ConnectionState>any());
    verify(agentConnection).setConnectionState(eq(ConnectionState.PENDING_AGENTINFO));
    verifyZeroInteractions(agentConnectionCallback);
    // now receive the AgentInfo message and check we've been told
    master.messageReceived(contextMock, agentInfoMock); // agentInfo message received
    verify(agentConnection, atMost(2)).setConnectionState(Matchers.<ConnectionState>any());
    verify(agentConnection).setConnectionState(eq(ConnectionState.CONNECTED));
    verify(agentConnectionCallback, times(1)).agentConnected(eq(agentConnection));
  }
}
