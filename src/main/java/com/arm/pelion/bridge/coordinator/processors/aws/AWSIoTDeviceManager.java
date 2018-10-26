/**
 * @file    AWSIoTDeviceManager.java
 * @brief   AWSIoT Device Manager for the AWSIoT Peer Processor
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
package com.arm.pelion.bridge.coordinator.processors.aws;

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.core.DeviceManager;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.data.SerializableHashMap;
import com.arm.pelion.bridge.transport.HttpTransport;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class defines the required REST functions to manage AWSIoT devices via the peer processor
 *
 * @author Doug Anson
 */
public class AWSIoTDeviceManager extends DeviceManager {    
    private ArrayList<String> m_keys_cert_ids = null;

    // XXX make configurable
    private String m_policy_name = null;
    private String m_policy_document = null;
    
    private AWSIoTMQTTProcessor m_processor = null;
    
    // lock to reconnection serial
    private boolean m_in_progress = false;
    
    // constructor
    public AWSIoTDeviceManager(HttpTransport http, AWSIoTMQTTProcessor processor) {
        this(null, http, processor);
    }

    // constructor
    public AWSIoTDeviceManager(String suffix, HttpTransport http, AWSIoTMQTTProcessor processor) {
        super(processor.errorLogger(), processor.preferences(),suffix,http,processor.orchestrator());
        this.m_processor = processor;

        // initialize the keys/cert id cache
        this.m_keys_cert_ids = new ArrayList<>();
        
        // not in progress by default
        this.m_in_progress = false;

        // get configuration params
        this.m_policy_name = this.orchestrator().preferences().valueOf("aws_iot_policy_name", this.m_suffix);
        this.m_policy_document = this.orchestrator().preferences().valueOf("aws_iot_policy_document", this.m_suffix);
        
    }

    // get the orchestrator
    private Orchestrator orchestrator() {
        return this.m_orchestrator;
    }

    // get our endpoint details
    public HashMap<String, Serializable> getEndpointDetails(String ep_name) {
        return this.m_endpoint_details.get(ep_name);
    }

    // process new device registration
    public synchronized boolean registerNewDevice(Map message) {
        boolean status = false;
        
        // DEBUG
        //this.errorLogger().ping("registerNewDevice(AWSIoT)");
        
        if (this.m_in_progress == false) {
            // we are now in progress
            this.m_in_progress = true;

            // get the device details
            String device_type = Utils.valueFromValidKey(message, "endpoint_type", "ept");
            String device = Utils.valueFromValidKey(message, "id", "ep");

            // see if we already have a device...
            HashMap<String, Serializable> ep = this.getDeviceDetails(device);
            if (ep != null) {
                // we already have this shadow. Complete the details... this will remove the existing certs/keys...
                this.completeDeviceDetails(ep);
                if (this.hasCompleteDeviceDetails(ep) == false) {
                    // we now create new certificate and keys... we need them for our MQTT connection...
                    this.createKeysAndCertsAndLinkToPolicyAndThing(ep);

                    // save off this device 
                    this.saveDeviceDetails(device, ep);
                }

                // DEBUG
                this.errorLogger().info("AWSIoT: registerNewDevice: device shadow already present (OK): " + message);

                // we are good
                status = true;
            }
            else {
                // DEBUG
                this.errorLogger().info("AWSIoT: registerNewDevice: no device shadow found. Creating device shadow: " + message);

                // device is not registered... so create/register it
                status = this.createAndRegisterNewDevice(message);
            }
            
            // add the device type to the map if we are OK...
            if (status == true) {
                // add the device type
                this.m_processor.setEndpointTypeFromEndpointName(device, device_type);
            }
            
            // no longer in progress
            this.m_in_progress = false;
        }
        else {
            // already in progress
            status = true;
        }

        // return our status
        return status;
    }

