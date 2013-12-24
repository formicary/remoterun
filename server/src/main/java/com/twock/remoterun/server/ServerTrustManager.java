package com.twock.remoterun.server;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

/**
 * @author Chris Pearson
 */
public class ServerTrustManager implements X509TrustManager {
  private final X509TrustManager trustManager;

  public ServerTrustManager(X509TrustManager trustManager) {
    this.trustManager = trustManager;
  }

  public X509TrustManager getTrustManager() {
    return trustManager;
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
    try {
      trustManager.checkClientTrusted(chain, authType);
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
}
