/**
 * @file HttpTransport.java
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
import com.arm.pelion.bridge.core.KeyValuePair;
import com.arm.pelion.bridge.preferences.PreferenceManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
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
    private String m_pelion_api_hostname = null;
    private PelionHostnameVerifier m_verifier = null;
    private PelionTrustManagerListFactory m_trust_managers = null;
    private ArrayList<KeyValuePair> m_additional_headers = null;

    // constructor
    /**
     *
     * @param error_logger
     * @param preference_manager
     */
    public HttpTransport(ErrorLogger error_logger, PreferenceManager preference_manager) {
        super(error_logger, preference_manager);
        
        // Pelion API address
        this.m_pelion_api_hostname = preference_manager.valueOf("mds_address");
        if (this.m_pelion_api_hostname == null || this.m_pelion_api_hostname.length() == 0) {
            this.m_pelion_api_hostname = preference_manager.valueOf("api_endpoint_address");
        }
        
        // set the hostname verifier
        this.m_verifier = new PelionHostnameVerifier(this.m_pelion_api_hostname);
        
        // set the trust managers
        this.m_trust_managers = new PelionTrustManagerListFactory(error_logger, preference_manager);
        
        // set the authentication qualifier (typically "bearer")
        String auth_qualifier = this.prefValue("http_auth_qualifier");
        if (auth_qualifier != null && auth_qualifier.length() > 0) {
            this.m_auth_qualifier_default = auth_qualifier;
            this.m_auth_qualifier = this.m_auth_qualifier_default;
        }
        this.errorLogger().info("HTTP: Authorization Qualifier set to: " + this.m_auth_qualifier);
        
        // initialize the additional header support
        this.m_additional_headers = new ArrayList<>();
    }
    
    // add an additional header option
    public void addHeader(String key,String value) {
        if (this.hasHeader(key,value) == false) {
            KeyValuePair kvp = new KeyValuePair(key,value);
            this.m_additional_headers.add(kvp);
        }
    }
    
    // clear additional headers
    public void clearAdditionalHeaders() {
        this.m_additional_headers.clear();
    }
    
    // check if we already have the requested header 
    private boolean hasHeader(String key,String value) {
        boolean has_header = false;
        KeyValuePair test_kvp = new KeyValuePair(key,value);
        
        for(int i=0;i<this.m_additional_headers.size() && !has_header;++i) {
            if (test_kvp.same(this.m_additional_headers.get(i)) == true) {
                has_header = true;
            } 
        }
        
        return has_header;
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

    // execute GET over https
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @return
     */
    public String httpsPeristentGet(String url_str, String username, String password, String data, String content_type) {
        return this.dispatchHTTPS("GET", url_str, username, password, data, content_type, true, false, true, false, null, true);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @return
     */
    public String httpsPersistentGetApiTokenAuth(String url_str, String api_token, String data, String content_type) {
        return this.dispatchHTTPS("GET", url_str, null, null, data, content_type, true, false, true, true, api_token, true);
    }

    // execute GET over https
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @return
     */
    public String httpsGet(String url_str, String username, String password, String data, String content_type) {
        return this.dispatchHTTPS("GET", url_str, username, password, data, content_type, true, false, true, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @return
     */
    public String httpsGetApiTokenAuth(String url_str, String api_token, String data, String content_type) {
        return this.dispatchHTTPS("GET", url_str, null, null, data, content_type, true, false, true, true, api_token);
    }

    // execute POST over https
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @return
     */
    public String httpsPost(String url_str, String username, String password, String data, String content_type) {
        return this.dispatchHTTPS("POST", url_str, username, password, data, content_type, true, true, true, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @return
     */
    public String httpsPostApiTokenAuth(String url_str, String api_token, String data, String content_type) {
        return this.dispatchHTTPS("POST", url_str, null, null, data, content_type, true, true, true, true, api_token);
    }

    // execute PUT over https
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @return
     */
    public String httpsPut(String url_str, String username, String password, String data, String content_type) {
        return this.dispatchHTTPS("PUT", url_str, username, password, data, content_type, true, true, true, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @return
     */
    public String httpsPutApiTokenAuth(String url_str, String api_token, String data, String content_type) {
        return this.dispatchHTTPS("PUT", url_str, null, null, data, content_type, true, true, true, true, api_token);
    }

    // execute PUT over https
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @param expect_response
     * @return
     */
    public String httpsPut(String url_str, String username, String password, String data, String content_type, boolean expect_response) {
        return this.dispatchHTTPS("PUT", url_str, username, password, data, content_type, expect_response, true, true, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @param expect_response
     * @return
     */
    public String httpsPutApiTokenAuth(String url_str, String api_token, String data, String content_type, boolean expect_response) {
        return this.dispatchHTTPS("PUT", url_str, null, null, data, content_type, expect_response, true, true, true, api_token);
    }

    // execute DELETE over https
    /**
     *
     * @param url_str
     * @param username
     * @param password
     * @param data
     * @param content_type
     * @return
     */
    public String httpsDelete(String url_str, String username, String password, String data, String content_type) {
        return this.dispatchHTTPS("DELETE", url_str, username, password, data, content_type, true, true, true, false, null);
    }

    /**
     *
     * @param url_str
     * @param api_token
     * @param data
     * @param content_type
     * @return
     */
    public String httpsDeleteApiTokenAuth(String url_str, String api_token, String data, String content_type) {
        return this.dispatchHTTPS("DELETE", url_str, null, null, data, content_type, true, true, true, true, api_token);
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

    // perform the HTTPS dispatch
    @SuppressWarnings("empty-statement")
    private String dispatchHTTPS(String verb, String url_str, String username, String password, String data, String content_type, boolean doInput, boolean doOutput, boolean doSSL, boolean use_api_token, String api_token) {
        return this.dispatchHTTPS(verb, url_str, username, password, data, content_type, doInput, doOutput, doSSL, use_api_token, api_token, false);
    }

    // perform the HTTPS dispatch
    private String dispatchHTTPS(String verb, String url_str, String username, String password, String data, String content_type, boolean doInput, boolean doOutput, boolean doSSL, boolean use_api_token, String api_token, boolean persistent) {
        String result = "";
        String line = "";
        URLConnection connection = null;
        SSLContext sc = null;
        boolean setup = false;

        try {
            URL url = new URL(url_str);

            // Install our pelion trust manager and hostname verifier
            try {
                sc = SSLContext.getInstance("TLS");
                sc.init(null, this.m_trust_managers.create(), new SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                HttpsURLConnection.setDefaultHostnameVerifier(this.m_verifier);
                setup = true;
            }
            catch (NoSuchAlgorithmException | KeyManagementException e) {
                // ERROR - unable to setup/config TLS
                this.errorLogger().critical("HttpTransport(): doHTTP() exception during TLS config: " + e.getMessage(),e);
            }

            // check if we have TLS setup/configured thus far...
            if (setup == true) {
                // open the SSL connction
                connection = (HttpsURLConnection) (url.openConnection());
                ((HttpsURLConnection) connection).setRequestMethod(verb);
                ((HttpsURLConnection) connection).setSSLSocketFactory(sc.getSocketFactory());
                ((HttpsURLConnection) connection).setHostnameVerifier(this.m_verifier); 

                // Input configuration
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
                    this.errorLogger().info("HttpTransport(): Basic Authorization: " + username + ":" + password + ": " + encoding);
                }

                // enable ApiTokenAuth auth if requested
                if (use_api_token == true && api_token != null && api_token.length() > 0) {
                    // use qualification for the authorization header...
                    connection.setRequestProperty("Authorization", this.m_auth_qualifier + " " + api_token);
                    this.errorLogger().info("HttpTransport(): ApiTokenAuth Authorization: " + this.m_auth_qualifier + " " + api_token);

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

                // special headers for HTTPS DELETE
                if (verb != null && verb.equalsIgnoreCase("delete")) {
                    connection.setRequestProperty("Access-Control-Allow-Methods", "OPTIONS, DELETE");
                }

                // specify a persistent connection or not
                if (persistent == true) {
                    connection.setRequestProperty("Connection", "keep-alive");
                }

                // add any additional headers
                if (this.m_additional_headers != null && this.m_additional_headers.size() > 0) {
                    for(int i=0;i<this.m_additional_headers.size();++i) {
                        KeyValuePair kvp = this.m_additional_headers.get(i);
                        connection.setRequestProperty(kvp.key(), kvp.value());
                    }
                }

                // DEBUG -  dump the headers
                this.errorLogger().info("HttpTransport(" + verb + "): Headers: " + ((HttpsURLConnection)connection).getRequestProperties()); 

                // specify data if requested - assumes it properly escaped if necessary
                if (doOutput && data != null && data.length() > 0) {
                    try (OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream(),"UTF-8")) {
                        this.errorLogger().info("HttpTransport(" + verb + "): DATA: " + data + " CONTENT_TYPE: " + content_type);
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
                        this.errorLogger().info("HttpTransport(" + verb + ") empty response (OK).");
                        result = "";
                    }
                }
                else {
                    // no result expected
                    result = "";
                }

                // save off the HTTP response code...
                this.saveResponseCode(((HttpsURLConnection) connection).getResponseCode());

                // DEBUG
                //this.errorLogger().info("HTTP(" + verb +") URL: " + url_str + " Data: " + data + " Response code: " + ((HttpsURLConnection)connection).getResponseCode());
            }
            else {
                // non-SSL not supported
                this.errorLogger().critical("ERROR! HttpTransport(): HTTP now unsupported in doHTTP(" + verb + "): URL: " + url_str);
            }
        }    
        catch (IOException ex) {
            // exception note (DEBUG)
            this.errorLogger().info("HttpTransport(): Exception in doHTTP(" + verb + "): URL: " + url_str +  " ERROR: " + ex.getMessage());
            result = null;

            try {
                // check for non-null connection
                if (connection != null) {
                    // save off the HTTPs response code...
                    this.saveResponseCode(((HttpsURLConnection) connection).getResponseCode());
                }
                else {
                    this.errorLogger().warning("HttpTransport(): ERROR in doHTTP(" + verb + "): Connection is NULL");
                }
            }
            catch (IOException ex2) {
                this.errorLogger().warning("HttpTransport(): Exception in doHTTP(" + verb + "): Unable to save last response code: " + ex2.getMessage());
            }
        }

        // return the result
        return result;
    }
}