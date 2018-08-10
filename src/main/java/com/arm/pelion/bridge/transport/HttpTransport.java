/**
 * @file    HttpTransport.java
 * @brief HTTP Transport Support
 * @author Doug Anson
 * @version 1.0
 * @see
 *
 * Copyright 2015. ARM Ltd. All rights reserved.
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.codec.binary.Base64;

/**
 * HTTP Transport Support
 *
 * @author Doug Anson
 */
public class HttpTransport extends BaseClass {

    private int m_last_response_code = 0;
    private String m_auth_qualifier_default = "bearer";
    private String m_auth_qualifier = this.m_auth_qualifier_default;
    private String m_basic_auth_qualifier = "Basic";
    private String m_etag_value = null;
    private String m_if_match_header_value = null;

    // constructor
    /**
     *
     * @param error_logger
     * @param preference_manager
     */
    public HttpTransport(ErrorLogger error_logger, PreferenceManager preference_manager) {
        super(error_logger, preference_manager);
        String auth_qualifier = this.prefValue("http_auth_qualifier");
        if (auth_qualifier != null && auth_qualifier.length() > 0) {
            this.m_auth_qualifier_default = auth_qualifier;
            this.m_auth_qualifier = this.m_auth_qualifier_default;
        }
        this.errorLogger().info("HTTP: Authorization Qualifier set to: " + this.m_auth_qualifier);
    }

    // set the authorization qualifier
    public void setAuthorizationQualifier(String qualifier) {
        if (qualifier != null && qualifier.length() > 0) {
            this.m_auth_qualifier = qualifier;
        }
    }

    // reset the authorization qualifier
    private void resetAuthorizationQualifier() {
        this.m_auth_qualifier = this.m_auth_qualifier_default;
    }

    // set the ETag value
    public void setETagValue(String etag) {
        this.m_etag_value = etag;
    }

    // reset the ETag value
    private void resetETagValue() {
        this.m_etag_value = null;
    }

    // set the If-Match value
    public void setIfMatchValue(String ifMatch) {
        this.m_if_match_header_value = ifMatch;
    }

    // reset the If-Match value
    private void resetIfMatchValue() {
        this.m_if_match_header_value = null;
    }