    // create and register a new device
    private boolean createAndRegisterNewDevice(Map message) {
        // create the new device type
        String device_type = Utils.valueFromValidKey(message, "endpoint_type", "ept");
        String device = Utils.valueFromValidKey(message, "id", "ep");
        
        // create the thing type first
        this.createThingType(message);

        // invoke AWS CLI to create a new device
        String args = "iot create-thing --thing-name=" + device + " --thing-type-name=" + device_type;
        String result = Utils.awsCLI(this.errorLogger(), args);

        // DEBUG
        this.errorLogger().info("AWSIoT: registerNewDevice: New EP: " + device + " EPT: " + device_type);
        this.errorLogger().info("AWSIoT: registerNewDevice: Addition RESULT: " + result);

        // return our status
        Boolean status = (result != null && result.length() > 0);

        // If OK, save the result
        if (status == true) {
            // DEBUG
            this.errorLogger().info("AWSIoT: registerNewDevice: saving off device details...");

            // save off device details...
            this.saveAddDeviceDetails(device, device_type, result);
        }

        // return our status
        return status;
    }

    // unlink the certificate from the Thing Record
    private void unlinkCertificateFromThing(HashMap<String, Serializable> ep) {
        this.unlinkCertificateFromThing((String) ep.get("thingName"), (String) ep.get("certificateArn"));
    }

    // unlink the certificate from the Thing Record
    private void unlinkCertificateFromThing(String ep_name, String arn) {
        if (ep_name != null && ep_name.length() > 0) {
            String ep_qual = "--thing-name=" + ep_name;
            String args = "iot detach-thing-principal " + ep_qual + " --principal=" + arn;
            Utils.awsCLI(this.errorLogger(), args);
        }
        else {
            // until we get a persistent store that can permanently map ep:ARN, we will have leakage on restart of the bridge...
            this.errorLogger().info("AWSIoT: Unable to unlink Cert from Thing... no Endpoint Name provided");
            this.errorLogger().info("AWSIoT: Unlink Failure details EP: " + ep_name + " ARN: " + arn);
        }
    }

    // unlink the certificate from the Policy
    private void unlinkCertificateFromPolicy(HashMap<String, Serializable> ep) {
        this.unlinkCertificateFromPolicy((String) ep.get("certificateArn"));
    }

    // unlink the certificate from the Policy
    private void unlinkCertificateFromPolicy(String arn) {
        String args = "iot detach-principal-policy --policy-name=" + this.m_policy_name + " --principal=" + arn;
        Utils.awsCLI(this.errorLogger(), args);
    }

    // inactivate the Certificate
    private void inactivateCertificate(HashMap<String, Serializable> ep) {
        this.inactivateCertificate((String) ep.get("certificateId"));
    }

    // inactivate the Certificate
    private void inactivateCertificate(String id) {
        String args = "iot update-certificate --certificate-id=" + id + " --new-status=INACTIVE";
        Utils.awsCLI(this.errorLogger(), args);
    }

    // delete the Certificate
    private void deleteCertificate(HashMap<String, Serializable> ep) {
        this.deleteCertificate((String) ep.get("certificateId"));
    }

    // delete the Certificate
    private void deleteCertificate(String id) {
        String args = "iot delete-certificate --certificate-id=" + id;
        Utils.awsCLI(this.errorLogger(), args);
    }

    // get the key and cert index 
    private int getKeyAndCertIndex(String id) {
        int index = -1;

        for (int i = 0; i < this.m_keys_cert_ids.size() && index < 0; ++i) {
            if (id.equalsIgnoreCase(this.m_keys_cert_ids.get(i)) == true) {
                index = i;
            }
        }

        return index;
    }

    // unlink a certificate from the policy and thing record, deactivate it, then delete it
    private void removeCertificate(String device) {
        if (device != null && device.length() > 0) {
            // Get our record 
            HashMap<String, Serializable> ep = this.getEndpointDetails(device);
            if (ep != null) {
                // unlink the certificate from the thing record
                this.unlinkCertificateFromThing(ep);

                // unlink the certificate from the thing record
                this.unlinkCertificateFromPolicy(ep);

                // deactivate the certificate
                this.inactivateCertificate(ep);

                // delete the certificate
                this.deleteCertificate(ep);

                // remove locally as well
                int index = this.getKeyAndCertIndex(device);
                if (index >= 0) {
                    this.m_keys_cert_ids.remove(index);
                }
            }
            else {
                this.errorLogger().warning("AWSIoT: Unable to find device details for: " + device + ". Unable to remove certs for it.");
            }
        }
        else {
            this.errorLogger().info("AWSIoT: removeCertificate: Device parameter is NULL. Ignoring cert deletion.");
        }
    }

