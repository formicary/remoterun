package com.twock.remoterun.common;

/**
 * @author Chris Pearson
 */
public class RemoteRunException extends RuntimeException {
  public RemoteRunException(String message) {
    super(message);
  }

  public RemoteRunException(String message, Throwable cause) {
    super(message, cause);
  }
}
