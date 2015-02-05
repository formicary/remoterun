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

import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Chris Pearson
 */
public class IoUtils {
  private static final Logger log = LoggerFactory.getLogger(IoUtils.class);
  private static final int BUFFER_SIZE = 1024;

  public static void closeQuietly(Closeable closeable) {
    if(closeable != null) {
      try {
        closeable.close();
      } catch(Exception e) {
        log.trace("Failed to close closeable " + closeable, e);
      }
    }
  }

  public static int copy(InputStream inputStream, OutputStream outputStream) throws IOException {
    int total = 0;
    byte[] buffer = new byte[BUFFER_SIZE];
    int count;
    while((count = inputStream.read(buffer, 0, BUFFER_SIZE)) != -1) {
      outputStream.write(buffer, 0, count);
      total += count;
    }
    return total;
  }
}
