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

package net.formicary.remoterun.common;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jboss.netty.util.CharsetUtil.UTF_8;

/**
 * A class responsible for reading a file or directory and streaming a series of data chunks to a callback.  If any
 * errors are thrown either finding files, or whilst reading files, they are propagated to the callback.
 *
 * @author Chris Pearson
 */
public class FileStreamer implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(FileStreamer.class);
  private static final String IS_DIRECTORY = "isDirectory";
  private static final String LAST_MODIFIED = "lastModifiedTime";
  private static final int MAX_FRAGMENT_SIZE = 1000000; /*TODO: work out best size*/
  private final Path path;
  private final FileStreamerCallback callback;
  private final ZipOutputStream zipOutput;
  private boolean finished = false;

  public FileStreamer(Path path, final FileStreamerCallback callback) {
    this.path = path;
    this.callback = callback;
    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        write(new byte[]{(byte)(b & 0xFF)}, 0, 1);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        callback.writeDataChunk(b, off, len);
      }

      @Override
      public void close() throws IOException {
      }
    }, MAX_FRAGMENT_SIZE);
    zipOutput = new ZipOutputStream(bufferedOutputStream);
  }

  private synchronized void finish(boolean success, String errorMessage, Throwable cause) {
    if(finished) {
      log.warn("Trying to call finished again, despite previous call, ignoring: " + errorMessage, cause);
    } else {
      finished = true;
      try {
        zipOutput.close();
      } catch(Exception e) {
        if(success) {
          success = false;
          errorMessage = "Failed to close output";
          cause = e;
        } else {
          log.debug("Failed to close output when finishing file streaming of " + path, e);
        }
      }
      callback.finished(success, errorMessage, cause);
    }
  }

  @Override
  public void run() {
    try {
      send(path, path.getParent(), zipOutput);
      finish(true, null, null);
    } catch(Exception e) {
      finish(false, "Failed to compress and send " + path.toString(), e);
    }
  }

  private void send(Path path, Path relativeTo, ZipOutputStream zipOutput) throws IOException {
    Map<String, Object> attributes = Files.readAttributes(path, IS_DIRECTORY + ',' + LAST_MODIFIED, LinkOption.NOFOLLOW_LINKS);
    boolean isDirectory = Boolean.TRUE.equals(attributes.get(IS_DIRECTORY));
    FileTime lastModifiedTime = (FileTime)attributes.get(LAST_MODIFIED);

    String relativePath = relativeTo.relativize(path).toString();
    ZipEntry entry = new ZipEntry(isDirectory ? relativePath + '/' : relativePath);
    if(path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      // add "<owner> <group> <permissions>" as a UTF-8 string in extra data
      PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
      PosixFileAttributes posix = view.readAttributes();
      entry.setExtra((posix.owner().getName() + ' ' + posix.group().getName() + ' ' + PosixFilePermissions.toString(posix.permissions())).getBytes(UTF_8));
    }
    entry.setTime(lastModifiedTime.toMillis());
    if(!Files.isSameFile(path, relativeTo)) {
      zipOutput.putNextEntry(entry);
    }
    if(isDirectory) {
      try (DirectoryStream<Path> dir = Files.newDirectoryStream(path)) {
        for(Path child : dir) {
          send(child, relativeTo, zipOutput);
        }
      }
    } else {
      Files.copy(path, zipOutput);
    }
  }

  public static interface FileStreamerCallback {
    void writeDataChunk(byte[] data, int offset, int length);

    void finished(boolean success, String errorMessage, Throwable cause);
  }
}
