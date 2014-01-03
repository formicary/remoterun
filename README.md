# RemoteRun
By Chris Pearson <chris at twock.com>

## Introduction

RemoteRun is a project created because I couldn't find any suitable alternative for running processes remotely without
resorting to SSH etc which is often unavailable to processes in larger (specifically financial) organisations.  Remote
services are generally permitted if deployed and managed appropriately, but SSH keys etc are often not.  There are
various options available but all seem to be overly complicated, so remoterun is aimed at literally permitting execution
of a remote command and nothing more.  This doesn't prohibit file transfers either: cat is your friend.

RemoteRun may be desirable when one or more of the following apply:

+ a large Java application (e.g. webserver) wants to fork sub-processes, and duplicating the memory footprint is an
  issue
+ you need to run processes on another server

The idea is that you:

+ embed an instance of com.twock.remoterun.embed.RemoteRunMaster in your application which listens for agent connections
+ on servers you want to run applications you start a com.twock.remoterun.agent.RemoteRunAgent instance

remoterun requires SSL client authentication: I wanted a way of running services without usernames/passwords but with
some form of authentication.  So the keystores are password protected, and this must be specified as a system property.
It doesn't currently use CRLs unless any of the JDK implementations automatically do that (which I think they probably
do).

## Running the example

The recommended way to get started with remoterun is to run the example then have a look at NettyServer for use in your
application.  There is a bundled example service in com.twock.remoterun.examples.Server.  To run it:

+ Generate SSL certificates by running ssl/certs.sh
+ run com.twock.remoterun.examples.Server
+ run com.twock.remoterun.agent.RemoteRunAgent
+ issue commands on the Server console as follows:
    + l - list connections
    + r <cmd> [arg [arg [...]]] - run a remote command
    + i <requestId> <stdinText> - send input to stdin of process
    + c <requestId> - close stdin of process

### Example output from immediate exit
    08:13:49.009 [main] INFO  c.t.remoterun.embed.RemoteRunMaster - Listening for connections on 0.0.0.0/0.0.0.0:1081
    08:13:58.063 [New I/O worker #1] INFO  c.t.remoterun.embed.RemoteRunMaster - Agent connected from /127.0.0.1:51540 (1 open connections)
    08:13:58.461 [New I/O worker #1] INFO  c.t.remoterun.embed.RemoteRunMaster - Agent connection complete from /127.0.0.1:51540 (CN=agent1, O=Twock, L=Potters Bar, ST=Hertfordshire, C=GB)
    l
    08:14:08.860 [main] INFO  com.twock.remoterun.examples.Server - 1: CONNECTED /127.0.0.1:51540
    08:14:08.860 [main] INFO  com.twock.remoterun.examples.Server - Finished listing 1 agent connections
    r echo hello
    08:14:14.149 [main] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] WRITE: messageType: RUN_COMMAND requestId: 1 runCommand {   cmd: "echo"   args: "hello" }
    08:14:14.300 [New I/O worker #1] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] RECEIVED: messageType: STARTED requestId: 1
    08:14:14.301 [New I/O worker #1] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] RECEIVED: messageType: STDOUT_FRAGMENT requestId: 1 fragment: "hello\n"
    08:14:14.302 [New I/O worker #1] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] RECEIVED: messageType: EXITED requestId: 1 exitCode: 0

### Example output from longer running process
    l
    08:15:39.448 [main] INFO  com.twock.remoterun.examples.Server - 1: CONNECTED /127.0.0.1:51540
    08:15:39.448 [main] INFO  com.twock.remoterun.examples.Server - Finished listing 1 agent connections
    r cat
    08:15:41.266 [main] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] WRITE: messageType: RUN_COMMAND requestId: 2 runCommand {   cmd: "cat" }
    08:15:41.280 [New I/O worker #1] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] RECEIVED: messageType: STARTED requestId: 2
    i 2 hello\n
    08:15:49.565 [main] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] WRITE: messageType: STDIN_FRAGMENT requestId: 2 stdinFragment: "hello\n"
    08:15:49.568 [New I/O worker #1] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] RECEIVED: messageType: STDOUT_FRAGMENT requestId: 2 fragment: "hello\n"
    c 2
    08:16:00.580 [main] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] WRITE: messageType: CLOSE_STDIN requestId: 2
    08:16:00.584 [New I/O worker #1] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0xe45373eb, /127.0.0.1:51540 => /127.0.0.1:1081] RECEIVED: messageType: EXITED requestId: 2 exitCode: 0


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

