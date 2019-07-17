/**
 * @file Utils.java
 * @brief misc collection of static methods and functions
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
package com.arm.pelion.bridge.core;

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.mbed.lwm2m.DecodingException;
import com.mbed.lwm2m.EncodingType;
import com.mbed.lwm2m.LWM2MResource;
import com.mbed.lwm2m.base64.Base64Decoder;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

/**
 * Static method utilities
 *
 * @author Doug Anson
 */
public class Utils {
    // Keystore Type
    private static String KEYSTORE_TYPE = "JKS";
    public static String DEFAULT_PUBKEY_PEM_FILENAME = "pubkey.pem";
    
    // Keystore aliases
    private static String KEYSTORE_PRIV_KEY_ALIAS = "privkey";
    private static String KEYSTORE_PUB_KEY_ALIAS = "pubkey";
    private static String KEYSTORE_VENDOR_CERT_ALIAS = "vendor";
    private static String KEYSTORE_CA_CERT_ALIAS = "ca";
    
    // static variables
    private static char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    private static String __cache_hash = null;
    private static String _externalIPAddress = null;

    // get local timezone offset from UTC in milliseconds
    public static int getUTCOffset() {
        TimeZone tz = TimeZone.getDefault();
        Calendar cal = GregorianCalendar.getInstance(tz);
        return tz.getOffset(cal.getTimeInMillis());
    }

    // get the local time in seconds since Jan 1 1970
    public static long getLocalTime() {
        long utc = (long) (System.currentTimeMillis() / 1000);
        long localtime = utc;
        return localtime;
    }
    
    // get the local time as an RFC3339 format
    public static String getLocalTimeAsString() {
        Instant instant = Instant.ofEpochMilli ( System.currentTimeMillis() );
        return instant.toString();
    }
    
    // get the local time as an RFC3339 format
    public static String getUTCTimeAsString() {
        Instant instant = Instant.ofEpochMilli ( Utils.getUTCTime() );
        return instant.toString();
    }

    // get UTC time in seconds since Jan 1 1970
    public static long getUTCTime() {
        return Calendar.getInstance(TimeZone.getTimeZone("GMT")).getTimeInMillis() - Utils.getUTCOffset();
    }

    // get our base URL
    public static String getBaseURL(String endpoint, HttpServletRequest request) {
        String url = "";
        try {
            url = request.getRequestURL().toString().replace(request.getRequestURI().substring(1), request.getContextPath());
            url += "//" + endpoint;
            url = url.replace("://", "_TEMP_");
            url = url.replace("//", "/");
            url = url.replace("_TEMP_", "://");
        }
        catch (Exception ex) {
            url = request.getRequestURL().toString();
        }
        return url;
    }

    // convert boolean to string
    public static String booleanToString(boolean val) {
        if (val) {
            return "true";
        }
        return "false";
    }

    // convert string to boolean
    public static boolean stringToBoolean(String val) {
        boolean bval = false;
        if (val != null && val.equalsIgnoreCase("true")) {
            bval = true;
        }
        return bval;
    }

    // START DATE FUNCTIONS
    // get the current date and time
    public static java.util.Date now() {
        java.util.Date rightnow = new java.util.Date(System.currentTimeMillis());
        return rightnow;
    }

    // convert a JAVA Date to a SQL Timestamp and back
    public static java.sql.Timestamp convertDate(java.util.Date date) {
        java.sql.Timestamp sql_date = new java.sql.Timestamp(date.getTime());
        sql_date.setTime(date.getTime());
        return sql_date;
    }

    // convert SQL Date to Java Date
    public static java.util.Date convertDate(java.sql.Timestamp date) {
        java.util.Date java_date = new java.util.Date(date.getTime());
        return java_date;
    }

    // convert a Date to a String (java)
    public static String dateToString(java.util.Date date) {
        return Utils.dateToString(date, "MM/dd/yyyy HH:mm:ss");
    }

    // Date to Date String
    public static String dateToString(java.util.Date date, String format) {
        if (date != null) {
            DateFormat df = new SimpleDateFormat(format);
            return df.format(date);
        }
        else {
            return "[no date]";
        }
    }

    // convert a SQL Timestamp to a String (SQL)
    public static String dateToString(java.sql.Timestamp timestamp) {
        if (timestamp != null) {
            return Utils.dateToString(new java.util.Date(timestamp.getTime()));
        }
        else {
            return "[no date]";
        }
    }

    // convert a Date to a String (SQL)
    public static String dateToString(java.sql.Date date) {
        if (date != null) {
            return Utils.dateToString(new java.util.Date(date.getTime()));
        }
        else {
            return "[no date]";
        }
    }

