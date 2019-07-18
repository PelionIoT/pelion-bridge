/**
 * @file MQTTTransport.java
 * @brief MQTT Transport Support
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

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.interfaces.GenericSender;
import com.arm.pelion.bridge.coordinator.processors.interfaces.ReconnectionInterface;
import com.arm.pelion.bridge.core.ErrorLogger;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.preferences.PreferenceManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.UUID;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;

/**
 * MQTT Transport Support
 *
 * @author Doug Anson
 */
public class MQTTTransport extends Transport implements GenericSender {
    // should never be used - should always be set in the configuration file
    private static final String KEYSTORE_PW_DEFAULT = UUID.randomUUID().toString();
    
    // number of connection attempts before giving up...
    private static final int DEFAULT_NUM_CONNECT_TRIES = 10;
    private int m_max_connect_tries = DEFAULT_NUM_CONNECT_TRIES;
    
    // default retain behavior
    private static final boolean DEFAULT_RETAIN_ENABLED = true;     // set to true
    
    // Access our instance 
    private static volatile MQTTTransport m_self = null;
    
    // FuseSource MQTT connection
    private BlockingConnection m_connection = null;
    private Topic[] m_subscribe_topics = null;
    private byte[] m_qoses = null;
    
    // Configuration
    private String m_suffix = null;
    private String m_username = null;
    private String m_password = null;
    private String m_host_url = null;
    private int m_sleep_time = 0;
    private String m_client_id = null;
    private String m_mqtt_version = null;
    private String m_connect_host = null;
    private int m_connect_port = 0;
    private String m_connect_client_id = null;
    private boolean m_connect_clean_session = false;
    private String m_connect_id = null;
    private boolean m_set_mqtt_version = true;  
    private String[] m_unsubscribe_topics = null;
    private boolean m_retain = DEFAULT_RETAIN_ENABLED;

    // port remapping option
    private boolean m_port_remap = true; // default is true...
    
    // reset mode/state
    private boolean m_is_in_reset = false;
    
    // SSL Switches/Context
    private boolean m_mqtt_import_keystore = false;
    private boolean m_mqtt_no_client_creds = false;
    private boolean m_use_ssl_connection = false;
    private SSLContext m_ssl_context = null;
    private boolean m_mqtt_use_ssl = false;
    private boolean m_ssl_context_initialized = false;
    private boolean m_no_tls_certs_or_keys = false;
    
    // X.509 Support
    private boolean m_use_x509_auth = false;
    private String m_pki_priv_key = null;
    private String m_pki_pub_key = null;
    private String m_pki_cert = null;
    private String m_keystore_pw = null;
    private String m_base_dir = null;
    private String m_keystore_filename = null;
    private String m_keystore_basename = null;
    private X509Certificate m_cert = null;
    private PublicKey m_pubkey = null;
    private PrivateKey m_privkey = null;
    private String m_pubkey_pem_filename = null;
    
    // Debug X.509 Auth Creds
    private boolean m_debug_creds = false;
    
    // connection details
    private String m_ep_name = null;
    private String m_ep_type = null;
    
    // initial connection indicator
    private boolean m_has_connected = false;

    /**
     * Instance Factory
     *
     * @param error_logger
     * @param preference_manager
     * @param reconnector
     * @return
     */
    public static Transport getInstance(ErrorLogger error_logger, PreferenceManager preference_manager,ReconnectionInterface reconnector) {
        if (MQTTTransport.m_self == null) {
            MQTTTransport.m_self = new MQTTTransport(error_logger,preference_manager,reconnector);
        }
        return MQTTTransport.m_self;
    }

    /**
     * Constructor
     *
     * @param error_logger
     * @param preference_manager
     * @param suffix
     * @param reconnector
     */
    public MQTTTransport(ErrorLogger error_logger, PreferenceManager preference_manager, String suffix, ReconnectionInterface reconnector) {
        super(error_logger, preference_manager,reconnector);
        this.m_use_x509_auth = false;
        this.m_use_ssl_connection = false;
        this.m_ssl_context = null;
        this.m_host_url = null;
        this.m_suffix = suffix;
        this.m_keystore_filename = null;
        this.m_set_mqtt_version = true;
        this.m_has_connected = false;
        this.m_is_in_reset = false;
        this.m_retain = false;
        this.m_port_remap = true; 
        
        this.m_mqtt_use_ssl = this.prefBoolValue("mqtt_use_ssl", this.m_suffix);
        this.m_debug_creds = this.prefBoolValue("mqtt_debug_creds", this.m_suffix);
        this.m_mqtt_import_keystore = this.prefBoolValue("mqtt_import_keystore", this.m_suffix);
        this.m_mqtt_no_client_creds = this.prefBoolValue("mqtt_no_client_creds", this.m_suffix);
        this.setMQTTVersion(this.prefValue("mqtt_version", this.m_suffix));
        this.setUsername(this.prefValue("mqtt_username", this.m_suffix));
        this.setPassword(this.prefValue("mqtt_password", this.m_suffix));
        this.m_sleep_time = ((this.preferences().intValueOf("mqtt_receive_loop_sleep", this.m_suffix)) * 1000);
        this.m_keystore_pw = this.preferences().valueOf("mqtt_keystore_pw", this.m_suffix);
        this.m_base_dir = this.preferences().valueOf("mqtt_keystore_basedir", this.m_suffix);
        this.m_keystore_basename = this.preferences().valueOf("mqtt_keystore_basename", this.m_suffix);
        this.m_pubkey_pem_filename = this.preferences().valueOf("mqtt_pubkey_pem_filename", this.m_suffix);
        
        // default pubkey filename if utilized
        if (this.m_pubkey_pem_filename == null || this.m_pubkey_pem_filename.length() == 0) {
            this.m_pubkey_pem_filename = Utils.DEFAULT_PUBKEY_PEM_FILENAME;
        }
        
        // sync our acceptance of self-signed client creds
        this.noSelfSignedCertsOrKeys(this.m_mqtt_no_client_creds);
        
        // should never be used
        if (this.m_keystore_pw == null) {
            // default the keystore pw
            this.m_keystore_pw = MQTTTransport.KEYSTORE_PW_DEFAULT;
        }
    }

