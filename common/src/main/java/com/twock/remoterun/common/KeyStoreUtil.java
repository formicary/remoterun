package com.twock.remoterun.common;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.apache.commons.io.IOUtils;

/**
 * @author Chris Pearson
 */
public abstract class KeyStoreUtil {
  private static final String DEFAULT_KEYSTORE = "ssl/ca-keystore.jks";
  private static final String DEFAULT_KEYSTORE_TYPE = "JKS";
  private static final String DEFAULT_KEYSTORE_PASSWORD = "123456";

  private KeyStoreUtil() {
  }

  public static KeyManager[] createKeyStore() {
    String keyStoreFile = System.getProperty("javax.net.ssl.keyStore", DEFAULT_KEYSTORE);
    String keyStoreType = System.getProperty("javax.net.ssl.keyStoreType", DEFAULT_KEYSTORE_TYPE);
    String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword", DEFAULT_KEYSTORE_PASSWORD);
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
}