    // process device deletion
    public boolean deleteDevice(String device) {
        if (device != null && device.length() > 0) {
            // DEBUG
            this.errorLogger().warning("AWSIoT: deleting device: " + device + "...");

            // first, unlink the certificate and deactivate it/remove it
            this.removeCertificate(device);

            // invoke AWS CLI to delete the current device shadow thing
            String args = "iot delete-thing --thing-name=" + device;
            String result = Utils.awsCLI(this.errorLogger(), args);

            // remove the endpoint details
            this.m_endpoint_details.remove(device);

            // DEBUG
            this.errorLogger().warning("AWSIoT: deleteDevice: device: " + device + " deletion SUCCESS");
            
            // return status
            return true;
        }
        else {
            // ignore the deletion request
            this.errorLogger().info("AWSIoT: deleteDevice: Device parameter is NULL. Ignoring deletion request");
        }
        return false;
    }

    // complete the device details
    private void completeDeviceDetails(HashMap<String, Serializable> ep) {
        // DEBUG
        this.errorLogger().info("AWSIoT: EP: " + ep);
        
        // add the thing type details
        if (ep.get("thingTypeArn") == null) {
            this.createThingType(ep);
        }
        
        // add the endpoint address details
        if (ep.get("endpointAddress") == null) {
            this.captureEndpointAddress(ep);
        }
        
        // get thing details
        if (ep.get("thingId") == null) {
            this.captureThingDetails(ep);
        }

        // add the policy details
        if (ep.get("policyArn") == null) {
            ep.put("policyName", this.m_policy_name);
            this.capturePolicyDetails(ep);
        }

        // get the certificate details (option)
        if (ep.get("certificateId") == null) {
            this.captureCertificateDetails(ep);
        }
    }

    // get a given device's details...
    private HashMap<String, Serializable> getDeviceDetails(String device) {
        HashMap<String, Serializable> ep = this.getEndpointDetails(device);

        // if we dont already have it, go get it... 
        if (ep == null) {
            // invoke AWS CLI to create a new device
            String args = "iot describe-thing --thing-name=" + device;
            String result = Utils.awsCLI(this.errorLogger(), args);

            // DEBUG
            //this.errorLogger().info("getDeviceDetails: RESULT: " + result);
            // parse our result...
            if (result != null && result.length() > 0) {
                ep = this.parseDeviceDetails(device, result);
            }
        }

        // return our endpoint details
        return ep;
    }

    // parse our device details
    private HashMap<String, Serializable> parseDeviceDetails(String device, String json) {
        return this.parseDeviceDetails(device, "", json);
    }

    private HashMap<String, Serializable> parseDeviceDetails(String device, String device_type, String json) {
        SerializableHashMap ep = null;

        // check the input json
        if (json != null) {
            try {
                if (json.contains("ResourceNotFoundException") == false) {
                    // Parse the JSON...
                    Map parsed = this.orchestrator().getJSONParser().parseJson(json);
                    if (parsed != null) {
                        // Device Details
                        String d = this.orchestrator().getTablenameDelimiter();
                        ep = new SerializableHashMap(this.orchestrator(),"AWS_DEVICE" + d + device + d + device_type);

                        // Device Name
                        ep.put("thingName", (String) parsed.get("thingName"));
                        ep.put("defaultClientId", (String) parsed.get("defaultClientId"));
                        ep.put("attributes", (String) parsed.get("thingName"));
                        ep.put("thingArn",(String) parsed.get("thingArn"));
                        ep.put("thingId", (String) parsed.get("thingId"));
                        ep.put("thingTypeName", (String) parsed.get("thingTypeName"));
                        ep.put("ep_name", device);
                        ep.put("ep_type", device_type);

                        // record the entire record for later...
                        if (json != null) {
                            ep.put("json_record", json);
                        }

                        // DEBUG
                        this.errorLogger().info("AWSIoT: parseDeviceDetails for " + device + ": " + ep);
                    }
                    else {
                        // unable to parse
                        this.errorLogger().warning("AWSIoT: parseDeviceDetails: ERROR Unable to parse device details!");
                    }
                }
                else {
                    // device is not found
                    this.errorLogger().info("AWSIoT: parseDeviceDetails: device " + device + " is not a registered device (OK)");
                    ep = null;
                }
            }
            catch (Exception ex) {
                // exception in parsing... so nullify...
                this.errorLogger().warning("AWSIoT: parseDeviceDetails: exception while parsing device " + device + " JSON: " + json, ex);
                if (ep != null) {
                    this.errorLogger().warning("AWSIoT: parseDeviceDetails: last known ep contents: " + ep);
                }
                else {
                    this.errorLogger().warning("AWSIoT: parseDeviceDetails: last known ep contents: EMPTY");
                }
                ep = null;
            }
        }
        else {
            this.errorLogger().warning("AWSIoT: parseDeviceDetails: input JSON is EMPTY");
            ep = null;
        }

        // return our endpoint details
        if (ep != null) {
            return ep.map();
        }
        
        // returning empty map
        this.errorLogger().warning("AWSIoT: parseDeviceDetails: returning empty map!"); 
        return null;
    }

