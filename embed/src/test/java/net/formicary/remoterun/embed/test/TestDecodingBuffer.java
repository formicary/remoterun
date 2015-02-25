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

package net.formicary.remoterun.embed.test;

import net.formicary.remoterun.embed.DecodingBuffer;
import org.testng.annotations.Test;

import static java.nio.charset.StandardCharsets.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

/**
 * @author Chris Pearson
 */
public class TestDecodingBuffer {
  @Test
  public void testEmptyBuffer() {
    DecodingBuffer.DataCallback mock = mock(DecodingBuffer.DataCallback.class);
    DecodingBuffer buffer = new DecodingBuffer(mock, 10);
    buffer.close();
    verifyZeroInteractions(mock);
    assertEquals(buffer.getCount(), 0);
  }

  @Test
  public void testParseOnClose() {
    DecodingBuffer.DataCallback mock = mock(DecodingBuffer.DataCallback.class);
    DecodingBuffer buffer = new DecodingBuffer(mock, 10);
    verifyZeroInteractions(mock);
    buffer.write("abc".getBytes(UTF_8));
    verifyZeroInteractions(mock);
    assertEquals(buffer.getCount(), 0);
    buffer.close();
    verify(mock).lineRead("abc");
    verifyNoMoreInteractions(mock);
    assertEquals(buffer.getCount(), 3);
  }

  @Test
  public void testMultipleLineBreaks() {
    DecodingBuffer.DataCallback mock = mock(DecodingBuffer.DataCallback.class);
    DecodingBuffer buffer = new DecodingBuffer(mock, 10);
    verifyZeroInteractions(mock);
    buffer.write("a\nb\nc\n".getBytes(UTF_8));
    verify(mock).lineRead("a");
    verify(mock).lineRead("b");
    verify(mock).lineRead("c");
    assertEquals(buffer.getCount(), 6);
    buffer.close();
    verifyNoMoreInteractions(mock);
    assertEquals(buffer.getCount(), 6);
  }

  @Test
  public void testParseOnCloseLineBreaks() {
    DecodingBuffer.DataCallback mock = mock(DecodingBuffer.DataCallback.class);
    DecodingBuffer buffer = new DecodingBuffer(mock, 10);
    verifyZeroInteractions(mock);
    buffer.write("a\nb\nc".getBytes(UTF_8));
    verify(mock).lineRead("a");
    verify(mock).lineRead("b");
    verifyNoMoreInteractions(mock);
    assertEquals(buffer.getCount(), 4);
    buffer.close();
    verify(mock).lineRead("c");
    verifyNoMoreInteractions(mock);
    assertEquals(buffer.getCount(), 5);
  }

  @Test
  public void testOffsetLength() {
    DecodingBuffer.DataCallback mock = mock(DecodingBuffer.DataCallback.class);
    DecodingBuffer buffer = new DecodingBuffer(mock, 10);
    verifyZeroInteractions(mock);
    byte[] bytes = "a\nb\nc".getBytes(UTF_8);
    buffer.write(bytes, 3, bytes.length - 3);
    verify(mock).lineRead("");
    verifyNoMoreInteractions(mock);
    assertEquals(buffer.getCount(), 1);
    buffer.close();
    verify(mock).lineRead("c");
    verifyNoMoreInteractions(mock);
    assertEquals(buffer.getCount(), 2);
  }

  @Test
  public void testPartialDecode() {
    DecodingBuffer.DataCallback mock = mock(DecodingBuffer.DataCallback.class);
    DecodingBuffer buffer = new DecodingBuffer(mock, UTF_16, 20);
    verifyZeroInteractions(mock);
    byte[] bytes = "a\nb\n".getBytes(UTF_16);
    assertEquals(bytes.length, 10);
    buffer.write(bytes, 0, 3); // BOM plus first byte of "a"
    verifyZeroInteractions(mock);
    assertEquals(buffer.getCount(), 0);
    buffer.write(bytes, 3, 4); // up to first byte of "b"
    verify(mock).lineRead("a");
    assertEquals(buffer.getCount(), 2);
    buffer.write(bytes, 7, 3); // to end
    verify(mock).lineRead("b");
    verifyNoMoreInteractions(mock);
    assertEquals(buffer.getCount(), 4);
    buffer.close();
    verifyNoMoreInteractions(mock);
    assertEquals(buffer.getCount(), 4);
  }
}
