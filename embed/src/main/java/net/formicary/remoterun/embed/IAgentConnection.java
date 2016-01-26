package net.formicary.remoterun.embed;

import java.nio.file.Path;

import net.formicary.remoterun.common.proto.RemoteRun;
import net.formicary.remoterun.embed.callback.FileDownloadCallback;
import net.formicary.remoterun.embed.callback.UploadCompleteCallback;
import net.formicary.remoterun.embed.request.AgentRequest;

/**
 * @author Hani Suleiman
 */
public interface IAgentConnection {
  ConnectionState getConnectionState();

  void setConnectionState(ConnectionState connectionState);

  RemoteRun.AgentToMaster.AgentInfo getAgentInfo();

  void setAgentInfo(RemoteRun.AgentToMaster.AgentInfo agentInfo);

  /**
   * Disconnect this agent.
   */
  void shutdown();

  /**
   * Initiate the upload of a file from master to agent.
   *
   * @param localSourcePath path to read and send on this host
   * @param remoteTargetDirectory where to try and store the data on the target
   * @param callback callback when the send is complete, can be null
   * @return unique request ID
   */
  long upload(Path localSourcePath, String remoteTargetDirectory, UploadCompleteCallback callback);

  long download(String remoteSourcePath, Path localTargetDirectory, FileDownloadCallback callback);

  long request(AgentRequest message);

  /**
   * Transmit a message that has already been given a unique request ID, and commit to handling the responses yourself
   * with the AgentConnectionCallback registered.
   */
  void write(RemoteRun.MasterToAgent message);
}