    // generate keys and certs
    private void createKeysAndCerts(HashMap<String, Serializable> ep) {
        // DEBUG
        //this.errorLogger().ping("createKeysAndCerts(AWS)");
        
        // AWS IOT CLI to create the keys and certificates
        String args = "iot create-keys-and-certificate --set-as-active";
        String result = Utils.awsCLI(this.errorLogger(), args);

        // DEBUG
        //this.errorLogger().info("createKeysAndCerts: RESULT: " + result);
        // parse if we have something to parse
        if (result != null && result.length() > 2) {
            // parse it
            Map parsed = this.m_orchestrator.getJSONParser().parseJson(result);

            // put the key items into our map
            ep.put("certificateArn", (String) parsed.get("certificateArn"));
            ep.put("certificatePem", (String) parsed.get("certificatePem"));
            ep.put("certificateId", (String) parsed.get("certificateId"));

            // Keys are also available
            Map keys = (Map) parsed.get("keyPair");

            // install the keys
            ep.put("PrivateKey", (String) keys.get("PrivateKey"));
            ep.put("PublicKey", (String) keys.get("PublicKey"));

            // save off to the key store
            this.m_keys_cert_ids.add((String) parsed.get("certificateId"));
        }

        // DEBUG
        //this.errorLogger().info("createKeysAndCerts: EP: " + ep);
    }
    
    // has complete device details
    private boolean hasCompleteDeviceDetails(HashMap<String, Serializable> ep) {        
        if (ep != null) {
            if (ep.get("PrivateKey") != null) {
                // DEBUG
                this.errorLogger().info("AWSIoT: Device details already complete: " + ep);
                
                // complete!
                return true;
            }
        }
        
        return false;
    }

    // get our current defaulted policy
    private String getDefaultPolicy() {
        // AWS CLI invocation...
        String args = "iot get-policy --policy-name=" + this.m_policy_name;
        return Utils.awsCLI(this.errorLogger(), args);
    }

    // save off the default policy
    private void saveDefaultPolicy(HashMap<String, Serializable> ep, String json) {
        Map parsed = this.m_orchestrator.getJSONParser().parseJson(json);
        ep.put("policyName", (String) parsed.get("policyName"));
        ep.put("policyArn", (String) parsed.get("policyArn"));
        ep.put("policyVersionId", (String) parsed.get("policyVersionId"));
        ep.put("policyDocument", (String) parsed.get("policyDocument"));
    }

    // create and save the defaulted policy
    private void checkAndCreateDefaultPolicy(HashMap<String, Serializable> ep) {
        // Our default policy
        String policy_json = this.getDefaultPolicy();

        // if we dont have it, create it...
        if (policy_json == null || policy_json.length() == 0) {
            // AWS CLI invocation...
            String args = "iot create-policy --policy-name=" + this.m_policy_name + " --policy-document=" + this.m_policy_document;
            Utils.awsCLI(this.errorLogger(), args);
            policy_json = this.getDefaultPolicy();
        }

        // save off
        this.saveDefaultPolicy(ep, policy_json);
    }

