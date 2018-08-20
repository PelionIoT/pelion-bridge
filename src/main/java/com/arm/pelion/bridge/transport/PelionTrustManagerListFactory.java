/**
 * @file PelionHostnameVerifier.java
 * @brief Pelion Hostname Verifier (SSL)
 * @author Doug Anson
 * @version 1.0
 * @see
 *
 * Copyright 2015-2018. ARM Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.arm.pelion.bridge.transport;

import com.arm.pelion.bridge.core.BaseClass;
import com.arm.pelion.bridge.core.ErrorLogger;
import com.arm.pelion.bridge.preferences.PreferenceManager;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Pelion TrustManager List factory
 * @author Doug Anson
 */
public class PelionTrustManagerListFactory extends BaseClass implements X509TrustManager {
    private TrustManager m_list[] = null;
    private TrustManagerFactory m_tmf = null;
    private X509TrustManager m_x509Tm = null;
    
    // default constructor
    public PelionTrustManagerListFactory(ErrorLogger error_logger, PreferenceManager preference_manager) {
        super(error_logger,preference_manager);
        this.m_list = null;
        
        try {
            // get the default trust manager factory..
            this.m_tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            
            // Using null here initialises the TMF with the default trust store.
            this.m_tmf.init((KeyStore) null);
            
            // get the default trust managers
            this.m_x509Tm = null;
            for (TrustManager tm : this.m_tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    this.m_x509Tm = (X509TrustManager) tm;
                    break;
                }
            }
        }
        catch (KeyStoreException | NoSuchAlgorithmException ex) {
            this.errorLogger().critical("PelionTrustManagerListFactory: Exception caught: " + ex.getMessage(),ex);
        }
    }
    
    // create the TrustManager List
    public TrustManager[] create() {
        if (this.m_list == null) {
            // hook our class into the default trust manager...
            this.m_list = new TrustManager[]{this};
        }
        return this.m_list;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String auth_type) throws CertificateException {
        if (this.m_x509Tm != null) {
            this.m_x509Tm.checkClientTrusted(chain, auth_type);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String auth_type) throws CertificateException {
        if (this.m_x509Tm != null) {
            this.m_x509Tm.checkServerTrusted(chain, auth_type);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        if (this.m_x509Tm != null) {
            return this.m_x509Tm.getAcceptedIssuers();
        }
        return null;
    }
}
