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
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
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

  public FileReceiver(Path root) throws IOException {
    this.root = root;
    pipedOutputStream = new PipedOutputStream();
    zipInputStream = new ZipInputStream(new PipedInputStream(pipedOutputStream));
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
        String extraText = extraBytes == null || extraBytes.length == 0 ? null : new String(extraBytes, Charsets.UTF_8);
        Path newPath = root.resolve(entry.getName());
        if(entry.isDirectory()) {
          log.info("Would create directory {}", newPath);
        } else {
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          IOUtils.copy(zipInputStream, baos);
          log.info("Would write {} bytes to file {} with permissions={}", baos.toByteArray().length, newPath, extraText);
        }
      }
      log.warn("Finished receiving");
    } catch(Exception e) {
      log.warn(entry == null ? "Failed whilst reading zip" : "Failed whilst reading " + entry.getName(), e);
      failure = e;
    } finally {
      finished = true;
    }
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

  @Override
  public void close() throws IOException {
    if(!closed) {
      IOUtils.closeQuietly(zipInputStream);
      IOUtils.closeQuietly(pipedOutputStream);
      closed = true;
    }
  }
}
