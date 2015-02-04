# RemoteRun
By Chris Pearson <christopher.pearson at formicary.net>

## Introduction

RemoteRun is a project created because I couldn't find any suitable alternative for running processes remotely without
resorting to SSH etc which is often unavailable to processes in larger (specifically financial) organisations.  Remote
services are generally permitted if deployed and managed appropriately, but SSH keys etc are often not.  There are
various options available but all seem to be overly complicated, so remoterun is aimed at literally permitting execution
of a remote command, permitting file transfers to/from an agent, and nothing more complex.

RemoteRun may be desirable when one or more of the following apply:

+ a large Java application (e.g. webserver) wants to fork sub-processes, and duplicating the memory footprint is an
  issue (Runtime.exec forks, and fork duplicates the memory footprint of the current process)
+ you need to run processes on another server or as another user

The idea is that you:

+ on servers you want to run applications you start a net.formicary.remoterun.agent.RemoteRunAgent instance
+ embed an instance of net.formicary.remoterun.embed.RemoteRunMaster in your application which listens for agent
  connections and issues commands to the agents

remoterun requires SSL client authentication: I wanted a way of running services without usernames/passwords but with
some form of authentication.  So the keystores are password protected, and this must be specified as a system property.
It doesn't explicitly use CRLs unless any of the JDK implementations automatically do that.

## Running the example

The recommended way to get started with remoterun is to run the example then have a look at
examples/src/main/java/net/formicary/remoterun/examples/FileServer.java to adapt for use in your
application.  There is a bundled example command line interface service in net.formicary.remoterun.examples.Server.
To run it:

+ Generate SSL certificates by running ssl/certs.sh
+ run net.formicary.remoterun.examples.Server
+ run net.formicary.remoterun.agent.RemoteRunAgent
+ issue commands on the Server console as follows:
    + l - list connections
    + r <cmd> [arg [arg [...]]] - run a remote command
    + i <requestId> <stdinText> - send input to stdin of process
    + c <requestId> - close stdin of process

### Example output from immediate exit
    08:13:49.009 [main] INFO  n.f.remoterun.embed.RemoteRunMaster - Listening for connections on 0.0.0.0/0.0.0.0:1081
    08:13:58.063 [New I/O worker #1] INFO  n.f.remoterun.embed.RemoteRunMaster - Agent connected from /127.0.0.1:51540 (1 open connections)
    08:13:58.461 [New I/O worker #1] INFO  n.f.remoterun.embed.RemoteRunMaster - Agent connection complete from /127.0.0.1:51540 (CN=agent1, O=Formicary Ltd, L=London, ST=Greater London, C=GB)
    l
    08:14:08.860 [main] INFO  net.formicary.remoterun.examples.Server - 1: CONNECTED /127.0.0.1:51540
    08:14:08.860 [main] INFO  net.formicary.remoterun.examples.Server - Finished listing 1 agent connections
    r echo hello
    08:14:14.149 [main] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] WRITE: messageType: RUN_COMMAND requestId: 1 runCommand {   cmd: "echo"   args: "hello" }
    08:14:14.300 [New I/O worker #1] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] RECEIVED: messageType: STARTED requestId: 1
    08:14:14.301 [New I/O worker #1] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] RECEIVED: messageType: STDOUT_FRAGMENT requestId: 1 fragment: "hello\n"
    08:14:14.302 [New I/O worker #1] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] RECEIVED: messageType: EXITED requestId: 1 exitCode: 0

### Example output from longer running process
    l
    08:15:39.448 [main] INFO  net.formicary.remoterun.examples.Server - 1: CONNECTED /127.0.0.1:51540
    08:15:39.448 [main] INFO  net.formicary.remoterun.examples.Server - Finished listing 1 agent connections
    r cat
    08:15:41.266 [main] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] WRITE: messageType: RUN_COMMAND requestId: 2 runCommand {   cmd: "cat" }
    08:15:41.280 [New I/O worker #1] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] RECEIVED: messageType: STARTED requestId: 2
    i 2 hello\n
    08:15:49.565 [main] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] WRITE: messageType: STDIN_FRAGMENT requestId: 2 stdinFragment: "hello\n"
    08:15:49.568 [New I/O worker #1] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] RECEIVED: messageType: STDOUT_FRAGMENT requestId: 2 fragment: "hello\n"
    c 2
    08:16:00.580 [main] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] WRITE: messageType: CLOSE_STDIN requestId: 2
    08:16:00.584 [New I/O worker #1] DEBUG n.f.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] RECEIVED: messageType: EXITED requestId: 2 exitCode: 0


## System Properties

 + javax.net.ssl.keyStore - path to keystore (i.e. keytool file containing master/agent certificate and key), defaults
   to "ssl/agent1-keystore.jks" or "ssl/server-keystore.jks" for agent and master respectively
 + javax.net.ssl.keyStoreType - JKS (Java Key Store) or PKCS12, defaults to JKS
 + javax.net.ssl.keyStorePassword - keystore and key passwords, defaults to "123456"
 + javax.net.ssl.trustStore - path to truststore (i.e. keytool file containing the certification authority public cert),
   defaults to "ssl/ca-truststore.jks"
 + javax.net.ssl.trustStoreType - JKS (Java Key Store) or PKCS12, defaults to JKS
 + javax.net.ssl.trustStorePassword - keystore password, defaults to "123456"

## Using RemoteRun in your own application

Using maven you need this in your pom.xml:

    <dependencies>
      <dependency>
        <groupId>net.formicary.remoterun</groupId>
        <artifactId>remoterun-embed</artifactId>
        <version>2.9</version>
      </dependency>
    </dependencies>

    <repositories>
      <repository>
        <id>remoterun-mvn-repo</id>
        <url>https://raw.github.com/formicary/remoterun/mvn-repo/</url>
        <snapshots>
          <enabled>true</enabled>
          <updatePolicy>always</updatePolicy>
        </snapshots>
      </repository>
    </repositories>

The simplest way to get started is not to call the RemoteRunMaster class directly but instead in your spring (or
similar) configuration:

    @Bean
    @Autowired
    @Lazy(false)
    public SimpleRemoteRun remoteRun(@Value("${remoterun.listen.host:}") String host, @Value("${remoterun.listen.port:1081}") int port, AgentConnectionCallback callback) {
      InetSocketAddress bindAddress = host == null || host.length() == 0 ? new InetSocketAddress(port) : new InetSocketAddress(host, port);
      return SimpleRemoteRun.start(bindAddress, new AgentStateFactory<AgentCallback>() {
        @Override
        public AgentCallback newConnection(AgentConnection connection) {
          return new MyImplementationOfAgentCallback(/* could pass connection in here */);
        }
      });
    }

Then you need to implement the AgentCallback interface and make the agents do what you want:

    public class MyImplementationOfAgentCallback implements AgentCallback {
      @Override
      public void messageReceived(AgentConnection agentConnection, RemoteRun.AgentToMaster message) throws Exception {
        if (message.getMessageType() == RemoteRun.AgentToMaster.MessageType.AGENT_INFO) {
          // Do something on establishment of connection
        } else {
          // Do something when a message is received
        }
      }

      @Override
      public void close(AgentConnection agentConnection) throws Exception {
        // Do something when an agent disconnects
      }
    }

To send a message to an agent, you can use the established connections held in SimpleRemoteRun:

## Licensing

Copyright 2014 Formicary Ltd

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.