    /**
     * Constructor
     *
     * @param error_logger
     * @param preference_manager
     * @param reconnector
     */
    public MQTTTransport(ErrorLogger error_logger, PreferenceManager preference_manager,ReconnectionInterface reconnector) {
        super(error_logger, preference_manager,reconnector);
        this.m_use_x509_auth = false;
        this.m_use_ssl_connection = false;
        this.m_ssl_context = null;
        this.m_host_url = null;
        this.m_suffix = null;
        this.m_keystore_filename = null;
        this.m_set_mqtt_version = true;
        this.m_has_connected = false;
        this.m_is_in_reset = false;
        this.m_retain = false;
        this.m_port_remap = true;

        this.m_mqtt_use_ssl = this.prefBoolValue("mqtt_use_ssl", this.m_suffix);
        this.m_debug_creds = this.prefBoolValue("mqtt_debug_creds", this.m_suffix);
        this.m_mqtt_import_keystore = this.prefBoolValue("mqtt_import_keystore", this.m_suffix);
        this.m_mqtt_no_client_creds = this.prefBoolValue("mqtt_no_client_creds", this.m_suffix);
        this.setMQTTVersion(this.prefValue("mqtt_version", this.m_suffix));
        this.setUsername(this.prefValue("mqtt_username", this.m_suffix));
        this.setPassword(this.prefValue("mqtt_password", this.m_suffix));
        this.m_sleep_time = ((this.preferences().intValueOf("mqtt_receive_loop_sleep", this.m_suffix)) * 1000);
        this.m_keystore_pw = this.preferences().valueOf("mqtt_keystore_pw", this.m_suffix);
        this.m_base_dir = this.preferences().valueOf("mqtt_keystore_basedir", this.m_suffix);
        this.m_keystore_basename = this.preferences().valueOf("mqtt_keystore_basename", this.m_suffix);
        this.m_pubkey_pem_filename = this.preferences().valueOf("mqtt_pubkey_pem_filename", this.m_suffix);
        
        // default pubkey filename if utilized
        if (this.m_pubkey_pem_filename == null || this.m_pubkey_pem_filename.length() == 0) {
            this.m_pubkey_pem_filename = Utils.DEFAULT_PUBKEY_PEM_FILENAME;
        }
        
        // sync our acceptance of self-signed client creds
        this.noSelfSignedCertsOrKeys(this.m_mqtt_no_client_creds);
        
        // should never be used
        if (this.m_keystore_pw == null) {
            // default the keystore pw
            this.m_keystore_pw = MQTTTransport.KEYSTORE_PW_DEFAULT;
        }
    }
    
    // enable debugging of creds
    public void enableDebugCreds(boolean debug) {
        this.m_debug_creds = debug;
    }
    
    // no ssl port remapping
    public void sslPortRemap(boolean remap) {
        this.m_port_remap = remap;
    }
    
    // record additional endpoint details
    public void setEndpointDetails(String ep_name,String ep_type) {
        this.m_ep_name = ep_name;
        this.m_ep_type = ep_type;
    }

    // disable/enable setting of MQTT version
    public void enableMQTTVersionSet(boolean set_mqtt_version) {
        this.m_set_mqtt_version = set_mqtt_version;
    }
    
    // if SSL is used, dont provide any certs/keys 
    public void noSelfSignedCertsOrKeys(boolean no_tls_certs_or_keys) {
        this.m_no_tls_certs_or_keys = no_tls_certs_or_keys;
    }

    // pre-plumb TLS/X.509 certs and keys
    public void prePlumbTLSCertsAndKeys(String priv_key, String pub_key, String certificate, String id) {
        this.m_pki_priv_key = priv_key;
        this.m_pki_pub_key = pub_key;
        this.m_pki_cert = certificate;
        if (this.initializeSSLContext(id) == true) {
            this.m_use_x509_auth = true;
            this.m_use_ssl_connection = true;
        }
        else {
            // unable to initialize the SSL context... so error out
            this.errorLogger().critical("prePlumbTLSCerts: Unable to initialize SSL Context for ID: " + id);
            this.m_pki_priv_key = null;
            this.m_pki_pub_key = null;
            this.m_pki_cert = null;
            this.m_ssl_context = null;
        }
    }

    // create the key manager
    private KeyManager[] createKeyManager(String keyStoreType) {
        KeyManager[] kms = null;
        FileInputStream fs = null;

        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            KeyStore ks = KeyStore.getInstance(keyStoreType);
            fs = new FileInputStream(this.m_keystore_filename);
            ks.load(fs, this.m_keystore_pw.toCharArray());
            kmf.init(ks, this.m_keystore_pw.toCharArray());
            kms = kmf.getKeyManagers();
        }
        catch (NoSuchAlgorithmException | KeyStoreException | IOException | CertificateException | UnrecoverableKeyException ex) {
            this.errorLogger().warning("MQTTTransport: createKeyManager: Exception in creating the KeyManager list", ex);
        }

        try {
            if (fs != null) {
                fs.close();
            }
        }
        catch (IOException ex) {
            // silent
        }

