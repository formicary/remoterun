RemoteRun
=========

By Chris Pearson <chris at twock.com>

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

+ embed com.twock.remoterun.server.NettyServer in your application
+ on servers you want to run applications you start a com.twock.remoterun.client.Client instance

remoterun requires SSL client authentication: I wanted a way of running services without usernames/passwords but with
some form of authentication.  It doesn't currently use CRLs unless any of the JDK implementations automatically do that
(which I think they probably do).

Running the example
-------------------

The recommended way to get started with remoterun is to run the example then have a look at NettyServer for use in your
application.  There is a bundled example service in com.twock.remoterun.server.Server.  To run it:

+ Generate SSL certificates by running ssl/certs.sh
+ run com.twock.remoterun.server.Server
+ run com.twock.remoterun.client.Client
+ issue commands as follows:
    + l - list connections
    + r <cmd> [arg [arg [...]]] - run a remote command
    + i <requestId> <stdinText> - send input to stdin of process
    + c <requestId> - close stdin of process

### Example Output
    21:56:09.607 [main] INFO  c.twock.remoterun.server.NettyServer - Listening for connections on 0.0.0.0/0.0.0.0:1081
    21:56:17.525 [New I/O worker #1] INFO  c.twock.remoterun.server.NettyServer - Client connected from /127.0.0.1:50495 (1 open connections)
    21:56:17.742 [New I/O worker #1] INFO  c.twock.remoterun.server.NettyServer - Client connection complete from /127.0.0.1:50495 (CN=client1, O=Twock, L=Potters Bar, ST=Hertfordshire, C=GB)
    l
    21:56:23.800 [main] INFO  com.twock.remoterun.server.Server - 1: CONNECTED /127.0.0.1:50495
    21:56:23.800 [main] INFO  com.twock.remoterun.server.Server - Finished listing 1 client connections
    r cat
    21:56:27.768 [main] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] WRITE: messageType: RUN_COMMAND requestId: 1 runCommand {   cmd: "cat" }
    21:56:27.899 [New I/O worker #1] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] RECEIVED: messageType: STARTED requestId: 1
    i 1 hello\n
    21:56:40.697 [main] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] WRITE: messageType: STDIN_FRAGMENT requestId: 1 stdinFragment: "hello\n"
    21:56:40.701 [New I/O worker #1] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] RECEIVED: messageType: STDOUT_FRAGMENT requestId: 1 fragment: "hello\n"
    c 1
    21:56:46.610 [main] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] WRITE: messageType: CLOSE_STDIN requestId: 1
    21:56:46.614 [New I/O worker #1] DEBUG c.t.r.common.NettyLoggingHandler - [id: 0x01977fc5, /127.0.0.1:50495 => /127.0.0.1:1081] RECEIVED: messageType: EXITED requestId: 1 exitCode: 0

System Properties
-----------------
 + javax.net.ssl.keyStore - path to keystore (i.e. keytool file containing server/client certificate and key), defaults
   to "ssl/client-keystore.jks" or "ssl/server-keystore.jks" for client and server respectively
 + javax.net.ssl.keyStoreType - JKS (Java Key Store) or PKCS12, defaults to JKS
 + javax.net.ssl.keyStorePassword - keystore and key passwords, defaults to "123456"
 + javax.net.ssl.trustStore - path to truststore (i.e. keytool file containing the certification authority public cert),
   defaults to "ssl/ca-truststore.jks"
 + javax.net.ssl.trustStoreType - JKS (Java Key Store) or PKCS12, defaults to JKS
 + javax.net.ssl.trustStorePassword - keystore password, defaults to "123456"
