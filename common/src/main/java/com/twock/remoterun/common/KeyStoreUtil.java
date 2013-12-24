package com.twock.remoterun.common;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import javax.net.ssl.*;

import org.apache.commons.io.IOUtils;

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
      IOUtils.closeQuietly(in);
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
      IOUtils.closeQuietly(in);
    }
  }
}
