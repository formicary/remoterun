package com.twock.remoterun.client.process;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.twock.remoterun.common.proto.RemoteRun.ClientToServer.MessageType.*;

/**
 * @author Chris Pearson
 */
public class ProcessHelper {
  private final long serverId;
  private final Process process;
  private final OutputReader stdout;
  private final OutputReader stderr;
  private Thread stdoutThread;
  private Thread stderrThread;

  public ProcessHelper(long serverId, String cmd, List<String> argsList, ReadCallback callback) throws IOException {
    this.serverId = serverId;

    List<String> command = new ArrayList<>(argsList.size());
    command.add(cmd);
    command.addAll(argsList);
    String[] cmdArray = command.toArray(new String[command.size()]);

    process = Runtime.getRuntime().exec(cmdArray);
    stdout = new OutputReader(process.getInputStream(), serverId, STDOUT_FRAGMENT, callback);
    stderr = new OutputReader(process.getErrorStream(), serverId, STDERR_FRAGMENT, callback);
  }

  public void startReadingOutput() {
    stdoutThread = startReaderThread(stdout);
    stderrThread = startReaderThread(stderr);
  }

  private Thread startReaderThread(OutputReader t) {
    Thread thread = new Thread(t);
    thread.setName("Process " + t.getServerId() + " " + t.getType().name().substring(0, 6) + " reader");
    thread.start();
    return thread;
  }

  public long getServerId() {
    return serverId;
  }

  public Process getProcess() {
    return process;
  }

  public boolean isFinished() {
    return stdout.isFinished() && stderr.isFinished();
  }
}
