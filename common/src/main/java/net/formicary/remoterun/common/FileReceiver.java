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

package net.formicary.remoterun.common;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.formicary.remoterun.common.IoUtils.closeQuietly;
import static org.jboss.netty.util.CharsetUtil.UTF_8;

/**
 * Normal usage of FileReceiver is:
 * <pre>
 * receiver = new FileReceiver(Files.createTempFile("received_", ".zip"));
 * new Thread(receiver).start();
 * ...
 * // receiving a file/finishing
 * if (transferBytesArrive) {
 *   writeMessageBytesTo(receiver.getPipedOutputStream());
 * } else if(transferFinishedMessageArrives) {
 *   IOUtils.closeQuietly(receiver.getPipedOutputStream());
 *   receiver.waitUntilFinishedUninterruptably();
 * }
 * </pre>
 *
 * @author Chris Pearson
 */
public class FileReceiver implements Runnable, Closeable {
  private static final Logger log = LoggerFactory.getLogger(FileReceiver.class);
  private final Path root;
  private final PipedOutputStream pipedOutputStream;
  private final ZipInputStream zipInputStream;
  private boolean closed = false;
  private boolean finished = false;
  private Throwable failure;
  private String failureMessage;
  private final PipedInputStream pipedInputStream;

  public FileReceiver(Path root) throws IOException {
    this.root = root;
    pipedOutputStream = new PipedOutputStream();
    pipedInputStream = new PipedInputStream(pipedOutputStream);
    zipInputStream = new ZipInputStream(pipedInputStream);
  }

  public PipedOutputStream getPipedOutputStream() {
    return pipedOutputStream;
  }

  @Override
  public void run() {
    ZipEntry entry = null;
    try {
      while((entry = zipInputStream.getNextEntry()) != null) {
        byte[] extraBytes = entry.getExtra();
        Set<PosixFilePermission> permissions = extraBytes == null ? null : PosixFilePermissions.fromString(new String(extraBytes, extraBytes.length - 9, 9, UTF_8));
        Path newPath = root.resolve(entry.getName());
        if(entry.isDirectory()) {
          log.debug("Creating directory {}", newPath);
          if(permissions == null) {
            Files.createDirectory(newPath);
          } else {
            Files.createDirectory(newPath, PosixFilePermissions.asFileAttribute(permissions));
          }
        } else {
          int bytesWritten;
          try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(newPath))) {
            bytesWritten = IoUtils.copy(zipInputStream, outputStream);
          }
          if(permissions != null) {
            Files.setPosixFilePermissions(newPath, permissions);
          }
          log.debug("Written {} bytes to file {} with permissions={}", bytesWritten, newPath, extraBytes == null ? null : new String(extraBytes, UTF_8));
        }
      }
      log.debug("Finished receiving");
    } catch(Exception e) {
      failureMessage = entry == null ? "Failed whilst reading zip" : "Failed whilst reading " + entry.getName();
      failure = e;
      log.warn(failureMessage, e);
    }
    // the piped streams get unhappy if zipInputStream doesn't read right to the end of the zip - believe there's an
    // index that doesn't get read
    try {
      byte[] buffer = new byte[1024];
      int read = 0;
      while(read != -1) {
        read = pipedInputStream.read(buffer);
      }
    } catch(Exception e) {
      log.trace("Ignoring error reading last of stream", e);
    }
    // close the streams, and mark as finished
    closeQuietly(this);
    finished = true;
    synchronized(this) {
      notifyAll();
    }
  }

  public synchronized void waitUntilFinishedUninterruptably() {
    while(!finished) {
      try {
        wait();
      } catch(InterruptedException e) {
        log.debug("Ignoring interruption", e);
      }
    }
  }

  public boolean isFinished() {
    return finished;
  }

  public boolean success() {
    return failure == null;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  public Throwable getFailure() {
    return failure;
  }

  public Path getRoot() {
    return root;
  }

  @Override
  public void close() throws IOException {
    if(!closed) {
      closeQuietly(zipInputStream);
      closeQuietly(pipedOutputStream);
      closed = true;
    }
  }
}