    // execute GET over http
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpGet(String url_str, String username, String password, String data, String content_type, String auth_domain) {
        return this.doHTTP("GET", url_str, username, password, data, content_type, auth_domain, true, false, false, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpGetApiTokenAuth(String url_str, String api_token, String data, String content_type, String auth_domain) {
        return this.doHTTP("GET", url_str, null, null, data, content_type, auth_domain, true, false, false, true, api_token);
    }

    // execute GET over https
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpPeristentGet(String url_str, String username, String password, String data, String content_type, String auth_domain) {
        return this.doHTTP("GET", url_str, username, password, data, content_type, auth_domain, true, false, false, false, null, true);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpPersistentGetApiTokenAuth(String url_str, String api_token, String data, String content_type, String auth_domain) {
        return this.doHTTP("GET", url_str, null, null, data, content_type, auth_domain, true, false, false, true, api_token, true);
    }

    // execute GET over https
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpsPeristentGet(String url_str, String username, String password, String data, String content_type, String auth_domain) {
        return this.doHTTP("GET", url_str, username, password, data, content_type, auth_domain, true, false, true, false, null, true);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpsPersistentGetApiTokenAuth(String url_str, String api_token, String data, String content_type, String auth_domain) {
        return this.doHTTP("GET", url_str, null, null, data, content_type, auth_domain, true, false, true, true, api_token, true);
    }

    // execute GET over https
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpsGet(String url_str, String username, String password, String data, String content_type, String auth_domain) {
        return this.doHTTP("GET", url_str, username, password, data, content_type, auth_domain, true, false, true, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpsGetApiTokenAuth(String url_str, String api_token, String data, String content_type, String auth_domain) {
        return this.doHTTP("GET", url_str, null, null, data, content_type, auth_domain, true, false, true, true, api_token);
    }

    // execute POST over https
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpPost(String url_str, String username, String password, String data, String content_type, String auth_domain) {
        return this.doHTTP("POST", url_str, username, password, data, content_type, auth_domain, true, true, false, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpPostApiTokenAuth(String url_str, String api_token, String data, String content_type, String auth_domain) {
        return this.doHTTP("POST", url_str, null, null, data, content_type, auth_domain, true, true, false, true, api_token);
    }

    // execute POST over https
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpsPost(String url_str, String username, String password, String data, String content_type, String auth_domain) {
        return this.doHTTP("POST", url_str, username, password, data, content_type, auth_domain, true, true, true, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpsPostApiTokenAuth(String url_str, String api_token, String data, String content_type, String auth_domain) {
        return this.doHTTP("POST", url_str, null, null, data, content_type, auth_domain, true, true, true, true, api_token);
    }

    // execute PUT over http
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpPut(String url_str, String username, String password, String data, String content_type, String auth_domain) {
        return this.doHTTP("PUT", url_str, username, password, data, content_type, auth_domain, true, true, false, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpPutApiTokenAuth(String url_str, String api_token, String data, String content_type, String auth_domain) {
        return this.doHTTP("PUT", url_str, null, null, data, content_type, auth_domain, true, true, false, true, api_token);
    }

    // execute PUT over https
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpsPut(String url_str, String username, String password, String data, String content_type, String auth_domain) {
        return this.doHTTP("PUT", url_str, username, password, data, content_type, auth_domain, true, true, true, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpsPutApiTokenAuth(String url_str, String api_token, String data, String content_type, String auth_domain) {
        return this.doHTTP("PUT", url_str, null, null, data, content_type, auth_domain, true, true, true, true, api_token);
    }

    // execute PUT over http
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @param auth_domain
     * @param expect_response
     * @return
     */
    public String httpPut(String url_str, String username, String password, String data, String content_type, String auth_domain, boolean expect_response) {
        return this.doHTTP("PUT", url_str, username, password, data, content_type, auth_domain, expect_response, true, false, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @param auth_domain
     * @param expect_response
     * @return
     */
    public String httpPutApiTokenAuth(String url_str, String api_token, String data, String content_type, String auth_domain, boolean expect_response) {
        return this.doHTTP("PUT", url_str, null, null, data, content_type, auth_domain, expect_response, true, false, true, api_token);
    }

    // execute PUT over https
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @param auth_domain
     * @param expect_response
     * @return
     */
    public String httpsPut(String url_str, String username, String password, String data, String content_type, String auth_domain, boolean expect_response) {
        return this.doHTTP("PUT", url_str, username, password, data, content_type, auth_domain, expect_response, true, true, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @param auth_domain
     * @param expect_response
     * @return
     */
    public String httpsPutApiTokenAuth(String url_str, String api_token, String data, String content_type, String auth_domain, boolean expect_response) {
        return this.doHTTP("PUT", url_str, null, null, data, content_type, auth_domain, expect_response, true, true, true, api_token);
    }

    // execute DELETE over http
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpDelete(String url_str, String username, String password, String data, String content_type, String auth_domain) {
        return this.doHTTP("DELETE", url_str, username, password, data, content_type, auth_domain, true, true, false, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpDeleteApiTokenAuth(String url_str, String api_token, String data, String content_type, String auth_domain) {
        return this.doHTTP("DELETE", url_str, null, null, data, content_type, auth_domain, true, true, false, true, api_token);
    }

    // execute DELETE over https
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpsDelete(String url_str, String username, String password, String data, String content_type, String auth_domain) {
        return this.doHTTP("DELETE", url_str, username, password, data, content_type, auth_domain, true, true, true, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @param auth_domain
     * @return
     */
    public String httpsDeleteApiTokenAuth(String url_str, String api_token, String data, String content_type, String auth_domain) {
        return this.doHTTP("DELETE", url_str, null, null, data, content_type, auth_domain, true, true, true, true, api_token);
    }

    // get the requested path component of a given URL
    /**
     *
     * @param url
     * @param index
     * @param whole_path
     * @return
     */
    public String getPathComponent(String url, int index, boolean whole_path) {
        try {
            return this.getPathComponent(new URL(url), index, whole_path);
        }
        catch (MalformedURLException ex) {
            this.errorLogger().critical("Caught Exception parsing URL: " + url + " in getPathComponent: ", ex);
        }
        return null;
    }

    private String getPathComponent(URL url, int index, boolean whole_path) {
        String value = null;
        if (whole_path) {
            value = url.getPath().trim();
        }
        else {
            String path = url.getPath().replace("/", " ").trim();
            String list[] = path.split(" ");
            if (index >= 0 && index < list.length) {
                value = list[index];
            }
            if (index < 0) {
                return list[list.length - 1];
            }
        }
        return value;
    }

    private void saveResponseCode(int response_code) {
        this.m_last_response_code = response_code;
    }

    public int getLastResponseCode() {
        return this.m_last_response_code;
    }

    // perform an authenticated HTML operation
    @SuppressWarnings("empty-statement")
    private String doHTTP(String verb, String url_str, String username, String password, String data, String content_type, String auth_domain, boolean doInput, boolean doOutput, boolean doSSL, boolean use_api_token, String api_token) {
        return this.doHTTP(verb, url_str, username, password, data, content_type, auth_domain, doInput, doOutput, doSSL, use_api_token, api_token, false);
    }

    // perform an authenticated HTML operation
    @SuppressWarnings("empty-statement")
    private String doHTTP(String verb, String url_str, String username, String password, String data, String content_type, String auth_domain, boolean doInput, boolean doOutput, boolean doSSL, boolean use_api_token, String api_token, boolean persistent) {
        String result = "";
        String line = "";
        URLConnection connection = null;
        SSLContext sc = null;

        try {
            URL url = new URL(url_str);

            // Http Connection and verb
            if (doSSL) {
                // Create a trust manager that does not validate certificate chains
                TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }};

                // Install the all-trusting trust manager
                try {
                    sc = SSLContext.getInstance("TLS");
                    sc.init(null, trustAllCerts, new SecureRandom());
                    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                    HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                        @Override
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }
                    });
                }
                catch (NoSuchAlgorithmException | KeyManagementException e) {
                    // do nothing
                    ;
                }

                // open the SSL connction
                connection = (HttpsURLConnection) (url.openConnection());
                ((HttpsURLConnection) connection).setRequestMethod(verb);
                ((HttpsURLConnection) connection).setSSLSocketFactory(sc.getSocketFactory());
                ((HttpsURLConnection) connection).setHostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                });
            }
            else {
                connection = (HttpURLConnection) (url.openConnection());
                ((HttpURLConnection) connection).setRequestMethod(verb);
            }

            connection.setDoInput(doInput);
            if (doOutput && data != null && data.length() > 0) {
                connection.setDoOutput(doOutput);
            }
            else {
                connection.setDoOutput(false);
            }

            // enable basic auth if requested
            if (use_api_token == false && username != null && username.length() > 0 && password != null && password.length() > 0) {
                String encoding = Base64.encodeBase64String((username + ":" + password).getBytes());
                connection.setRequestProperty("Authorization", this.m_basic_auth_qualifier + " " + encoding);
                this.errorLogger().info("HTTP(S): Basic Authorization: " + username + ":" + password + ": " + encoding);
            }

            // enable ApiTokenAuth auth if requested
            if (use_api_token == true && api_token != null && api_token.length() > 0) {
                // use qualification for the authorization header...
                connection.setRequestProperty("Authorization", this.m_auth_qualifier + " " + api_token);
                this.errorLogger().info("HTTP(S): ApiTokenAuth Authorization: " + this.m_auth_qualifier + " " + api_token);

                // Always reset to the established default
                this.resetAuthorizationQualifier();
            }

            // ETag support if requested
            if (this.m_etag_value != null && this.m_etag_value.length() > 0) {
                // set the ETag header value
                connection.setRequestProperty("ETag", this.m_etag_value);
                //this.errorLogger().info("ETag Value: " + this.m_etag_value);

                // Always reset to the established default
                this.resetETagValue();
            }

            // If-Match support if requested
            if (this.m_if_match_header_value != null && this.m_if_match_header_value.length() > 0) {
                // set the If-Match header value
                connection.setRequestProperty("If-Match", this.m_if_match_header_value);
                //this.errorLogger().info("If-Match Value: " + this.m_if_match_header_value);

                // Always reset to the established default
                this.resetIfMatchValue();
            }

            // specify content type if requested
            if (content_type != null && content_type.length() > 0) {
                connection.setRequestProperty("Content-Type", content_type);
                connection.setRequestProperty("Accept", "*/*");

                // DEBUG
                this.errorLogger().info("ContentType: " + content_type);
            }

            // add Connection: keep-alive (does not work...)
            //connection.setRequestProperty("Connection", "keep-alive");
            // special gorp for HTTP DELETE
            if (verb != null && verb.equalsIgnoreCase("delete")) {
                connection.setRequestProperty("Access-Control-Allow-Methods", "OPTIONS, DELETE");
            }

            // specify domain if requested
            if (auth_domain != null && auth_domain.length() > 0) {
                connection.setRequestProperty("Domain", auth_domain);
            }

            // specify a persistent connection or not
            if (persistent == true) {
                connection.setRequestProperty("Connection", "keep-alive");
            }

            // DEBUG dump the headers
            //if (doSSL) 
            //    this.errorLogger().info("HTTP: Headers: " + ((HttpsURLConnection)connection).getRequestProperties()); 
            //else
            //    this.errorLogger().info("HTTP: Headers: " + ((HttpURLConnection)connection).getRequestProperties()); 
            // specify data if requested - assumes it properly escaped if necessary
            if (doOutput && data != null && data.length() > 0) {
                try (OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream(),"UTF-8")) {
                    this.errorLogger().info("HTTP(" + verb + "): DATA: " + data + " CONTENT_TYPE: " + content_type);
                    out.write(data);
                    out.flush();
                    out.close();
                }
            }

            // setup the output if requested
            if (doInput) {
                try {
                    try (InputStream content = (InputStream) connection.getInputStream(); BufferedReader in = new BufferedReader(new InputStreamReader(content))) {
                        StringBuilder buf = new StringBuilder();
                        while ((line = in.readLine()) != null) {
                            buf.append(line);
                        }
                        result = buf.toString();
                    }
                }
                catch (java.io.FileNotFoundException ex) {
                    this.errorLogger().info("HTTP(" + verb + ") empty response (OK).");
                    result = "";
                }
            }
            else {
                // no result expected
                result = "";
            }

            // save off the HTTP response code...
            if (doSSL) {
                this.saveResponseCode(((HttpsURLConnection) connection).getResponseCode());
            }
            else {
                this.saveResponseCode(((HttpURLConnection) connection).getResponseCode());
            }

            // DEBUG
            //if (doSSL)
            //    this.errorLogger().info("HTTP(" + verb +") URL: " + url_str + " Data: " + data + " Response code: " + ((HttpsURLConnection)connection).getResponseCode());
            //else
            //    this.errorLogger().info("HTTP(" + verb +") URL: " + url_str + " Data: " + data + " Response code: " + ((HttpURLConnection)connection).getResponseCode());
        }
        catch (IOException ex) {
            // exception note (DEBUG)
            this.errorLogger().info("Exception in doHTTP(" + verb + "): URL: " + url_str +  " ERROR: " + ex.getMessage());
            result = null;

            try {
                // check for non-null connection
                if (connection != null) {
                    // save off the HTTP response code...
                    if (doSSL) {
                        this.saveResponseCode(((HttpsURLConnection) connection).getResponseCode());
                    }
                    else {
                        this.saveResponseCode(((HttpURLConnection) connection).getResponseCode());
                    }
                }
                else {
                    this.errorLogger().warning("ERROR in doHTTP(" + verb + "): Connection is NULL");
                }
            }
            catch (IOException ex2) {
                this.errorLogger().warning("Exception in doHTTP(" + verb + "): Unable to save last response code: " + ex2.getMessage());
            }
        }

        // return the result
        return result;
    }
}
