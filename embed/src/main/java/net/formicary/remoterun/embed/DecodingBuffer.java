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

package net.formicary.remoterun.embed;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

import static java.lang.Math.min;

/**
 * @author Chris Pearson
 */
public class DecodingBuffer {
  private static final int DEFAULT_MAX_LINE_LENGTH = 1024;
  private final DataCallback callback;
  private final CharBuffer charBuffer;
  private final ByteBuffer byteBuffer;
  private final CharsetDecoder charsetDecoder;
  private long count = 0;
  private char lastChar = 0;

  public DecodingBuffer(DataCallback callback) {
    this(callback, StandardCharsets.UTF_8, DEFAULT_MAX_LINE_LENGTH);
  }

  public DecodingBuffer(DataCallback callback, int maxLineLength) {
    this(callback, StandardCharsets.UTF_8, maxLineLength);
  }

  public DecodingBuffer(DataCallback callback, Charset charset, int maxLineLength) {
    this.charsetDecoder = charset.newDecoder()
      .onMalformedInput(CodingErrorAction.REPLACE)
      .onUnmappableCharacter(CodingErrorAction.REPLACE);
    this.charBuffer = CharBuffer.allocate(maxLineLength);
    this.byteBuffer = ByteBuffer.allocate(maxLineLength);
    this.callback = callback;
  }

  public long getCount() {
    return count;
  }

  public void write(byte[] fragment) {
    write(fragment, 0, fragment.length);
  }

  public void write(byte[] fragment, int offset, int length) {
    int written = 0;
    while(written != length) {
      int writeSize = min(byteBuffer.remaining(), length - written);
      byteBuffer.put(fragment, offset + written, writeSize);
      written += writeSize;
      decode(false);
      if(byteBuffer.remaining() == 0) {
        throw new BufferOverflowException();
      }
      String line;
      while((line = readLine()) != null) {
        callback.lineRead(line);
      }
    }
  }

  private void decode(boolean endOfInput) {
    byteBuffer.flip();
    try {
      charsetDecoder.decode(byteBuffer, charBuffer, endOfInput);
    } finally {
      byteBuffer.compact();
    }
  }

  /**
   * Expects charBuffer to be ready for writing (limit == capacity, position == amountOfDataInBuffer).
   *
   * @return a line of data, if one is available
   */
  private String readLine() {
    String line = null;
    charBuffer.flip();
    for(int i = 0; i < charBuffer.remaining(); i++) {
      char c = charBuffer.charAt(i);
      if(lastChar == '\r' && c == '\n') {
        // this is the second part of a line break, ignore
        charBuffer.position(i + 1);
        count++;
      } else if(c == '\r' || c == '\n') {
        line = charBuffer.subSequence(0, i).toString();
        charBuffer.position(i + 1);
        count += i + 1;
      }
      lastChar = c;
      if(line != null) {
        break;
      }
    }
    if((charBuffer.position() == 0 && charBuffer.limit() == charBuffer.capacity())) {
      // the buffer is full - but no line breaks
      line = charBuffer.toString();
      count += charBuffer.limit();
      charBuffer.limit(0);
    }
    charBuffer.compact();
    return line;
  }

  public void close() {
    decode(true);
    if(charBuffer.position() > 0) {
      charBuffer.flip();
      callback.lineRead(charBuffer.toString());
      count += charBuffer.limit();
      charBuffer.compact();
    }
  }

  public static interface DataCallback {
    void lineRead(String line);
  }
}
