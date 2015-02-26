# RemoteRun
By Chris Pearson <christopher.pearson at formicary.net>

## Introduction

RemoteRun is a Java library enabling you to spawn child processes with minimum memory footprint, optionally on another
host.  A call to Runtime.exec() duplicates the memory footprint of the host process, which can be prohibitive.  By
running a lightweight "agent" and using that to spawn new processes we avoid the pain of creating new processes, and
gain the ability to run processes on other hosts too.

Using remoterun means you don't need to resort to SSH etc which is often poorly supported in Java and unavailable to
processes in larger (specifically financial) organisations.  Remote services are generally permitted if deployed and
managed appropriately, but SSH keys etc are often not.  There are various options available but all seem to be overly
complicated, so remoterun is aimed at literally permitting execution of a remote command, permitting file transfers
to/from an agent, and nothing more complex.

RemoteRun may be desirable when one or more of the following apply:

+ a large Java application (e.g. webserver) wants to fork sub-processes, and duplicating the memory footprint is an
  issue (Runtime.exec forks, and fork duplicates the memory footprint of the current process)
+ you need to run processes on another server or as another user

The idea is that:

+ on servers you want to run applications you start a net.formicary.remoterun.agent.RemoteRunAgent instance
+ you embed an instance of net.formicary.remoterun.embed.RemoteRunMaster in your application which listens for agent
  connections and lets you issue commands to the agents

remoterun requires SSL client authentication: no usernames/passwords but a bi-directional certificate trust.  So the
keystores can be password protected, and this must be specified as a system property.
It doesn't explicitly use CRLs unless any of the JDK implementations automatically do that.

## Using RemoteRun in your own application

Using maven you need this in your pom.xml:

    <dependencies>
      <dependency>
        <groupId>net.formicary.remoterun</groupId>
        <artifactId>remoterun-embed</artifactId>
        <version>3.0.0</version>
      </dependency>
    </dependencies>

SSL certificates are mandatory.  To generate them run ssl/certs.sh or use some other means.  RemoteRun uses the java
support for Java Key Store format or PKCS12 (.pfx) format certificate/trust stores.

The simplest way to get started with the code is to take a look at
/examples/src/main/java/net/formicary/remoterun/examples/SimpleRemoteRunMaster.java

To use it yourself without any immediate behaviour when an agent connects:

    RemoteRunMaster master = new RemoteRunMaster(/* optionally specify on-connect callback */);
    master.bind(bindAddress);

An annotation driven Spring bean provision of the above might look like this:

    @Bean(destroyMethod = "shutdown")
    @Autowired
    @Lazy(false)
    public RemoteRunMaster remoteRun(@Value("${remoterun.listen.host:}") String host, @Value("${remoterun.listen.port:1081}") int port, final BeanFactory beanFactory) {
      RemoteRunMaster master = new RemoteRunMaster();
      InetSocketAddress bindAddress = host == null || host.length() == 0 ? new InetSocketAddress(port) : new InetSocketAddress(host, port);
      master.bind(bindAddress);
      return master;
    }

If you want to have agents do something on connection, you can change the above to create the RemoteRunMaster as
follows:

    RemoteRunMaster master = new RemoteRunMaster(new AgentConnectionCallback() {
      @Override
      public void agentConnected(AgentConnection agentConnection) {
      }

      @Override
      public void messageReceived(AgentConnection agentConnection, RemoteRun.AgentToMaster message) throws Exception {
      }

      @Override
      public void agentDisconnected(AgentConnection agentConnection) {
      }
    });

To obtain the list of connected agents at a later point, you can use RemoteRunMaster.getConnectedClients:

    for(AgentConnection connection : master.getConnectedClients()) {
    }

Once you have an AgentConnection object, you can run a command on an agent as follows:

    RemoteRun.MasterToAgent.Builder command = MessageHelper.runCommand("/bin/echo", "Hello World!");
    TextOutputCallback callback = new TextOutputCallback() {
      @Override
      public void onStdOut(String line) {
        // called for each line of stdout
      }

      @Override
      public void onStdErr(String line) {
        // called for each line of stderr
      }

      @Override
      public void onExit(int exitCode, String exitReason) {
        // called when process has exited and all stdout/stderr has been processed
      }
    };
    TextOutputRequest request = new TextOutputRequest(command, callback);
    // Note that there are a couple of other constructors in TextOutputRequest including max line length and charset
    // These default to 4096 and UTF-8 respectively.
    connection.request(request);

To send a file or directory to the agent:

    connection.upload(sourcePath, targetRootDirectory, new UploadCompleteCallback() {
      @Override
      public void uploadComplete(AgentConnection agent, long requestId, String targetPath, boolean success) {
        // do something on upload completion
      }
    });

To request a file or directory from the agent:

    connection.download(remoteSource, targetRootDirectory, new FileDownloadCallback() {
      @Override
      public void onExit(int exitCode, String exitReason) {
        // do something on download completion
      }
    }));


## System Properties

Bi-directional SSL is mandatory (i.e. client and server trust each other).  These properties allow you to point
remoterun at your key / trusted certificate stores.

 + javax.net.ssl.keyStore - path to keystore (i.e. keytool file containing master/agent certificate and key), defaults
   to "ssl/agent1-keystore.jks" or "ssl/server-keystore.jks" for agent and master respectively
 + javax.net.ssl.keyStoreType - JKS (Java Key Store) or PKCS12, defaults to JKS
 + javax.net.ssl.keyStorePassword - keystore and key passwords, defaults to "123456"
 + javax.net.ssl.trustStore - path to truststore (i.e. keytool file containing the certification authority public cert),
   defaults to "ssl/ca-truststore.jks"
 + javax.net.ssl.trustStoreType - JKS (Java Key Store) or PKCS12, defaults to JKS
 + javax.net.ssl.trustStorePassword - keystore password, defaults to "123456"

## Running the example server

There is a bundled example command line interface service in net.formicary.remoterun.examples.Server.
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

## Licensing

Copyright 2015 Formicary Ltd

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.