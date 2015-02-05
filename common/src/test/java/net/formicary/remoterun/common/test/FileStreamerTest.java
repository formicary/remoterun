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

package net.formicary.remoterun.common.test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;

import net.formicary.remoterun.common.FileReceiver;
import net.formicary.remoterun.common.FileStreamer;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @author Chris Pearson
 */
public class FileStreamerTest {
  private static final Logger log = LoggerFactory.getLogger(FileStreamerTest.class);
  private Path tempDirectory;

  @BeforeClass
  public void setup() throws IOException {
    tempDirectory = Files.createTempDirectory("FileStreamerTest_");
  }

  @AfterClass
  public void tearDown() throws IOException {
    FileUtils.forceDelete(tempDirectory.toFile());
  }

  @Test
  public void testDirectory() throws URISyntaxException, IOException {
    try (FileSystem zipfs = FileSystems.newFileSystem(Paths.get(getClass().getResource("/example_directory.zip").toURI()), null);
         FileReceiver fileReceiver = new FileReceiver(tempDirectory)) {
      new Thread(fileReceiver).start();
      new FileStreamer(zipfs.getPath("/example"), new MyFileStreamerCallback(fileReceiver)).run();
      fileReceiver.waitUntilFinishedUninterruptably();
      Assert.assertTrue(fileReceiver.success(), fileReceiver.getFailureMessage());
    }
  }

  @Test
  public void testFile() throws URISyntaxException, IOException {
    try (FileReceiver fileReceiver = new FileReceiver(tempDirectory)) {
      new Thread(fileReceiver).start();
      new FileStreamer(Paths.get(getClass().getResource("/example_directory.zip").toURI()), new MyFileStreamerCallback(fileReceiver)).run();
      fileReceiver.waitUntilFinishedUninterruptably();
      Assert.assertTrue(fileReceiver.success(), fileReceiver.getFailureMessage());
    }
  }

  private static class MyFileStreamerCallback implements FileStreamer.FileStreamerCallback {
    private final FileReceiver fileReceiver;

    public MyFileStreamerCallback(FileReceiver fileReceiver) {
      this.fileReceiver = fileReceiver;
    }

    @Override
    public void writeDataChunk(byte[] data, int offset, int length) {
      try {
        fileReceiver.getPipedOutputStream().write(data, offset, length);
      } catch(IOException e) {
        throw new RuntimeException("Failed to write data chunk of " + length + " bytes", e);
      }
    }

    @Override
    public void finished(boolean success, String errorMessage, Throwable cause) {
      try {
        fileReceiver.getPipedOutputStream().close();
      } catch(IOException e) {
        throw new RuntimeException("Failed to close piped output stream", e);
      }
      if (!success) {
        log.error("Finished streaming files but failed: " + errorMessage, cause);
      }
      Assert.assertTrue(success, errorMessage);
    }
  }
}