    // convert a String (Java) to a java.util.Date object
    public static java.util.Date stringToDate(ErrorLogger err, String str_date) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            String stripped = str_date.replace('"', ' ').trim();
            return dateFormat.parse(stripped);
        }
        catch (ParseException ex) {
            err.warning("Unable to parse string date: " + str_date + " to format: \"MM/dd/yyyy HH:mm:ss\"", ex);
        }
        return null;
    }

    // END DATE FUNCTIONS
    // Hex String to ByteBuffer or byte[]
    public static ByteBuffer hexStringToByteBuffer(String str) {
        return ByteBuffer.wrap(Utils.hexStringToByteArray(str));
    }

    // hex String to ByteArray
    public static byte[] hexStringToByteArray(String str) {
        int len = str.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4) + Character.digit(str.charAt(i + 1), 16));
        }
        return data;
    }

    // convert a hex byte array to a string
    public static String bytesToHexString(ByteBuffer bytes) {
        return Utils.bytesToHexString(bytes.array());
    }

    // ByteArray to hex string
    public static String bytesToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; ++j) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    // read in a HTML file
    public static String readHTMLFileIntoString(HttpServlet svc, ErrorLogger err, String filename) {
        try {
            String text = null;
            String file = "";
            ServletContext context = svc.getServletContext();
            try (InputStream is = context.getResourceAsStream("/" + filename); InputStreamReader isr = new InputStreamReader(is); BufferedReader reader = new BufferedReader(isr)) {
                while ((text = reader.readLine()) != null) {
                    file += text;
                }
            }
            return file;
        }
        catch (IOException ex) {
            err.critical("error while trying to read HTML template: " + filename, ex);
        }
        return null;
    }

    // decode CoAP payload Base64
    public static String decodeCoAPPayload(String payload) {
        String decoded = null;

        try {
            String b64_payload = payload.replace("\\u003d", "=");
            Base64 decoder = new Base64();
            byte[] data = decoder.decode(b64_payload);
            decoded = new String(data);
        }
        catch (Exception ex) {
            decoded = "<unk>";
        }

        return decoded;
    }

    // create a URL-safe Token
    public static String createURLSafeToken(String seed) {
        try {
            byte[] b64 = Base64.encodeBase64(seed.getBytes());
            return new String(b64);
        }
        catch (Exception ex) {
            return "exception";
        }
    }

    // create Authentication Hash
    public static String createHash(String data) {
        try {
            if (data == null) {
                return "none";
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data.getBytes());
            String hex = Hex.encodeHexString(digest);
            return Base64.encodeBase64URLSafeString(hex.getBytes());
        }
        catch (NoSuchAlgorithmException ex) {
            return "none";
        }
    }

    // validate the Authentication Hash
    public static boolean validateHash(String header_hash, String calc_hash) {
        boolean validated = false;
        try {
            if (Utils.__cache_hash == null) {
                validated = (header_hash != null && calc_hash != null && calc_hash.equalsIgnoreCase(header_hash) == true);
                if (validated && Utils.__cache_hash == null) {
                    Utils.__cache_hash = header_hash;
                }
            }
            else {
                validated = (header_hash != null && Utils.__cache_hash != null && Utils.__cache_hash.equalsIgnoreCase(header_hash) == true);
            }
            return validated;
        }
        catch (Exception ex) {
            return false;
        }
    }

    // get our external IP Address
    public static String getExternalIPAddress() {
        if (Utils._externalIPAddress == null) {
            BufferedReader in = null;
            try {
                // this does not seem to work correctly for MS Azure VMs... so you need to set mds_gw_address in service.properties to override
                URL whatismyip = new URL("http://checkip.amazonaws.com");
                in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
                Utils._externalIPAddress = in.readLine();
                in.close();
            }
            catch (IOException ex) {
                try {
                    if (in != null) {
                        in.close();
                    }
                }
                catch (IOException ex2) {
                    // silent
                }
            }
        }
        return Utils._externalIPAddress;
    }

    // convert a InputStream to a String
    public static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    /**
     * Execute the AWS CLI
     *
     * @param logger - ErrorLogger instance
     * @param args - arguments for the AWS CLI
     * @return response from CLI action
     */
    public static String awsCLI(ErrorLogger logger, String args) {
        // construct the arguments
        String cmd = "./aws " + args;
        String response = null;
        String error = null;

        try {
            // invoke the AWS CLI
            Process proc = Runtime.getRuntime().exec(cmd);
            response = Utils.convertStreamToString(proc.getInputStream());
            error = Utils.convertStreamToString(proc.getErrorStream());

            // wait to tcompletion
            proc.waitFor();
            int status = proc.exitValue();

            // DEBUG
            if (status != 0) {
                // non-zero exit status
                logger.info("AWS CLI: Invoked: " + cmd);
                logger.info("AWS CLI: Response: " + response);
                logger.info("AWS CLI: Error: " + error);
                logger.info("AWS CLI: Exit Code: " + status);
                
                // nullify the response..
                response = null;
            }
            else {
                // successful exit status
                logger.info("AWS CLI: Invoked: " + cmd);
                logger.info("AWS CLI: Response: " + response);
                logger.info("AWS CLI: Exit Code: " + status);
                
                // trim the response
                response = response.trim();
                
                // if no response... but successful... lets put in an error code
                if (response == null || response.length() == 0) {
                    response = "{\"status\":" + status + "}";
                }
            }
        }
        catch (IOException | InterruptedException ex) {
            // note the exception - it needs to be looked into...
            logger.warning("AWS CLI: Exception for command: " + cmd, ex);
            
            // nullify the response...
            response = null;
        }

        // return the resposne
        return response;
    }

    // escape chars utility
    public static String escapeChars(String str) {
        return str.replace("\\n", "");
    }

    // Create CA Root certificate
    public static X509Certificate createCACertificate(ErrorLogger logger) {
        // Root CA for AWS IoT (5/6/2016)
        // https://www.symantec.com/content/en/us/enterprise/verisign/roots/VeriSign-Class%203-Public-Primary-Certification-Authority-G5.pem
        String pem = "-----BEGIN CERTIFICATE-----"
                + "MIIE0zCCA7ugAwIBAgIQGNrRniZ96LtKIVjNzGs7SjANBgkqhkiG9w0BAQUFADCB"
                + "yjELMAkGA1UEBhMCVVMxFzAVBgNVBAoTDlZlcmlTaWduLCBJbmMuMR8wHQYDVQQL"
                + "ExZWZXJpU2lnbiBUcnVzdCBOZXR3b3JrMTowOAYDVQQLEzEoYykgMjAwNiBWZXJp"
                + "U2lnbiwgSW5jLiAtIEZvciBhdXRob3JpemVkIHVzZSBvbmx5MUUwQwYDVQQDEzxW"
                + "ZXJpU2lnbiBDbGFzcyAzIFB1YmxpYyBQcmltYXJ5IENlcnRpZmljYXRpb24gQXV0"
                + "aG9yaXR5IC0gRzUwHhcNMDYxMTA4MDAwMDAwWhcNMzYwNzE2MjM1OTU5WjCByjEL"
                + "MAkGA1UEBhMCVVMxFzAVBgNVBAoTDlZlcmlTaWduLCBJbmMuMR8wHQYDVQQLExZW"
                + "ZXJpU2lnbiBUcnVzdCBOZXR3b3JrMTowOAYDVQQLEzEoYykgMjAwNiBWZXJpU2ln"
                + "biwgSW5jLiAtIEZvciBhdXRob3JpemVkIHVzZSBvbmx5MUUwQwYDVQQDEzxWZXJp"
                + "U2lnbiBDbGFzcyAzIFB1YmxpYyBQcmltYXJ5IENlcnRpZmljYXRpb24gQXV0aG9y"
                + "aXR5IC0gRzUwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCvJAgIKXo1"
                + "nmAMqudLO07cfLw8RRy7K+D+KQL5VwijZIUVJ/XxrcgxiV0i6CqqpkKzj/i5Vbex"
                + "t0uz/o9+B1fs70PbZmIVYc9gDaTY3vjgw2IIPVQT60nKWVSFJuUrjxuf6/WhkcIz"
                + "SdhDY2pSS9KP6HBRTdGJaXvHcPaz3BJ023tdS1bTlr8Vd6Gw9KIl8q8ckmcY5fQG"
                + "BO+QueQA5N06tRn/Arr0PO7gi+s3i+z016zy9vA9r911kTMZHRxAy3QkGSGT2RT+"
                + "rCpSx4/VBEnkjWNHiDxpg8v+R70rfk/Fla4OndTRQ8Bnc+MUCH7lP59zuDMKz10/"
                + "NIeWiu5T6CUVAgMBAAGjgbIwga8wDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8E"
                + "BAMCAQYwbQYIKwYBBQUHAQwEYTBfoV2gWzBZMFcwVRYJaW1hZ2UvZ2lmMCEwHzAH"
                + "BgUrDgMCGgQUj+XTGoasjY5rw8+AatRIGCx7GS4wJRYjaHR0cDovL2xvZ28udmVy"
                + "aXNpZ24uY29tL3ZzbG9nby5naWYwHQYDVR0OBBYEFH/TZafC3ey78DAJ80M5+gKv"
                + "MzEzMA0GCSqGSIb3DQEBBQUAA4IBAQCTJEowX2LP2BqYLz3q3JktvXf2pXkiOOzE"
                + "p6B4Eq1iDkVwZMXnl2YtmAl+X6/WzChl8gGqCBpH3vn5fJJaCGkgDdk+bW48DW7Y"
                + "5gaRQBi5+MHt39tBquCWIMnNZBU4gcmU7qKEKQsTb47bDN0lAtukixlE0kF6BWlK"
                + "WE9gyn6CagsCqiUXObXbf+eEZSqVir2G3l6BFoMtEMze/aiCKm0oHw0LxOXnGiYZ"
                + "4fQRbxC1lfznQgUy286dUV4otp6F01vvpX1FQHKOtw5rDgb7MzVIcbidJ4vEZV8N"
                + "hnacRHr2lVz2XTIIM6RUthg/aFzyQkqFOFSDX9HoLPKsEdao7WNq"
                + "-----END CERTIFICATE-----";

        return Utils.createX509CertificateFromPEM(logger, pem, "X509");
    }
    
    // Google: create the RSA Keys for a given device...
    public static String createRSAKeysforDevice(ErrorLogger logger,String root_dir,int num_days,String cmd,String cvt_cmd,int key_length,String id) {
        String filename = root_dir + "/" + id + "/" + "private.pem";
        String pkcs8_filename = root_dir + "/" + id + "/" + "private.pkcs8";
        String pub_filename = root_dir + "/" + id + "/" + "cert.pem";
        String final_cmd = cmd.replace("__PRIV_KEY_FILE__",filename)
                              .replace("__CERT_FILE__",pub_filename)
                              .replace("__NUM_DAYS__","" + num_days)
                              .replace("__KEY_LENGTH__","" + key_length);
        String final_cvt_cmd = cvt_cmd.replace("__PRIV_KEY_FILE__",filename)
                              .replace("__PRIV_KEY_PKCS8__",pkcs8_filename);
        
        
        // make the root directory if it does not exist
        try {
            File rootdir = new File(root_dir + "/" + id);
            if (rootdir.exists() == false) {
                // make the root directory
                rootdir.mkdirs();
            }
        }
        catch(Exception ex) {
            // silence
        }
        
        // execute the command
        try {
            // DEBUG
            logger.info("createRSAKeysforDevice: OpenSSL Create Command: " + final_cmd);
            
            // invoke the OpenSSL command
            Process proc = Runtime.getRuntime().exec(final_cmd);
            String response = Utils.convertStreamToString(proc.getInputStream());
            String error = Utils.convertStreamToString(proc.getErrorStream());

            // wait to completion
            proc.waitFor();
            int status = proc.exitValue();

            // DEBUG
            if (status != 0) {
                // command failed
                logger.warning("createRSAKeysforDevice: ERROR: Unable to create device keys... status=" + status + " out: " + response + " error: " + error);
                filename = null;
            }
            else {
                // command succeeded
                logger.info("createRSAKeysforDevice: device keys created SUCCESSFULLY");
                
                // DEBUG
                logger.info("createRSAKeysforDevice: OpenSSL Convert Command: " + final_cvt_cmd);
                
                // now convert to PKCS8
                proc = Runtime.getRuntime().exec(final_cvt_cmd);
                response = Utils.convertStreamToString(proc.getInputStream());
                error = Utils.convertStreamToString(proc.getErrorStream());
                
                // wait to completion
                proc.waitFor();
                status = proc.exitValue();
                if (status != 0) {
                    // command failed
                    logger.warning("createRSAKeysforDevice: ERROR: Unable convert to PKCS8... status=" + status + " out: " + response + " error: " + error);
                    filename = null;
                }
                else {
                    // command succeeded
                    logger.info("createRSAKeysforDevice: device keys converted to PKCS8 SUCCESSFULLY");
                    
                    // remove the old private file
                    File doomed = new File(filename);
                    if (doomed.delete()) {
                        // command succeeded
                        logger.info("createRSAKeysforDevice: old PEM private key deleted SUCCESSFULLY");
                        filename = pkcs8_filename;
                    }
                    else {
                        // command FAILED
                        logger.info("createRSAKeysforDevice: old PEM private key deletion FAILED: " + filename);
                    }
                }
            }
        }
        catch (IOException | InterruptedException ex) {
            // DEBUG
            logger.info("createRSAKeysforDevice: Exception occured: " + ex.getMessage(),ex);
            
            // exception in the command 
            filename = null;
        }
        
        // return the keystore filename
        return filename;
    }
    
    // Google: read all bytes from a given devices' keyfile
    public static byte[] readRSAKeyforDevice(ErrorLogger logger, String root_dir, String id,boolean priv_key) {
        String filename = root_dir + "/" + id + "/" + "private.pkcs8";
        String cert_filename = root_dir + "/" + id + "/" + "cert.pem";
        String debug_filename = filename;
        try {
            if (priv_key == true) {
                debug_filename = filename;
                return Files.readAllBytes(Paths.get(filename));
            }
            else {
                debug_filename = cert_filename;
                return Files.readAllBytes(Paths.get(cert_filename));
            }
        }
        catch (IOException ex) {
            // unable to read from the PEM file
            logger.warning("readPrivateKeyForDevice: WARNING Unable to read from PEM file: " + debug_filename + " ID: " + id + " message: " + ex.getMessage());
        }
        return new byte[0];
    }
    
    // convert X509 to PEM
    public static String convertX509ToPem(X509Certificate cert) throws CertificateEncodingException {
        return Utils.convertX509ToPem(cert.getEncoded());
    }
    
    // convert private key to PEM
    public static String convertPrivKeyToPem(KeyPair keys) {
        return Utils.convertPrivKeyToPem(keys.getPrivate().getEncoded()); 
    }
    
    // convert public key to PEM
    public static String convertPubKeyToPem(KeyPair keys) {
        return Utils.convertPubKeyToPem(keys.getPublic().getEncoded()); 
    }
    
    // convert X509 to PEM
    public static String convertX509ToPem(byte[] cert) throws CertificateEncodingException {
        return Utils.convertToPem("BEGIN CERTIFICATE","END CERTIFICATE",cert);
    }
    
    // convert private key to PEM
    public static String convertPrivKeyToPem(byte[] priv) {
        return Utils.convertToPem("BEGIN RSA PRIVATE KEY","END RSA PRIVATE KEY",priv); 
    }
    
    // convert public key to PEM
    public static String convertPubKeyToPem(byte[] pub) {
        return Utils.convertToPem("BEGIN PUBLIC KEY","END PUBLIC KEY",pub); 
    }
    
    // convert encoded data into PEM
    public static String convertToPem(String start, String stop, byte[] data) {
        java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();
        String begin = "-----" + start + "-----\n";
        String end = "-----" + stop + "-----";

        String str_data = new String(encoder.encode(data));
        String str_pem = begin + str_data + end;
        return str_pem;
    }
    
    // Read the X.509 Cert from the Keystore in PEM format
    public static String readCertFromKeystoreAsPEM(ErrorLogger logger,String filename,String pw) {
        try {
            FileInputStream is = new FileInputStream(filename);
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            char[] passwd = pw.toCharArray();
            keystore.load(is, passwd);
            Key cert = keystore.getKey(Utils.KEYSTORE_VENDOR_CERT_ALIAS, passwd);
            if (cert instanceof X509Certificate) {
                return Utils.convertX509ToPem(cert.getEncoded());
            }
            else {
                // error
                logger.warning("readCertFromKeystoreAsPEM: Cert not instance of X509Certificate... ERROR");
            }
        }
        catch (IOException | KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException ex) {
            // error
            logger.warning("readCertFromKeystoreAsPEM: Exception: " + ex.getMessage());
        }
        return null;
    }
    
    // Read the Private Key from the Keystore in PEM format 
    public static String readPrivKeyFromKeystoreAsPEM(ErrorLogger logger,String filename,String pw) {
        try {
            FileInputStream is = new FileInputStream(filename);
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            char[] passwd = pw.toCharArray();
            keystore.load(is, passwd);
            Key key = keystore.getKey(Utils.KEYSTORE_PRIV_KEY_ALIAS, passwd);
            if (key instanceof PrivateKey) {
                return Utils.convertPrivKeyToPem(key.getEncoded());
            }
            else {
                // error
                logger.warning("readPrivKeyFromKeystoreAsPEM: Key not instance of PrivateKey... ERROR");
            }
        }
        catch (IOException | KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException ex) {
            // error
            logger.warning("readPrivKeyFromKeystoreAsPEM: Exception: " + ex.getMessage());
        }
        return null;
    }
    
    // Read the Public Key from the Keystore in PEM format
    public static String readPubKeyAsPEM(ErrorLogger logger,String filename) {
        try {
            String pem = "";
            try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
                String line = null;
                while ((line = br.readLine()) != null) {
                    pem += line + "\n";
                }
            }
            PublicKey key = Utils.createPublicKeyFromPEM(logger,pem,"RSA");
            if (key != null) {
                return Utils.convertPubKeyToPem(key.getEncoded());
            }
            else {
                // error
                logger.warning("readPubKeyAsPEM: Public key not found in PEM file: " + filename);
            }
        }
        catch (IOException ex) {
            // error
            logger.warning("readPubKeyAsPEM: Exception: " + ex.getMessage());
        }
        return null;
    }
   
    // make the keystore filename
    public static String makeKeystoreFilename(String base, String sep, String filename) {
        String basedir = base + File.separator + sep;
        return basedir + File.separator + filename;
    }
    
    // make the pubkey PEM filename
    public static String makePubkeyFilename(String base, String sep, String filename) {
        String basedir = base + File.separator + sep;
        return basedir + File.separator + filename;
    }

    // create a Keystore
    public static String createKeystore(ErrorLogger logger, String base, String sep, String filename, X509Certificate cert, PrivateKey priv_key, PublicKey pub_key, String pw) {
        String basedir = base + File.separator + sep;
        String keystore_filename = Utils.makeKeystoreFilename(base, sep, filename);

        try {
            // first create the directory if it does not exist
            File file = new File(basedir);

            // make the directories
            logger.info("createKeystore: Making directories for keystore...");
            file.mkdirs();

            // create the KeyStore
            logger.info("createKeystore: Creating keystore: " + keystore_filename);
            file = new File(keystore_filename);
            if (file.createNewFile()) {
                logger.info("createKeystore: keystore created:  " + keystore_filename);
            }
            else {
                logger.info("createKeystore: keystore already exists " + keystore_filename);
            }

            // store data into the keystore
            KeyStore ks = KeyStore.getInstance(Utils.KEYSTORE_TYPE);
            ks.load(null, pw.toCharArray());

            // set the certificate, priv and pub keys
            if (cert != null) {
                Certificate[] cert_list = new Certificate[2];
                cert_list[0] = cert;                                // Vendor
                cert_list[1] = Utils.createCACertificate(logger);   // CA

                ks.setCertificateEntry(Utils.KEYSTORE_VENDOR_CERT_ALIAS, cert_list[0]);
                ks.setCertificateEntry(Utils.KEYSTORE_CA_CERT_ALIAS, cert_list[1]);

                if (priv_key != null) {
                    try {
                        ks.setKeyEntry(Utils.KEYSTORE_PRIV_KEY_ALIAS, priv_key, pw.toCharArray(), cert_list);
                    }
                    catch (KeyStoreException ex2) {
                        logger.warning("createKeystore: Exception during priv addition... not added to keystore", ex2);
                    }
                }
                else {
                    logger.warning("createKeystore: privkey is NULL... not added to keystore");
                }
            }
            else {
                logger.warning("createKeystore: certificate is NULL... not added to keystore");
            }

            try (FileOutputStream fos = new FileOutputStream(keystore_filename)) {
                // store away the keystore content
                ks.store(fos, pw.toCharArray());

                // close
                fos.flush();
            }
        }
        catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException ex) {
            logger.warning("createKeystore: Unable to create keystore: " + keystore_filename, ex);
        }

        // return the keystore filename
        return keystore_filename;
    }

    // generate a keystore password for the device shadow's keystore holding its creds... 
    public static String generateKeystorePassword(ErrorLogger logger,String base_pw, String id, String salt) {
        // create a long password for the keystore that holds the device shadow creds
        return Utils.createHash(salt + base_pw + UUID.randomUUID() + id);
    }

    // remove the keystore from the filesystem
    public static void deleteKeystore(ErrorLogger logger, String filename, String keystore_name) {
        try {
            // DEBUG
            logger.info("deleteKeystore: deleting keystore: " + filename);

            // Delete the KeyStore
            File file = new File(filename);
            if (file.delete()) {
                // success
                logger.info(file.getName() + " is deleted!");
            }
            else {
                // failure
                logger.warning("Delete operation is failed: " + filename);
            }

            // Create the parent directory
            String basedir = filename.replace("/" + keystore_name, "");

            // DEBUG
            logger.info("deleteKeystore: deleting keystore parent directory: " + basedir);

            // Delete the Base Directory
            file = new File(basedir);
            if (file.isDirectory()) {
                if (file.delete()) {
                    // success
                    logger.info(basedir + " is deleted!");
                }
                else {
                    // failure
                    logger.warning("Delete operation is failed : " + basedir);
                }
            }

        }
        catch (Exception ex) {
            // exception caught
            logger.warning("deleteKeystore: Exception during deletion of keystore: " + filename, ex);
        }
    }

    // Create X509Certificate from PEM
    static public X509Certificate createX509CertificateFromPEM(ErrorLogger logger, String pem, String cert_type) {
        try {
            String temp = Utils.escapeChars(pem);
            String certPEM = temp.replace("-----BEGIN CERTIFICATE-----", "");
            certPEM = certPEM.replace("-----END CERTIFICATE-----", "");

            // DEBUG
            //logger.info("createX509CertificateFromPEM: " + certPEM);
            Base64 b64 = new Base64();
            byte[] decoded = b64.decode(certPEM);

            CertificateFactory cf = CertificateFactory.getInstance(cert_type);
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(decoded));
        }
        catch (CertificateException ex) {
            // exception caught
            logger.warning("createX509CertificateFromPEM: Exception during private key gen", ex);
        }
        return null;
    }

    // Create PrivateKey from PEM
    static public PrivateKey createPrivateKeyFromPEM(ErrorLogger logger, String pem, String algorithm) {
        try {
            String temp = Utils.escapeChars(pem);
            String privKeyPEM = temp.replace("-----BEGIN RSA PRIVATE KEY-----", "");
            privKeyPEM = privKeyPEM.replace("-----END RSA PRIVATE KEY-----", "");
            
            // some dont have RSA in them... 
            privKeyPEM = privKeyPEM.replace("-----BEGIN PRIVATE KEY-----", "");
            privKeyPEM = privKeyPEM.replace("-----END PRIVATE KEY-----", "");

            // DEBUG
            //logger.info("createPrivateKeyFromPEM: " + privKeyPEM);
            Base64 b64 = new Base64();
            byte[] decoded = b64.decode(privKeyPEM);

            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory kf = KeyFactory.getInstance(algorithm);
            return kf.generatePrivate(spec);
        }
        catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            // exception caught
            logger.warning("createPrivateKeyFromPEM: Exception during private key gen", ex);
        }
        return null;
    }

    // Create PublicKey from PEM
    static public PublicKey createPublicKeyFromPEM(ErrorLogger logger, String pem, String algorithm) {
        try {
            String temp = Utils.escapeChars(pem);
            String publicKeyPEM = temp.replace("-----BEGIN PUBLIC KEY-----", "");
            publicKeyPEM = publicKeyPEM.replace("-----END PUBLIC KEY-----", "");

            // DEBUG
            //logger.info("createPublicKeyFromPEM: " + publicKeyPEM);
            Base64 b64 = new Base64();
            byte[] decoded = b64.decode(publicKeyPEM);

            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory kf = KeyFactory.getInstance(algorithm);
            return kf.generatePublic(spec);
        }
        catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            // exception caught
            logger.warning("createPublicKeyFromPEM: Exception during public key gen", ex);
        }
        return null;
    }

    // ensure that an HTTP response code is in the 200's
    public static boolean httpResponseCodeOK(int code) {
        int check = code - 200;
        if (check >= 0 && check < 100) {
            return true;
        }
        return false;
    }

    // re-type a JSON Map
    public static Map retypeMap(Map json, TypeDecoder decoder) {
        HashMap<String, Object> remap = new HashMap<>();

        // iterate through the existing map and re-type each entry
        Iterator it = json.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            Object value = decoder.getFundamentalValue(pair.getValue());
            if (value instanceof Double || value instanceof Integer || value instanceof String) {
                // fundamental types get mapped directly
                remap.put((String) pair.getKey(), value);
            }
            else {
                // this is a complex embedded type...
                if (value instanceof Map) {
                    // embedded JSON - directly recurse.
                    remap.put((String) pair.getKey(), Utils.retypeMap((Map) value, decoder));
                }
                if (value instanceof List) {
                    // list of embedded JSON - loop and recurse each Map(i)... 
                    List list = (List) value;
                    for (int i = 0; i < list.size(); ++i) {
                        list.set(i, Utils.retypeMap((Map) (Map) list.get(i), decoder));
                    }
                    // replace list with new one...
                    remap.put((String) pair.getKey(), list);
                }
            }
        }

        return remap;
    }
    
    // simple ugly replacement of oddball characters
    public static String replaceAllCharOccurances(String my_string,char out_char,char in_char) {
        // fix up from config file
        if (my_string != null && my_string.length() > 0) {
            char[] tmp_array = my_string.toCharArray();
            for(int i=0;i<tmp_array.length;++i) {
                if (tmp_array[i] == out_char) {
                    tmp_array[i] = in_char;
                }
            }
            return String.valueOf(tmp_array);
        }
        return my_string;
    }
    
    // replace "n" occurances of a given char within a string (Google Cloud specific formatting)
    public static String replaceCharOccurances(String data,char m,char r,int num_to_replace) {
        if (data != null && data.length() > 0) {
            // first are going to strip out the first 3 "slashes" and leave the rest... additional formatting will be applied later
            StringBuilder sb = new StringBuilder();
            int length = data.length();
            for(int i=0;i<length;++i) {
                if (data.charAt(i) == m && num_to_replace > 0) {
                    sb.append(r);
                    --num_to_replace;
                }
                else if (data.charAt(i) == m && num_to_replace == 0) {
                    // we found another ... so add one more delimiter then add the original and continue... do this only once...
                    // (Google Cloud specific formatting)
                    sb.append(r);
                    sb.append(m);
                    num_to_replace = -1;
                }
                else {
                    sb.append(data.charAt(i));
                }
            }
            return sb.toString();
        }
        return data;
    }
    
    // where am I?
    public static void whereAmI(ErrorLogger logger) {
        Exception ex = new Exception();
        logger.info("whereAmI: StackTrace",ex);
    }
    
    // TLV Decode to LWM2M Resource list (ARM SDK)
    public static List<LWM2MResource> tlvDecodeToLWM2MObjectList(ErrorLogger logger,String payload) {
        List<LWM2MResource> list = null;
        if (payload != null && payload.length() > 0) {
            // use the TLV decoder from ARM mbed
            try {
                // Get the LWM2MResource instance list
                list = Base64Decoder.decodeBase64(ByteBuffer.wrap(payload.getBytes()),LWM2MResource.class,EncodingType.TLV);
                
                // DEBUG
                if (list != null) {
                    // parsed with content
                    logger.info("tlvDecodeToLWM2MObjectList: list length: " + list.size() + " payload: " + payload);
                    logger.info("tlvDecodeToLWM2MObjectList: list: " + list);
                }
                else {
                    // empty parse
                    logger.info("tlvDecodeToLWM2MObjectList: parsed list is EMPTY for payload: " + payload);
                }
            }
            catch (DecodingException ex) {
                // error in decoding
                logger.warning("Utils.tlvDecodeToLWM2MObjectList: Exception caught in TLV decode: " + ex.getMessage());
            }
        }
        
        // return the list of LWM2M objects
        return list;
    }
    
    // get the specific LWM2M resource instance by resource ID from the list
    public static LWM2MResource getLWM2MResourceInstanceByResourceID(List<LWM2MResource> list,int id) {
        LWM2MResource res = null;
        
        // loop to find the appropriate LWM2MResource
        for(int i=0;list != null && res == null && i<list.size();++i) {
            LWM2MResource tmp = list.get(i);
            if (tmp.getId().intValue() == id) {
                res = tmp;
            }
        }
        return res;
    }
    
    // get the specific LWM2M resource value by resource ID from the list
    public static String getLWM2MResourceValueByResourceID(ErrorLogger logger,List<LWM2MResource> list,int id) {
        String value = null;
        
        LWM2MResource res = Utils.getLWM2MResourceInstanceByResourceID(list,id);
        if (res != null) {
            // found the resource... so get the value of it
            value = res.getStringValue();
            
            // DEBUG
            logger.info("Utils.getLWM2MResourceValueByResourceID: ID: " + id + " Value: " + value);
        }
        else {
            // no such resource ID
            logger.info("Utils.getLWM2MResourceValueByResourceID: no resource found for ID: " + id);
        }
        return value;
    }
    
    // wait a bit
    public static void waitForABit(ErrorLogger logger, long wait_time_ms) {
        try {
            Thread.sleep(wait_time_ms);
        }
        catch (InterruptedException ex) {
            if (logger != null) {
                logger.info("waitForABit: sleep interrupted: " + ex.getMessage() + " callstack: ",ex);
            }
        }
    }
    
    // extract the certificateId from the AWS principal ARN
    public static String pullCertificateIdFromAWSPrincipal(String arn) {
        if (arn != null && arn.length() > 0) {
            // format: arn:aws:iot:us-east-1:<id>:cert/<certid>
            String[] elements = arn.split(":");
            
            // pull the last element
            int cert_id_index = elements.length - 1;
            String cert_full = elements[cert_id_index];
            
            // remove the "cert/" from the rest of the cert ID
            return cert_full.replace("cert/","");
        }
        return null;
    }
    
    // reset the bridge
    public static void resetBridge(ErrorLogger logger,String message) {
        if (logger != null) {
            // do a full reset 
            if (logger.getParent() != null && logger.getParent() instanceof Orchestrator) {
                Orchestrator orchestrator = (Orchestrator)logger.getParent();
                
                // RESET
                logger.warning("Utils: Performing bridge RESET: " + message + "...");
                orchestrator.reset();
            }
        }
    }
    
    // chops a list into non-view sublists of length L
    // credit: https://stackoverflow.com/questions/2895342/java-how-can-i-split-an-arraylist-in-multiple-small-arraylists
    public static <T> List<List<T>> chopList(List<T> list, final int L) {
        List<List<T>> parts = new ArrayList<>();
        if (list != null) {
            int N = list.size();
            for (int i = 0; i < N; i += L) {
                parts.add(new ArrayList<>(list.subList(i, Math.min(N, i + L))));
            }
        }
        return parts;
    }
    
    // Create the IoTHub SAS Token
    public static String CreateIoTHubSASToken(ErrorLogger logger,String resourceUri, String keyName, String key, long validity_time_ms) {
        String expiry = Utils.CreateSASTokenExpirationTime(logger,validity_time_ms);
        String sasToken = null;
        
        try {
            String stringToSign = URLEncoder.encode(resourceUri, "UTF-8") + "\n" + expiry;
            String signature = Utils.GenerateIoTHubSASTokenHMAC256(logger,key,stringToSign);
            sasToken = "SharedAccessSignature sr=" + URLEncoder.encode(resourceUri, "UTF-8") +"&sig=" + URLEncoder.encode(signature, "UTF-8") + "&se=" + expiry + "&skn=" + keyName;
        } 
        catch (UnsupportedEncodingException ex) {
            logger.warning("Utils(CreateIoTHubSASToken): Exception caught: " + ex.getMessage());
        }
        
        // DEBUG
        logger.info("Utils(CreateIoTHubSASToken): Generated Token: " + sasToken);
        
        // return the SAS Token
        return sasToken;
    }
    
    // Calculate the IoTHub SAS Token Expiration Time
    private static String CreateSASTokenExpirationTime(ErrorLogger logger,long validity_time_ms) { 
        long now = System.currentTimeMillis()/1000L;
        return Long.toString(now + validity_time_ms);
    }

    // SAS Token HMAC256 Generator
    private static String GenerateIoTHubSASTokenHMAC256(ErrorLogger logger,String key, String input) {
        Mac sha256_HMAC = null;
        String hash = null;
        try {
            sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(Base64.decodeBase64(key),"HmacSHA256");
            sha256_HMAC.init(secret_key);
            hash = new String(Base64.encodeBase64(sha256_HMAC.doFinal(input.getBytes("UTF-8"))));
        } 
        catch (InvalidKeyException | NoSuchAlgorithmException | IllegalStateException | UnsupportedEncodingException ex) {
            logger.warning("Utils(GenerateIoTHubSASTokenHMAC256):getHMAC256: Exception caught: " + ex.getMessage());
        }
        return hash;
    }
    
    // is a full URI (objectID/instanceID/resourceID)
    public static boolean isCompleteURI(String uri) {
        if (uri != null && uri.length() > 0) {
            String[] elements = uri.split("/");
            if (elements != null && elements.length > 0) {
                // Format1:  /object_id/instance_id/resource_id == 4 
                // Format2:   object_id/instance_id/resource_id == 3
                if (uri.charAt(0) == '/') {
                    if (elements.length >= 4) {
                        return true;
                    }
                }
                else {
                    if (elements.length >= 3) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    // is the URI a reserved or otherwise handled URI
    public static boolean isHandledURI(String uri) {
        if (uri != null) {
            String object_id = Utils.objectIDFromURI(uri);
            try {
                Integer oid = Integer.parseInt(object_id);
                if (oid > 0) {
                    if (oid == 1 || oid == 3 || oid == 5 || oid == 10255 || oid == 5000 || oid == 10252 || oid == 35011) {
                        return true;
                    }
                }
            }
            catch (NumberFormatException ex) {
                // silent
            }
        }
        return false;
    }
    
    // get the resource ID from the URI
    public static String resourceIDFromURI(String uri) {
        String resource_id = "";
        if (uri != null && uri.length() > 0) {
            String[] elements = uri.split("/");
            if (elements.length > 0) {
                // Format:  /object_id/instance_id/resource_id
                resource_id = elements[elements.length-1];
            }
        }
        return resource_id;
    }
    
    // get the object ID from the URI
    public static String objectIDFromURI(String uri) {
        String object_id = "";
        if (uri != null && uri.length() > 0) {
            String[] elements = uri.split("/");
            if (elements.length > 0) {
                if (uri.charAt(0) == '/') {
                    // Format:  /object_id/instance_id/resource_id
                    object_id = elements[1];
                }
                else {
                    // Format:  object_id/instance_id/resource_id
                    object_id = elements[0];
                }
            }
        }
        return object_id;
    }
    
    // get the the value from one of two keys
    public static String valueFromValidKey(Map data, String key1, String key2) {
        if (data != null) {
            if (data.get(key1) != null) {
                return (String)data.get(key1);
            }
            if (data.get(key2) != null) {
                return (String)data.get(key2);
            }
        }
        return null;
    }
    
    // Gather the JVM memory stats
    public static Map gatherMemoryStatistics(ErrorLogger logger) {
        HashMap<String,Object> map = new HashMap<>();
        long zero = 0;
        map.put("total",(Long)zero);
        map.put("free",(Long)zero);
        map.put("used",(Long)zero);
        map.put("max",(Long)zero);
        map.put("processors",(Integer)0);
        try {
            Runtime rt = Runtime.getRuntime();
            map.put("total",(Long)rt.totalMemory()/1024/1024);
            map.put("free",(Long)rt.freeMemory()/1024/1024);
            map.put("used",(Long)(rt.totalMemory() - rt.freeMemory())/1024/1024);
            map.put("max",(Long)rt.maxMemory()/1024/1024);
            map.put("processors",(Integer)rt.availableProcessors());
        }
        catch (Exception ex) {
            logger.warning("gatherMemoryStatistics: Exception occured: " + ex.getMessage());
            
        }
        return map;
    }
    
    // create a random number within the min/max range
    public static int createRandomNumberWithinRange(int min,int max) {
        Random rn = new Random();
        return rn.nextInt(max - min + 1) + min;
    }
    
    // generate the public key from the supplied base64 encoded certificate
    public static String generateBase64EncodedPublicKeyFromCertificate(ErrorLogger logger, String b64_cert) {
        String b64_pub_key = null;
        
        // Base64 decode the Cert
        byte[] cert = Base64.decodeBase64(b64_cert);
        
        try {
            // create an X509Certificate instance
            X509Certificate x509_certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(cert));
        
            // snag the public key
            PublicKey pub_key = x509_certificate.getPublicKey();
            
            // Convert to PEM format
            String pubkey = "-----BEGIN PUBLIC KEY-----" +  
                          Base64.encodeBase64String(pub_key.getEncoded()) + 
                          "-----END PUBLIC KEY-----";
            
            // Base64 encode
            b64_pub_key = Base64.encodeBase64String(pubkey.getBytes());
            
        }
        catch (CertificateException ex) {
            // Exception caught
            logger.warning("generateBase64EncodedPublicKeyFromCertificate: Exception during parse of X509 Certificate: " + b64_cert);
        }
        
        // return the base64 encoded public key
        return b64_pub_key;
    }
    
    // scale back GC() operations
    public static boolean doGC() {
        long val = (long)Math.random();
        if (val%17 == 0) {
            return true;
        }
        return false;
    }
}