    // link the certificate to the thing record and the default policy
    private void linkCertificateToThingAndPolicy(HashMap<String, Serializable> ep) {
        // AWS CLI invocation - link policy to certficate ARN
        String args = "iot attach-principal-policy --policy-name=" + this.m_policy_name + " --principal=" + (String) ep.get("certificateArn");
        Utils.awsCLI(this.errorLogger(), args);

        // AWS CLI invocation - link thing record to certificate
        args = "iot attach-thing-principal --thing-name=" + (String) ep.get("thingName") + " --principal=" + (String) ep.get("certificateArn");
        Utils.awsCLI(this.errorLogger(), args);
    }
    
    // check ifa thing type already exists
    private boolean thingTypeExists(Map ep) {
        if (ep != null) {
            String args = "iot describe-thing-type --thing-type-name=" + (String) ep.get("ept");
            String result = Utils.awsCLI(this.errorLogger(), args);
            if (result != null && result.length() > 2) {
                // parse it
                Map parsed = this.m_orchestrator.getJSONParser().parseJson(result);

                // resync - put the thing type ARN back into the ep
                ep.put("thingTypeArn", (String) parsed.get("thingTypeArn"));
                return true;
            }
        }
        return false;
    }
    
    // create the thing type
    private void createThingType(Map ep) {
        if (ep != null && this.thingTypeExists(ep) == false) {
            // create the Thing Type...
            String args = "iot create-thing-type --thing-type-name=" + (String) ep.get("ept");
            String result = Utils.awsCLI(this.errorLogger(), args);
            if (result != null && result.length() > 2) {
                // parse it
                Map parsed = this.m_orchestrator.getJSONParser().parseJson(result);

                // put the thing type ARN into the ep
                ep.put("thingTypeArn", (String) parsed.get("thingTypeArn"));
            }
        }
    }

    // capture the endpoint address
    private void captureEndpointAddress(HashMap<String, Serializable> ep) {
        // AWS CLI invocation - get endpoint details
        String args = "iot describe-endpoint";
        String json = Utils.awsCLI(this.errorLogger(), args);
        if (json != null && json.length() > 0) {
            Map parsed = this.m_orchestrator.getJSONParser().parseJson(json);
            ep.put("endpointAddress", (String) parsed.get("endpointAddress"));
        }
    }
    
    // capture the thing details
    private void captureThingDetails(HashMap<String, Serializable> ep) {
        // AWS CLI invocation - get thing details
        String args = "iot describe-thing --thing-name=" + (String)ep.get("thingName");
        String json = Utils.awsCLI(this.errorLogger(), args);
        if (json != null && json.length() > 0) {
            Map parsed = this.m_orchestrator.getJSONParser().parseJson(json);
            ep.put("defaultClientId", (String) parsed.get("defaultClientId"));
        }
    }
    
    // capture the thing's certificate details
    private void captureCertificateDetails(HashMap<String, Serializable> ep) {
        ArrayList<String> doomed_arn_list = new ArrayList<>();
        
        // AWS CLI invocation - get thing's certificate details... we are going to delete EVERYTHING...
        String args = "iot list-thing-principals --thing-name=" + (String)ep.get("thingName");
        String json = Utils.awsCLI(this.errorLogger(), args);
        if (json != null && json.length() > 0) {
            Map parsed = this.m_orchestrator.getJSONParser().parseJson(json);
            List arns = (List)parsed.get("principals");
            for (int i=0;arns != null && i<arns.size();++i) {
                // DEBUG
                this.errorLogger().info("AWSIoT: doomed ARN: " + (String)arns.get(i));

                // put on the doomed list...
                doomed_arn_list.add((String)arns.get(i));
            }
            
            // for the doomed list, we have to iterate through all of the certs and get the Arns...
            for(int i=0;i<doomed_arn_list.size();++i) {
                String cert_arn = doomed_arn_list.get(i);
                String cert_id = Utils.pullCertificateIdFromAWSPrincipal(cert_arn);

                // DEBUG
                this.errorLogger().info("AWSIoT: unlinking: " + cert_arn);

                // unlink the certificate from the thing record
                this.unlinkCertificateFromThing((String)ep.get("thingName"), cert_arn);

                // unlink the certificate from the thing record
                this.unlinkCertificateFromPolicy(cert_arn);

                // DEBUG
                this.errorLogger().info("AWSIoT: inactivating: " + cert_id);
                
                // inactivate
                this.inactivateCertificate(cert_id);
                
                // DEBUG
                this.errorLogger().info("AWSIoT: deleting: " + cert_id);

                // delete
                this.deleteCertificate(cert_id);

                // purge the certificate from the cache
                try {
                    this.m_keys_cert_ids.remove(this.getKeyAndCertIndex(cert_id));
                }
                catch (Exception ex) {
                    // fail silently...
                }
            }
        }
    }
   
