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

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import javax.net.ssl.*;

import static net.formicary.remoterun.common.IoUtils.closeQuietly;

/**
 * @author Chris Pearson
 */
public abstract class KeyStoreUtil {
  private KeyStoreUtil() {
  }

  public static KeyManager[] createKeyStore(String defaultKeyStoreType, String defaultKeyStore, String defaultKeyStorePassword) {
    String keyStoreFile = System.getProperty("javax.net.ssl.keyStore", defaultKeyStore);
    String keyStoreType = System.getProperty("javax.net.ssl.keyStoreType", defaultKeyStoreType);
    String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword", defaultKeyStorePassword);
    InputStream in = null;
    try {
      KeyStore keyStore = KeyStore.getInstance(keyStoreType);
      in = new FileInputStream(keyStoreFile);
      keyStore.load(in, keyStorePassword.toCharArray());
      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("PKIX", "SunJSSE");
      keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());
      return keyManagerFactory.getKeyManagers();
    } catch(Exception e) {
      throw new RemoteRunException("Failed to create keyStore from " + keyStoreFile, e);
    } finally {
      closeQuietly(in);
    }
  }

  public static TrustManager[] createTrustStore(String defaultTrustStoreType, String defaultTrustStore, String defaultTrustStorePassword) {
    String trustStoreFile = System.getProperty("javax.net.ssl.trustStore", defaultTrustStore);
    String trustStoreType = System.getProperty("javax.net.ssl.trustStoreType", defaultTrustStoreType);
    String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword", defaultTrustStorePassword);
    InputStream in = null;
    try {
      KeyStore keyStore = KeyStore.getInstance(trustStoreType);
      in = new FileInputStream(trustStoreFile);
      keyStore.load(in, trustStorePassword.toCharArray());
      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX", "SunJSSE");
      trustManagerFactory.init(keyStore);
      return trustManagerFactory.getTrustManagers();
    } catch(Exception e) {
      throw new RemoteRunException("Failed to create trustStore from " + trustStoreFile, e);
    } finally {
      closeQuietly(in);
    }
  }
}
