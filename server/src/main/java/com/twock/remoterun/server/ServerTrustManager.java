package com.twock.remoterun.server;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.twock.remoterun.common.RemoteRunException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Chris Pearson
 */
public class ServerTrustManager implements X509TrustManager {
  private static final Logger log = LoggerFactory.getLogger(ServerTrustManager.class);
  private static final String DEFAULT_TRUSTSTORE = "ssl/ca-truststore.jks";
  private static final String DEFAULT_TRUSTSTORE_TYPE = "JKS";
  private static final String DEFAULT_TRUSTSTORE_PASSWORD = "123456";
  private final X509TrustManager trustManager;
  private Callback callback;

  public ServerTrustManager() {
    this(null);
  }

  public ServerTrustManager(Callback callback) {
    this.callback = callback;
    String trustStoreFile = System.getProperty("javax.net.ssl.trustStore", DEFAULT_TRUSTSTORE);
    String trustStoreType = System.getProperty("javax.net.ssl.trustStoreType", DEFAULT_TRUSTSTORE_TYPE);
    String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword", DEFAULT_TRUSTSTORE_PASSWORD);
    InputStream in = null;
    try {
      KeyStore trustStore = KeyStore.getInstance(trustStoreType);
      in = new FileInputStream(trustStoreFile);
      trustStore.load(in, trustStorePassword.toCharArray());
      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX", "SunJSSE");
      trustManagerFactory.init(trustStore);
      trustManager = (X509TrustManager)trustManagerFactory.getTrustManagers()[0];
    } catch(Exception e) {
      throw new RemoteRunException("Failed to create " + getClass().getName(), e);
    } finally {
      IOUtils.closeQuietly(in);
    }
  }

  public Callback getCallback() {
    return callback;
  }

  public void setCallback(Callback callback) {
    this.callback = callback;
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
    try {
      trustManager.checkClientTrusted(chain, authType);
      if(callback != null) {
        callback.verifyClientCertificate(chain, authType);
      }
    } catch(CertificateException e) {
      throw e;
    }
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
    trustManager.checkServerTrusted(chain, authType);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return trustManager.getAcceptedIssuers();
  }

  public static interface Callback {
    /**
     * Check client certificate is valid for the application, throw an exception if not.
     */
    void verifyClientCertificate(X509Certificate[] chain, String authType) throws CertificateException;
  }
}