    // capture the policy details
    private void capturePolicyDetails(HashMap<String, Serializable> ep) {
        String json = this.getDefaultPolicy();
        if (json != null && json.length() > 0) {
            this.saveDefaultPolicy(ep, json);
        }
    }

    // create keys and certs and link to the device 
    private void createKeysAndCertsAndLinkToPolicyAndThing(HashMap<String, Serializable> ep) {
        // add the certificates and keys
        this.createKeysAndCerts(ep);

        // link the thing record and default policy to the certificate
        this.linkCertificateToThingAndPolicy(ep);
    }

    // Parse the AddDevice result and capture key elements 
    private void saveAddDeviceDetails(String device, String device_type, String json) {
        // parse our device details into structure
        HashMap<String, Serializable> ep = this.parseDeviceDetails(device, device_type, json);
        if (ep != null) {
            // create the default policy
            this.checkAndCreateDefaultPolicy(ep);

            // create the keys/certs and link it to the default policy and device
            this.createKeysAndCertsAndLinkToPolicyAndThing(ep);

            // capture the endpoint address
            this.captureEndpointAddress(ep);
            
            // save off the details
            this.saveDeviceDetails(device, ep);
        }
        else {
            // unable to parse details
            this.errorLogger().warning("AWSIoT: saveAddDeviceDetails: ERROR: unable to parse device " + device + " details JSON: " + json);
        }
    }

    // save device details
    private void saveDeviceDetails(String device, HashMap<String, Serializable> entry) {
        // don't overwrite an existing entry..
        if (this.getEndpointDetails(device) == null) {
            // save off the endpoint details
            this.m_endpoint_details.put(device, entry);
        }
    }

    // is this cert_id used by one of the existing devices?
    private boolean isUsedCert(String id) {
        boolean found = false;
        for (int i = 0; i < this.m_keys_cert_ids.size() && !found && id != null && id.length() > 0; ++i) {
            if (id.equalsIgnoreCase(this.m_keys_cert_ids.get(i))) {
                found = true;
            }
        }
        return found;
    }
    
    // look and see if certificates are present
    private boolean certificatesFound(String json) {
        boolean found = false;
        
        // crappy JSON parser cannot deal with []... so we have to look for it specifically...
        if (json != null && json.length() > 0 && json.contains("\"certificates\": []") == false) {
            found = true;
        }
        
        return found;
    }

    // get our list of certificates
    private ArrayList<HashMap<String, Serializable>> getRegisteredCertificates() {
        ArrayList<HashMap<String, Serializable>> list = new ArrayList<>();

        // AWS IoT CLI
        String args = "iot list-certificates";
        String json = Utils.awsCLI(this.errorLogger(), args);

        // parse and process the result
        if (this.certificatesFound(json) == true) {
            Map parsed = this.m_orchestrator.getJSONParser().parseJson(json);
            List cert_list = (List) parsed.get("certificates");
            for (int i = 0; cert_list != null && i < cert_list.size(); ++i) {
                Map entry = (Map) cert_list.get(i);
                HashMap<String, Serializable> cert = new HashMap<>();
                cert.put("certificateId", (String) entry.get("certificateId"));
                cert.put("certificateArn", (String) entry.get("certificateArn"));
                list.add(cert);
            }

            // DEBUG
            this.errorLogger().info("AWSIoT: getRegisteredCertificates(" + list.size() + "): " + list);
        }
        else {
            // no certificates found
            this.errorLogger().info("AWSIoT: getRegisteredCertificates: no certificates found (OK)");
        }
        
        // return the list
        return list;
    }
}
