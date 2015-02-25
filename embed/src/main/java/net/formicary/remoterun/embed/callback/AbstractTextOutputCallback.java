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

package net.formicary.remoterun.embed.callback;

/**
 * Convenience class to avoid having to implement all methods in TextOutputCallback.
 */
public abstract class AbstractTextOutputCallback implements TextOutputCallback {
  @Override
  public void onStdOut(String line) {

  }

  @Override
  public void onStdErr(String line) {

  }

  @Override
  public void onExit(int exitCode, String exitReason) {

  }
}