        return kms;
    }

    // create the trust manager
    private TrustManager[] createTrustManager() {
        TrustManager tm[] = new TrustManager[1];
        tm[0] = new MQTTTrustManager(this.m_keystore_filename, this.m_keystore_pw);
        return tm;
    }
    
    // Init a self-signed certificate
    private String initSelfSignedCert(KeyPair keys) {
        String cert = null;
        
        try {
            // build a certificate generator
            X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();
            X500Principal dnName = new X500Principal("cn=127.0.0.1");

            // add some options
            certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
            certGen.setSubjectDN(new X509Name("dc=ARM"));
            certGen.setIssuerDN(dnName); // use the same
            
            // yesterday
            certGen.setNotBefore(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
            
            // in 2 years
            certGen.setNotAfter(new Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000));
            certGen.setPublicKey(keys.getPublic());
            certGen.setSignatureAlgorithm("SHA256WithRSAEncryption");
            certGen.addExtension(X509Extensions.ExtendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));

            // finally, sign the certificate with the private key of the same KeyPair
            X509Certificate x509 = certGen.generate(keys.getPrivate(),"BC");
            
            // encode the certificate into a PEM string
            cert = Utils.convertX509ToPem(x509);
        }
        catch (IllegalArgumentException | IllegalStateException | InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException | SignatureException | CertificateEncodingException ex) {
            // unable to init self-signed cert
            this.errorLogger().critical("MQTT: initSelfSignedCert: Exception caught: " + ex.getMessage());
        }
        return cert;
    }
    
    // create our own key material if we dont have it already
    private void initKeyMaterial() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(1024);
            KeyPair keys= keyGen.generateKeyPair();
            
            // Create the key material
            this.m_pki_priv_key = Utils.convertPrivKeyToPem(keys);
            this.m_pki_pub_key = Utils.convertPubKeyToPem(keys);

            // create the certificate
            this.m_pki_cert = this.initSelfSignedCert(keys);
        }
        catch (NoSuchAlgorithmException ex) {
            // unable to init key material
            this.errorLogger().critical("MQTT: initKeyMaterial: Exception caught: " + ex.getMessage());
        }
    }
    
    // reuse an exsiting keystore 
    private String useExistingKeyStore(String id,String pw) {
        // create our filename from the ID 
        String filename =  Utils.makeKeystoreFilename(this.m_base_dir, id, this.m_keystore_basename);
        String pubkey_pem = Utils.makePubkeyFilename(this.m_base_dir, id, this.m_pubkey_pem_filename);
        
        // now read from the Keystore
        if (this.m_pki_cert == null || this.m_pki_priv_key == null || this.m_pki_pub_key == null) {
            this.m_pki_cert = Utils.readCertFromKeystoreAsPEM(this.errorLogger(),filename,pw);
            this.m_pki_priv_key = Utils.readPrivKeyFromKeystoreAsPEM(this.errorLogger(),filename,pw);
            this.m_pki_pub_key = Utils.readPubKeyAsPEM(this.errorLogger(),pubkey_pem);
        }
        
        // display creds if debugging
        if (this.m_debug_creds == true) {
            // Creds DEBUG
            this.errorLogger().info("MQTT: PRIV: " + this.m_pki_priv_key);
            this.errorLogger().info("MQTT: PUB: " + this.m_pki_pub_key);
            this.errorLogger().info("MQTT: CERT: " + this.m_pki_cert);
        }
        
        // return the filename
        return filename;
    }
    
    // convert a private key to a string form
    private String privateKeyToString(PrivateKey key) {
        if (key != null) {
            return key.toString();
        }
        return null;
    }

    // create keystore
    private String initializeKeyStore(String id) {
        // create self-signed creds if we dont have them but need SSL
        if (this.m_pki_cert == null || this.m_pki_priv_key == null || this.m_pki_pub_key == null) {
            this.initKeyMaterial();
        }
        
        // display creds if debugging
        if (this.m_debug_creds == true) {
            // Creds DEBUG
            this.errorLogger().info("MQTT: PRIV: " + this.m_pki_priv_key);
            this.errorLogger().info("MQTT: PUB: " + this.m_pki_pub_key);
            this.errorLogger().info("MQTT: CERT: " + this.m_pki_cert);
        }
            
        // create our credentials
        this.m_cert = Utils.createX509CertificateFromPEM(this.errorLogger(), this.m_pki_cert, "X509");
        this.m_privkey = Utils.createPrivateKeyFromPEM(this.errorLogger(), this.m_pki_priv_key, "RSA");

        // also hold onto the public key
        this.m_pubkey = Utils.createPublicKeyFromPEM(this.errorLogger(), this.m_pki_pub_key, "RSA");

        // set our keystore PW
        this.m_keystore_pw = Utils.generateKeystorePassword(this.errorLogger(),this.m_keystore_pw, id, privateKeyToString(this.m_privkey));

        // create the keystore
        return Utils.createKeystore(this.errorLogger(), this.m_base_dir, id, this.m_keystore_basename, this.m_cert, this.m_privkey, this.m_pubkey, this.m_keystore_pw);
    }

    // initialize the SSL context
    private boolean initializeSSLContext(String id) {
        if (this.m_ssl_context_initialized == false) {
            try {
                // enable the Bouncy Castle SSL provider
                java.security.Security.addProvider(new BouncyCastleProvider());

                // do we want self-signed certs and keys?
                if (this.m_no_tls_certs_or_keys == true) {
                    // just create our SSL context... use defaults for everything
                    this.m_ssl_context = SSLContext.getInstance("TLSv1.2");
                    this.m_ssl_context.init(null, null, new SecureRandom());
                    this.m_ssl_context_initialized = true;
                }
                else {
                    if (this.m_mqtt_import_keystore == false) {
                        // initialize the keystores with certs/key...
                        this.m_keystore_filename = this.initializeKeyStore(id);
                    }
                    else {
                        // use existing keystore 
                        this.m_keystore_filename = this.useExistingKeyStore(id,this.m_keystore_pw);
                    }
                    
                    if (this.m_keystore_filename != null) {
                        // create our SSL context - FYI: AWS IoT requires TLS v1.2
                        this.m_ssl_context = SSLContext.getInstance("TLSv1.2");

                        // initialize the SSL context with our KeyManager and our TrustManager
                        KeyManager km[] = this.createKeyManager("JKS");
                        TrustManager tm[] = this.createTrustManager();
                        this.m_ssl_context.init(km, tm, new SecureRandom());
                        this.m_ssl_context_initialized = true;
                    }
                    else {
                        // unable to create the filename
                        this.errorLogger().critical("MQTTTransport: initializeSSLContext(SSL) failed. unable to init keystore");
                    }
                }
            }
            catch (NoSuchAlgorithmException | KeyManagementException ex) {
                // exception caught
                this.errorLogger().critical("MQTTTransport: initializeSSLContext(SSL) failed. SSL DISABLED", ex);
            }
        }
        return this.m_ssl_context_initialized;
    }

    // PUBLIC: Create the authentication hash
    public String createAuthenticationHash() {
        return Utils.createHash(this.getUsername() + "_" + this.getPassword() + "_" + this.prefValue("mqtt_client_id", this.m_suffix));
    }

    // PRIVATE: Username/PW for MQTT connection
    private String getUsername() {
        return this.m_username;
    }

    // PRIVATE: Username/PW for MQTT connection
    private String getPassword() {
        return this.m_password;
    }

    // PUBLIC: Get the client ID
    public String getClientID() {
        return this.m_client_id;
    }

    // PUBLIC: Set the client ID
    public void setClientID(String clientID) {
        this.m_client_id = clientID;
    }

    /**
     * Set the MQTT Username
     *
     * @param username
     */
    public final void setUsername(String username) {
        this.m_username = username;
    }

    /**
     * Set the MQTT Password
     *
     * @param password
     */
    public final void setPassword(String password) {
        this.m_password = password;
    }
    
    /**
     * Set the MQTT Version
     *
     * @param version
     */
    public final void setMQTTVersion(String version) {
        this.m_mqtt_version = version;
    }

    /**
     * Are we connected to a MQTT broker?
     *
     * @return
     */
    @Override
    public boolean isConnected() {
        if (this.m_connection != null) {
            return this.m_connection.isConnected();
        }
        //this.errorLogger().warning("WARNING: MQTT connection instance is NULL...");
        return super.isConnected();
    }

    /**
     * Connect to the MQTT broker
     *
     * @param host
     * @param port
     * @return
     */
    @Override
    public boolean connect(String host, int port) {
        return this.connect(host, port, this.prefValue("mqtt_client_id", this.m_suffix), this.prefBoolValue("mqtt_clean_session", this.m_suffix));
    }

    /**
     * Connect to the MQTT broker
     *
     * @param host
     * @param port
     * @param clientID
     * @return
     */
    public boolean connect(String host, int port, String clientID) {
        return this.connect(host, port, clientID, this.prefBoolValue("mqtt_clean_session", this.m_suffix));
    }

     /**
     * Connect to the MQTT broker
     *
     * @param host
     * @param port
     * @param clientID
     * @param clean_session
     * @return
     */
    public boolean connect(String host, int port, String clientID, boolean clean_session) {
        return this.connect(host,port,clientID,clean_session,null);
    }
    
    /**
     * Connect to the MQTT broker
     *
     * @param host
     * @param port
     * @param clientID
     * @param clean_session
     * @param id 
     * @return
     */
    public boolean connect(String host, int port, String clientID, boolean clean_session, String id) {
        int num_tries = this.prefIntValue("mqtt_connect_retries", this.m_suffix);
        
        // build out the URL connection string
        String url = this.setupHostURL(host, port, id);

        // setup default clientID
        if (clientID == null || clientID.length() <= 0) {
            clientID = this.prefValue("mqtt_client_id", this.m_suffix);
        }
        String def_client_id = this.prefValue("mqtt_default_client_id", this.m_suffix);
        
        // sanity checking
        if (host == null || host.length() == 0 || port <= 0) {
            // just error out
            this.errorLogger().critical("MQTTTransport: no hostname supplied for MQTT connection. aborting (bridge unconfigured?)");
            return false;
        }

        // DEBUG
        this.errorLogger().info("MQTTTransport: connect() starting... URL: [" + url + "]");
        
        // loop until connected
        for (int i = 0; i < num_tries && this.m_connected == false; ++i) {
            // DEBUG
            this.errorLogger().info("MQTTTransport: Connection attempt: " + (i+1) + " of " + num_tries + "...");
            try {
                // MQTT endpoint 
                MQTT endpoint = new MQTT();
                if (endpoint != null) {
                    // set the target URL for our MQTT connection
                    endpoint.setHost(url);

                    // MQTT version
                    if (this.m_mqtt_version != null && this.m_set_mqtt_version == true) {
                        endpoint.setVersion(this.m_mqtt_version);
                    }

                    // SSL Context for secured MQTT connections
                    if (this.m_ssl_context_initialized == true) {
                        // DEBUG
                        this.errorLogger().info("MQTTTransport: SSL Used... setting SSL context...");

                        // SSL Context should be set
                        endpoint.setSslContext(this.m_ssl_context);
                    }

                    // configure credentials
                    String username = this.getUsername();
                    String pw = this.getPassword();

                    if (username != null && username.length() > 0 && username.equalsIgnoreCase("off") == false) {
                        endpoint.setUserName(username);
                        this.errorLogger().info("MQTTTransport: Username: [" + username + "] used");
                    }
                    else {
                        this.errorLogger().info("MQTTTransport: Anonymous username used");
                    }
                    if (pw != null && pw.length() > 0 && pw.equalsIgnoreCase("off") == false) {
                        endpoint.setPassword(pw);
                        this.errorLogger().info("MQTTTransport: pw: [" + pw + "] used");
                    }
                    else {
                        this.errorLogger().info("MQTTTransport: Anonymous pw used");
                    }

                    // Client ID 
                    if (clientID != null && clientID.length() > 0 && clientID.equalsIgnoreCase("off") == false) {
                        endpoint.setClientId(clientID);
                        this.errorLogger().info("MQTTTransport: Client ID: [" + clientID + "] used");
                    }
                    else if (clean_session == false) {
                        if (def_client_id != null && def_client_id.equalsIgnoreCase("off") == false) {
                            // set a defaulted clientID
                            endpoint.setClientId(def_client_id);
                            this.errorLogger().info("MQTTTransport: Client ID (default for clean): [" + def_client_id + "] used");
                        }
                        else {
                            // non-clean session specified, but no clientID was given...
                            this.errorLogger().warning("MQTTTransport: ERROR: Non-clean session requested but no ClientID specified");
                        }
                    }
                    else {
                        // no client ID used... clean session specified (OK)
                        this.errorLogger().info("MQTTTransport: No ClientID being used (clean session)");
                    }

                    // set Clean Session...
                    endpoint.setCleanSession(clean_session);
                    endpoint.setCleanSession(true);

                    // Will Message...
                    String will = this.prefValue("mqtt_will_message", this.m_suffix);
                    if (will != null && will.length() > 0 && will.equalsIgnoreCase("off") == false) {
                        endpoint.setWillMessage(will);
                    }

                    // Will Topic...
                    String will_topic = this.prefValue("mqtt_will_topic", this.m_suffix);
                    if (will_topic != null && will_topic.length() > 0 && will_topic.equalsIgnoreCase("off") == false) {
                        endpoint.setWillTopic(will_topic);
                    }

                    // Traffic Class...
                    int trafficClass = this.prefIntValue("mqtt_traffic_class", this.m_suffix);
                    if (trafficClass >= 0) {
                        endpoint.setTrafficClass(trafficClass);
                    }

                    // Reconnect Attempts...
                    int reconnectAttempts = this.prefIntValue("mqtt_reconnect_retries_max", this.m_suffix);
                    if (reconnectAttempts >= 0) {
                        endpoint.setReconnectAttemptsMax(reconnectAttempts);
                    }

                    // Reconnect Delay...
                    long reconnectDelay = (long) this.prefIntValue("mqtt_reconnect_delay", this.m_suffix);
                    if (reconnectDelay >= 0) {
                        endpoint.setReconnectDelay(reconnectDelay);
                    }

                    // Reconnect Max Delay...
                    long reconnectDelayMax = (long) this.prefIntValue("mqtt_reconnect_delay_max", this.m_suffix);
                    if (reconnectDelayMax >= 0) {
                        endpoint.setReconnectDelayMax(reconnectDelayMax);
                    }

                    // Reconnect back-off multiplier
                    float backoffMultiplier = this.prefFloatValue("mqtt_backoff_multiplier", this.m_suffix);
                    if (backoffMultiplier >= 0) {
                        endpoint.setReconnectBackOffMultiplier(backoffMultiplier);
                    }

                    // Keep-Alive...
                    short keepAlive = (short) this.prefIntValue("mqtt_keep_alive", this.m_suffix);
                    if (keepAlive >= 0) {
                        endpoint.setKeepAlive(keepAlive);
                    }

                    // record the ClientID for later...
                    if (endpoint.getClientId() != null) {
                        this.m_client_id = endpoint.getClientId().toString();
                    }
                    
                    // OK... now lets try to connect to the broker...
                    try {
                        // wait a bit
                        //Utils.waitForABit(this.errorLogger(), this.m_sleep_time);
                            
                        // attempt connection...record our connection status
                        this.m_connected = false;
                        this.m_endpoint = endpoint;
                        this.errorLogger().info("MQTTTransport: acquiring blocking connection handle...");
                        this.m_connection = endpoint.blockingConnection();                        
                        if (this.m_connection != null) {
                            this.errorLogger().info("MQTTTransport: connection handle acquired!  Connecting...");
                            if (this.attemptConnection() == true) {
                                // CONNECTED!  succesful... record and go...
                                this.m_has_connected = true;
                                this.errorLogger().info("MQTTTransport: Connection to: " + url + " SUCCESSFUL");
                                this.m_connect_host = host;
                                this.m_connect_port = port;
                                if (endpoint != null && endpoint.getClientId() != null) {
                                    this.m_client_id = endpoint.getClientId().toString();
                                }
                                else {
                                    this.m_client_id = null;
                                }
                                this.m_connect_client_id = this.m_client_id;
                                this.m_connect_clean_session = clean_session;
                                this.m_connect_id = id;
                            }
                            else {
                                // connection failure
                                this.errorLogger().warning("MQTTTransport: Connection to: " + url + " FAILED");
                            }
                        }
                        else {
                            // unable to connect... 
                            this.errorLogger().critical("MQTTTransport: MQTT connection handle is NULL. connect() FAILED");
                            
                            // do a full bridge reset 
                            Utils.resetBridge(this.errorLogger(),"Unable to acquire MQTT connection handle");
                        }
                    }
                    catch (Exception ex) {
                        this.errorLogger().warning("MQTTTransport: Exception during connect(): " + ex.getMessage());

                        // DEBUG
                        this.showConnectionInfo(url,clean_session);
                    }
                }
                else {
                    // cannot create an instance of the MQTT client
                    this.errorLogger().warning("MQTTTransport(connect): unable to create instance of MQTT client");
                    this.m_connected = false;
                    
                    // do a full bridge reset 
                    Utils.resetBridge(this.errorLogger(),"Unable to acquire MQTT connection handle");
                }
            }
            catch (URISyntaxException ex) {
                this.errorLogger().critical("MQTTTransport(connect): URI Syntax exception occured", ex);
                this.m_connected = false;
            }
            catch (Exception ex) {
                this.errorLogger().critical("MQTTTransport(connect): general exception occured", ex);
                this.m_connected = false;
                
                // do a full bridge reset 
                Utils.resetBridge(this.errorLogger(),"Unable to acquire MQTT connection handle");
            }
        }
        
        // if we are connected, the we our out of the reset condition
        if (this.m_connected == true) {
            this.m_is_in_reset = false;
        }

        // return our connection status
        return this.m_connected;
    }
    
    // debug connection info
    private void showConnectionInfo(String url, boolean clean_session) {
        // DEBUG
        this.errorLogger().warning("MQTT: URL: " + url);
        this.errorLogger().warning("MQTT: clientID: " + this.m_client_id);
        this.errorLogger().warning("MQTT: clean_session: " + clean_session);
        if (this.m_debug_creds == true) {
            this.errorLogger().warning("MQTT: username: " + this.getUsername());
            this.errorLogger().warning("MQTT: password: " + this.getPassword());

            if (this.m_pki_priv_key != null) {
                this.errorLogger().info("MQTT: PRIV: " + this.m_pki_priv_key);
            }

            if (this.m_pki_pub_key != null) {
                this.errorLogger().info("MQTT: PUB: " + this.m_pki_pub_key);
            }
            if (this.m_pki_cert != null) {
                this.errorLogger().info("MQTT: CERT: " + this.m_pki_cert);
            }
        }
    }

    /**
     * Main handler for receiving and processing MQTT Messages (called repeatedly by TransportReceiveThread...)
     *
     * @return true - processed (or empty), false - failure
     */
    @Override
    public boolean receiveAndProcess() {
        if (this.isConnected()) {
            try {
                // receive the MQTT message and process it...
                this.errorLogger().info("receiveAndProcess(MQTT). Calling receiveAndProcessMessage()...");
                this.receiveAndProcessMessage();
            }
            catch (Exception ex) {
                // caught exception while connected... something is wrong.
                this.errorLogger().info("receiveAndProcess(MQTT): Exception caught while connected: " + ex.getMessage());
                
                // reset the connection
                this.resetConnection();
                
                // return (dont-care)
                return false;
            }
            return true;
        }
        else {
            this.errorLogger().info("receiveAndProcess(MQTT): not connected (OK)");
            return true;
        }
    }
    
    // attempt a connection
    private boolean attemptConnection() throws Exception {
        this.m_connected = false;
        
        // try a few times to connect... then reset if not able to...
        for(int i=0;i<this.m_max_connect_tries && this.m_connected == false;++i) {
            try {
                // DEBUG
                this.errorLogger().info("MQTT: attemptConnection(): Trying to connect()...");

                // attempt connection
                this.m_connection.connect();

                // DEBUG
                this.errorLogger().info("MQTT: attemptConnection(): waiting a bit...()...");

                // wait a bit...
                Utils.waitForABit(this.errorLogger(), this.m_sleep_time);

                // DEBUG
                this.errorLogger().info("MQTT: attemptConnection(): Getting Connection status...");

                // get the connection status
                this.m_connected = this.m_connection.isConnected();

                // DEBUG
                this.errorLogger().info("MQTT: attemptConnection(): Connection status: " + this.m_connected);

                // wait once more if not connected
                if (this.m_connected == false) {
                    // DEBUG
                    this.errorLogger().warning("MQTT: attemptConnection(RETRY): Waiting a bit...");

                    // wait a bit (2x)
                    Utils.waitForABit(this.errorLogger(), this.m_sleep_time);
                    
                    // get the connection status
                    this.m_connected = this.m_connection.isConnected();
                }
            }
            catch (Exception ex) {
                // exception caught in connection attempt
                this.errorLogger().warning("MQTT: attemptConnection(): EXCEPTION in connect(): " + ex.getMessage());
                
                // get the connection status
                this.m_connected = this.m_connection.isConnected();
                if (this.m_connected == false) {
                    // DEBUG
                    this.errorLogger().warning("MQTT: attemptConnection(exception): Waiting a bit...");

                    // wait a bit...
                    Utils.waitForABit(this.errorLogger(), this.m_sleep_time);
                    
                    // get the connection status
                    this.m_connected = this.m_connection.isConnected();
                }
            }
        }
        
        // unable to connect
        if(this.m_connected == false) {
            this.errorLogger().critical("attemptConnection: multiple connection attemps FAILED!");
        }
        
        // return our connection state
        return this.m_connected;
    }

    // reset our MQTT connection... sometimes it goes wonky...
    private synchronized void resetConnection() {
        // if we have never connected before, just return
        if (this.m_has_connected == false) {
            // we've NEVER connected before... so just ignore... we may be in the middle of our first connection attempt...
            this.errorLogger().warning("resetConnection: Never Connected before... so ignoring...");
            return;
        }
        
        // if we are already in reset mode, just ignore this reset request...
        if (this.m_is_in_reset == true) {
            // we are already in reset mode... so just ignore
            this.errorLogger().warning("resetConnection: Already in reset... so ignoring this reset request...(OK).");
            return;
        }
                       
        // reset the connection
        if (this.m_connection != null) {
            // we are in reset mode
            this.m_is_in_reset = true;
            
            // DEBUG
            this.errorLogger().warning("resetConnection(MQTT): resetting MQTT connection (was previously connected)...");
        
            // ensure that we have a shadow device to reconnect to...
            if (this.m_reconnector != null) {
                // DEBUG
                this.errorLogger().info("resetConnection(MQTT): restarting MQTT connection for device: " + this.m_ep_name);

                // end us, restart with a new connection... so adios... 
                this.m_reconnector.startReconnection(this.m_ep_name,this.m_ep_type,this.m_subscribe_topics);

                // nothing more to do... we will be terminated as a thread...
            }
            else {
                // unable to re-validate shadow device
                this.errorLogger().warning("resetConnection(MQTT): unable to restart MQTT connection for device: " + this.m_ep_name + ". Restarting bridge...");
                
                // reboot bridge
                Orchestrator orchestrator = (Orchestrator)this.errorLogger().getParent();
                if (orchestrator != null) {
                    orchestrator.reset();
                }
            }
        }
        else {
            // already removed connection...
            this.errorLogger().warning("resetConnection(MQTT): Already removed connection (OK)");
        }
    }

    // subscribe to specific topics 
    public void subscribe(Topic[] list) {
        if (this.m_connection != null && this.m_connection.isConnected() == true) {
            try {
                // DEBUG
                this.errorLogger().info("MQTTTransport: Subscribing to " + list.length + " topics...");
                
                // DEBUG
                for(int i=0;i<list.length;++i) {
                    this.errorLogger().info("MQTTTransport: Subscribing to Topic[" + i + "]: " + list[i].toString());
                }

                // subscribe
                this.m_subscribe_topics = list;
                this.m_unsubscribe_topics = null;
                this.m_qoses = this.m_connection.subscribe(list);

                // DEBUG
                this.errorLogger().info("MQTTTransport: Subscribed to  " + list.length + " SUCCESSFULLY");
            }
            catch (Exception ex) {
                // unable to subscribe to topic
                this.errorLogger().warning("MQTTTransport: unable to subscribe to topic...", ex);

                // attempt reset
                this.resetConnection();
            }
        }
        else if (this.m_connection != null) {
            // unable to subscribe - not connected... 
            this.errorLogger().info("MQTTTransport: unable to subscribe. Not connected yet.");
        }
        else {
            // unable to subscribe - not connected... 
            this.errorLogger().info("MQTTTransport: unable to subscribe. Connection handle is NULL");
            
            // attempt reset
            this.resetConnection();
        }
    }

    // unsubscribe from specific topics
    public void unsubscribe(String[] list) {
        if (this.m_connection != null && this.m_connection.isConnected() == true) {
            try {
                this.m_subscribe_topics = null;
                this.m_unsubscribe_topics = list;
                this.m_connection.unsubscribe(list);
                
                // DEBUG
                this.errorLogger().info("MQTTTransport: Unsubscribed from TOPIC(s): " + list.length);
            }
            catch (Exception ex) {
                // unable to subscribe to topic
                this.errorLogger().info("MQTTTransport: unable to unsubscribe to topic...", ex);

                // attempt reset
                this.resetConnection();
            }
        }
        else if (this.m_connection != null) {
            // unable to subscribe - not connected... 
            this.errorLogger().info("MQTTTransport: unable to unsubscribe. Not connected yet.");
        }
        else {
            // unable to subscribe - not connected... 
            this.errorLogger().info("MQTTTransport: unable to unsubscribe. Connection is NULL");
            
            // attempt reset
            this.resetConnection();
        }
    }
    
    // set the retain option
    public void setRetain(boolean retain) {
        this.m_retain = retain;
    }
    
    // get the retaion option
    private boolean getRetain() {
        return this.m_retain;
    }

    /**
     * Publish a MQTT message
     *
     * @param topic
     * @param message
     */
    @Override
    public void sendMessage(String topic, String message) {
        this.sendMessage(topic, message, QoS.AT_LEAST_ONCE);
    }

    /**
     * Publish a MQTT message
     *
     * @param topic
     * @param message
     * @param qos
     * @return send status
     */
    public boolean sendMessage(String topic, String message, QoS qos) {
        boolean sent = false;
        if (this.m_connection != null && this.m_connection.isConnected() == true && message != null) {
            try {
                // DEBUG
                this.errorLogger().info("sendMessage: message: " + message + " Topic: " + topic);
                this.m_connection.publish(topic, message.getBytes(), qos, this.getRetain());

                // DEBUG
                this.errorLogger().info("sendMessage(MQTT): message sent. SUCCESS");
                sent = true;
            }
            catch (Exception ex) {
                // unable to send (EOF) - final
                this.errorLogger().warning("sendMessage:Exception in sendMessage... resetting connection. message: " + message, ex);

                // reset the connection
                this.resetConnection();
            }
        }
        else if (this.m_connection != null && message != null) {
            // unable to send (not connected yet)
            this.errorLogger().warning("sendMessage: NOT CONNECTED. Unable to send message: " + message);
            
            // XXX attempt reset (guarded by initial_connect vs. subsquent connect)
            // this.resetConnection();
            sent = true;
        }
        else if (message != null) {
            // unable to send (not connected)
            this.errorLogger().info("sendMessage: NOT CONNECTED(no handle). Unable to send message: " + message);
        }
        else {
            // unable to send (empty message)
            this.errorLogger().info("sendMessage: EMPTY MESSAGE. Not sent (OK)");
            sent = true;
        }

        // return the status
        return sent;
    }

    // get the next MQTT message
    private MQTTMessage getNextMessage() throws Exception {
        if (this.m_connection != null && this.m_connection.isConnected() == true) {
            MQTTMessage message = new MQTTMessage(this.m_connection.receive());
            if (message != null) {
                message.ack();
            }
            return message;
        }
        else if (this.m_connection != null) {
            // attempt reset (guarded by initial_connect vs. subsquent connect)
            this.resetConnection();
        }
        else {
            // no handle. throw exception 
            throw new Exception();
        }
        return null;
    }

    /**
     * Receive and process a MQTT Message
     *
     * @return
     */
    public MQTTMessage receiveAndProcessMessage() {
        MQTTMessage message = null;
        try {
            // DEBUG
            this.errorLogger().info("receiveMessage: getting next MQTT message...");
            message = this.getNextMessage();
            if (this.m_listener != null && message != null) {
                // call the registered listener to process the received message
                this.errorLogger().info("receiveAndProcessMessage(MQTT): processing new message: Topic:  " + message.getTopic() + " Mesage: " + message.getMessage());
                this.m_listener.onMessageReceive(message.getTopic(), message.getMessage());
            }
            else if (message != null) {
                // no listener
                this.errorLogger().warning("receiveAndProcessMessage(MQTT): Not processing new message: Topic:  " + message.getTopic() + " Mesage: " + message.getMessage() + ". Listener is NULL");
            }
            else {
                // no message - just skip
                this.errorLogger().info("receiveAndProcessMessage(MQTT): Not processing NULL message");
            }
        }
        catch (Exception ex) {
            // unable to receiveMessage - final
            this.errorLogger().warning("receiveAndProcessMessage(MQTT): Exception caught while connected: " + ex.getMessage(),ex);
            
            // reset the connection
            this.resetConnection();
        }
        return message;
    }

    /**
     * Disconnect from MQTT broker
     */
    @Override
    public void disconnect() {
        // disconnect will either be a close-down or resetConnection()... either way we clear all...
        this.disconnect(true);
    }

    // Disconnect from MQTT broker
    public void disconnect(boolean clear_all) {
        if (this.m_connection != null) {
            // DEBUG
            this.errorLogger().info("MQTT: disconnecting from MQTT Broker.. ");

            // disconnect... 
            try {
                this.m_connection.disconnect();
            }
            catch (Exception ex) {
                // unable to send
                this.errorLogger().warning("MQTT: exception during disconnect(). ", ex);
            }

            // DEBUG
            this.errorLogger().info("MQTT: disconnected. Cleaning up...");

            // clean up...
            super.disconnect();
            this.m_connection = null;
            this.m_has_connected = false;

            // clear the cached values 
            if (clear_all == true) {
                this.errorLogger().info("MQTT: disconnected (clean-up). Removing MQTT credentials/settings...");
                this.m_connect_host = null;
                this.m_connect_port = 0;
                this.m_connect_id = null;
                this.m_password = null;
                this.m_username = null;
                this.m_endpoint = null;
                this.m_connect_client_id = null;
                if (this.m_use_x509_auth == true && this.m_keystore_filename != null) {
                    // DEBUG
                    this.errorLogger().info("MQTT: disconnected (clean-up). Removing keystore...");
                    Utils.deleteKeystore(this.errorLogger(), this.m_keystore_filename, this.m_keystore_basename);
                }
                this.m_keystore_filename = null;
            }
        }
        else {
            // already removed connection...
            this.errorLogger().info("MQTT: already disconnected (OK)");
        }
    }

    // force use of SSL
    public void useSSLConnection(boolean use_ssl_connection) {
        this.m_use_ssl_connection = use_ssl_connection;
    }
    
    // setup the MQTT host URL
    private String setupHostURL(String host, int port,String id) {
        if (this.m_host_url == null) {
            // SSL override check...
            if (this.m_use_ssl_connection == true && this.m_mqtt_use_ssl == false) {
                // force use of SSL
                this.errorLogger().info("MQTT: OVERRIDE use of SSL enabled");
                this.m_mqtt_use_ssl = true;
            }

            // MQTT prefix determination (non-SSL)
            String prefix = "tcp://";
            
            // adjust for SSL usage when needed
            if (this.m_mqtt_use_ssl == true) {
                // initialize our SSL context
                if (this.m_port_remap == true) {
                    port += 7000;           // take mqtt_port 1883 and add 7000 --> 8883
                }
                prefix = "ssl://";      // SSL used... 
                
                // by default, we use a fixed ID for the ssl context... this may be overriden
                if (id == null) {
                    id = host.replace(".","_") + "_" + port;
                }
                
                // initialize the SSl context if not already done...
                if (this.initializeSSLContext(id) == false) {
                    // unable to initialize the SSL context
                    this.errorLogger().warning("MQTT: Unable to initialize SSL context ID: " + id);
                }
            }
            
            // complete the URL construction
            this.m_host_url = prefix + host + ":" + port;
        }
        return this.m_host_url;
    }

    // Internal MQTT Trust manager
    class MQTTTrustManager implements X509TrustManager {

        private KeyStore m_keystore = null;
        private String m_keystore_filename = null;
        private String m_keystore_pw = null;

        // constructor
        public MQTTTrustManager(String keystore_filename, String pw) {
            super();
            this.m_keystore_filename = keystore_filename;
            this.m_keystore_pw = pw;
            this.initializeTrustManager();
        }

        // intialize the Trust Manager
        private void initializeTrustManager() {
            try {
                KeyStore myTrustStore;
                try (FileInputStream myKeys = new FileInputStream(this.m_keystore_filename)) {
                    myTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                    myTrustStore.load(myKeys, this.m_keystore_pw.toCharArray());
                }
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(myTrustStore);
            }
            catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException ex) {
                errorLogger().warning("MQTTTrustManager:initializeTrustManager: FAILED to initialize", ex);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}