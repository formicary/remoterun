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

import java.util.List;

import net.formicary.remoterun.common.proto.RemoteRun;

/**
 * Convenience interface for constants applicable to both agents and masters.
 *
 * @author Chris Pearson
 */
public final class RemoteRunHelper {
  /**
   * Name of the agent system property set on startup with the value of
   * {@link java.lang.management.RuntimeMXBean#getName() ManagementFactory.getRuntimeMXBean().getName()}.
   * On typical Sun/Oracle JVMs this is "<code>&lt;PID&gt;@&lt;hostname&gt;</code>".
   */
  public static final String AGENT_NAME_SYSTEM_PROPERTY = "remoterun.agent.name";

  private RemoteRunHelper() {
  }

  /**
   * Look up the specified key in the provided list.
   *
   * @return the first found value associated with the given key
   */
  public static String getProperty(String key, String defaultValue, List<RemoteRun.StringStringKeyValuePair> list) {
    return getProperty(key, defaultValue, list, null);
  }

  /**
   * Look up the specified key in the provided lists.
   *
   * @return the first found value associated with the given key
   */
  public static String getProperty(String key, String defaultValue, List<RemoteRun.StringStringKeyValuePair> list1, List<RemoteRun.StringStringKeyValuePair> list2) {
    if(list1 != null) {
      for(RemoteRun.StringStringKeyValuePair pair : list1) {
        if(key.equals(pair.getKey())) {
          return pair.getValue();
        }
      }
    }
    if(list2 != null) {
      for(RemoteRun.StringStringKeyValuePair pair : list2) {
        if(key.equals(pair.getKey())) {
          return pair.getValue();
        }
      }
    }
    return defaultValue;
  }
}
