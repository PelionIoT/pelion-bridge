/**
 * @file DeviceAttributeRetrievalDispatchManager.java
 * @brief Device Attribute Retrieval Dispatch Manager the Pelion bridge
 * @author Doug Anson
 * @version 1.0
 * @see
 *
 * Copyright 2019. ARM Ltd. All rights reserved.
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
package com.arm.pelion.bridge.coordinator.processors.core;

import com.arm.pelion.bridge.coordinator.processors.arm.PelionProcessor;
import com.arm.pelion.bridge.core.BaseClass;
import com.arm.pelion.bridge.core.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Device Attribute Retrieval Dispatcher the Pelion bridge
 * @author Doug Anson
 */
public class DeviceAttributeRetrievalDispatchManager extends BaseClass implements Runnable {
    private static final int WAIT_TIME_MS = 5000;       // wait 5 seconds
    
    private PelionProcessor m_processor = null;
    private ArrayList<HashMap<String,Object>> m_retrievers = null;
    private boolean m_running = false;
    private Map m_endpoint = null;
    
    // default constructor
    public DeviceAttributeRetrievalDispatchManager(PelionProcessor processor,Map endpoint,String uri_list[]) {
        super(processor.errorLogger(), processor.preferences());
        this.m_processor = processor;
        this.m_endpoint = endpoint;
        String device_type = Utils.valueFromValidKey(endpoint, "endpoint_type", "ept");
        String device_id = Utils.valueFromValidKey(endpoint, "id", "ep");
        this.m_retrievers = this.initRetrievers(device_id,device_type,uri_list);
    }

    // initialize the retrievers
    private ArrayList<HashMap<String,Object>> initRetrievers(String device_id,String device_type,String uri_list[]) {
        ArrayList<HashMap<String,Object>> list = new ArrayList<>();
        
        // create the retrievers
        if (uri_list != null && uri_list.length > 0) {
            for(int i=0;i<uri_list.length;++i) {
                HashMap<String,Object> entry = new HashMap<>();
                entry.put("uri",uri_list[i]);
                entry.put("device_id",device_id);
                entry.put("device_type",device_type);
                entry.put("value",null);
                entry.put("retriever",new DeviceAttributeRetrievalDispatcher(this,this.m_processor,this.m_endpoint,device_id,device_type,uri_list[i]));
                list.add(entry);
            }
        }
        
        // return the list
        return list;
    }
    
    // update the given attribute for a given device
    public synchronized void updateAttributeForDevice(DeviceAttributeRetrievalDispatcher dispatcher, Object value) {
        // get the entry for our dispatcher
        HashMap<String,Object> entry = this.lookupDispatcher(dispatcher);
        if (entry != null) {
            // record the value...
            entry.put("value",value);
            
            try {
                // collect the dispatcher thread and clean up...
                Thread tr = (Thread)entry.get("thread");
                tr.join();
            }
            catch (InterruptedException ex) {
                // silent
            }
            
            // nullify the thread entry...
            entry.remove("thread");
            
            // check and notify the parent when all threads have completed...
            this.notifyParentWhenCompleted();
        }
    }
    
    // lookup the dispatcher from our list
    private HashMap<String,Object> lookupDispatcher(DeviceAttributeRetrievalDispatcher dispatcher) {
        for (int i=0;dispatcher != null && i<this.m_retrievers.size();++i) {
            HashMap<String,Object> entry = (HashMap<String,Object>)this.m_retrievers.get(i);
            DeviceAttributeRetrievalDispatcher t_dispatcher = (DeviceAttributeRetrievalDispatcher)entry.get("retriever");
            if (t_dispatcher == dispatcher) {
                return entry;
            }
        }
        return null;
    }
    
    // get active thread count...
    private int getActiveThreadCount() {
        int count = 0;
        for(int i=0;this.m_retrievers != null && i<this.m_retrievers.size();++i) {
            HashMap<String,Object> entry = (HashMap<String,Object>)this.m_retrievers.get(i);
            if (entry != null) {
                if (entry.get("thread") != null) {
                    ++count;
                }
            }
        }
        return count;
    }
    
    // notify the parent when all threads have completed
    private void notifyParentWhenCompleted() {
        if (this.getActiveThreadCount() == 0) {
            // we are done running
            this.m_running = false;
            
            // all threads are complete... notify the parent.
            this.m_processor.updateDeviceAttributes(this.m_endpoint,this.m_retrievers);
        }
    }

    @Override
    public void run() {
        // initialize all of the Threads and dispatch them...
        for(int i=0;i<this.m_retrievers.size();++i) {
            try {
                HashMap<String,Object> entry = this.m_retrievers.get(i);
                DeviceAttributeRetrievalDispatcher retriever = (DeviceAttributeRetrievalDispatcher)entry.get("retriever");
                Thread t = new Thread(retriever);
                entry.put("thread", t);
                t.start();
            }
            catch (Exception ex) {
                // exception caught during retriever dispatch
                this.errorLogger().warning("DeviceAttributeRetrievalDispatchManager: Exception while launching retriever: " + ex.getMessage(),ex);
            }
        }
        
        // wait for everything to complete
        while(this.m_running == true) {
            Utils.waitForABit(this.errorLogger(), WAIT_TIME_MS);
        }
        
        // DEBUG
        this.errorLogger().info("DeviceAttributeRetrievalDispatchManager: task exited (OK)");
    }
}
