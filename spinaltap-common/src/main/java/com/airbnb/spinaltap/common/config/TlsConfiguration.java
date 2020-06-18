/**
 * Copyright 2019 Airbnb. Licensed under Apache-2.0. See License in the project root for license
 * information.
 */
package com.airbnb.spinaltap.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.FileInputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TlsConfiguration {
  @JsonProperty("key_store_file_path")
  private String keyStoreFilePath;

  @JsonProperty("key_store_password")
  private String keyStorePassword;

  @JsonProperty("key_store_type")
  private String keyStoreType;

  @JsonProperty("trust_store_file_path")
  private String trustStoreFilePath;

  @JsonProperty("trust_store_password")
  private String trustStorePassword;

  @JsonProperty("trust_store_type")
  private String trustStoreType;

  public KeyManagerFactory getKeyManagerFactory() throws Exception {
    if (keyStoreFilePath != null && keyStorePassword != null) {
      KeyStore keyStore =
          KeyStore.getInstance(keyStoreType == null ? KeyStore.getDefaultType() : keyStoreType);
      keyStore.load(new FileInputStream(keyStoreFilePath), keyStorePassword.toCharArray());
      KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());
      return keyManagerFactory;
    }
    return null;
  }

  public KeyManager[] getKeyManagers() throws Exception {
    KeyManagerFactory keyManagerFactory = getKeyManagerFactory();
    return keyManagerFactory == null ? null : keyManagerFactory.getKeyManagers();
  }

  public TrustManagerFactory getTrustManagerFactory() throws Exception {
    if (trustStoreFilePath != null && trustStorePassword != null) {
      KeyStore keyStore =
          KeyStore.getInstance(trustStoreType == null ? KeyStore.getDefaultType() : trustStoreType);
      keyStore.load(new FileInputStream(trustStoreFilePath), trustStorePassword.toCharArray());
      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(keyStore);
      return trustManagerFactory;
    }
    return null;
  }

  public TrustManager[] getTrustManagers() throws Exception {
    TrustManagerFactory trustManagerFactory = getTrustManagerFactory();
    return trustManagerFactory == null ? null : trustManagerFactory.getTrustManagers();
  }
}
