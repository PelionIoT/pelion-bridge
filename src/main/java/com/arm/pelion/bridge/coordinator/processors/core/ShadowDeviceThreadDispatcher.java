/**
 * @file ShadowDeviceThreadDispatcher.java
 * @brief ShadowDeviceThread Dispatcher Thread
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
package com.arm.pelion.bridge.coordinator.processors.core;

import com.arm.pelion.bridge.coordinator.processors.arm.PelionProcessor;
import com.arm.pelion.bridge.core.BaseClass;
import com.arm.pelion.bridge.core.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Threaded dispatching of creating a device's shadow
 * @author Doug Anson
 */
public class ShadowDeviceThreadDispatcher extends BaseClass implements Runnable {
    // Throttling Tunables
    private static final int WAIT_ON_DISPATCH_GROUP_MS=250;                 // 1/4 second (time between thread completion checks per dispatch group)
    private static final int WAIT_DISPATCH_GROUP_LAUNCH_PAUSE_MS=100;       // 1/10 second (time between launching threads in a dispatch group)
    
    private PelionProcessor m_pelion_processor = null;
    private int m_mds_max_shadow_create_threads = 0;
    private int m_wait_on_dispatch_group_ms = WAIT_ON_DISPATCH_GROUP_MS;
    private int m_wait_dispatch_group_launch_pause_ms = WAIT_DISPATCH_GROUP_LAUNCH_PAUSE_MS;
    
    // constructor
    public ShadowDeviceThreadDispatcher(PelionProcessor pelion_processor,int max_shadow_create_threads) {
        super(pelion_processor.errorLogger(),pelion_processor.preferences());
        this.m_pelion_processor = pelion_processor;
        this.m_mds_max_shadow_create_threads = max_shadow_create_threads;
    }
    
    // run the thread to dispatch the device shadow threads
    @Override
    public void run() {
        if (this.m_pelion_processor != null) {
            // DEBUG
            this.errorLogger().warning("ShadowDeviceThreadDispatcher: Quering Pelion for existing devices in your organization...");

            // query mbed Cloud for the current list of Registered devices
            List devices = this.m_pelion_processor.discoverRegisteredDevices();
            
            // DEBUG
            if (devices != null) {
                this.errorLogger().warning("ShadowDeviceThreadDispatcher: Pelion indicates " + devices.size() + " devices in your organization.");
            }
            else {
                this.errorLogger().warning("ShadowDeviceThreadDispatcher: Pelion indicates 0 devices in your organization.");
            }
        
            // see what we got in response...
            if (devices.size() > 0 && devices.size() < this.m_mds_max_shadow_create_threads) {
                // DEBUG
                this.errorLogger().warning("ShadowDeviceThreadDispatcher: Dispatching single group (" + devices.size() +  ") of threads for device shadow creation...");

                // dispatch a single group
                List<Thread> list = this.beginDispatchGroup(devices);
                
                // wait on the dispatch group to complete
                this.waitOnDispatchGroup(list);
            }
            else if (devices.size() > 0) {
                // Chop the list up into a list of lists
                List<List<Map>> chopped_list = Utils.chopList(devices,this.m_mds_max_shadow_create_threads);
                for(int i=0;chopped_list != null && i<chopped_list.size();++i) {
                    // Get the ith list of devices...
                    List device_list_i = chopped_list.get(i);

                    // DEBUG
                    this.errorLogger().warning("ShadowDeviceThreadDispatcher: Dispatching group (" + (i+1) + " of " + chopped_list.size() +  ") of threads for device shadow creation...");

                    // now invoke the dispatch of the ith group of devices
                    List<Thread> list = this.beginDispatchGroup(device_list_i);

                    // wait on the dispatch group to complete
                    this.waitOnDispatchGroup(list);
                }
            }
        }
        else {
            // ERROR - invalid params to constructor
            this.errorLogger().warning("ShadowDeviceThreadDispatcher: NULL Pelion processor. Unable to create shadow dispatcher");
        }
        
        // DEBUG
        this.errorLogger().warning("ShadowDeviceThreadDispatcher: Existing device discovery is COMPLETE");
    }
    
    // dispatch of device shadow setup in a single group
    private List<Thread> beginDispatchGroup(List devices) {
        ArrayList<Thread> thread_list = new ArrayList<>();
        // loop through each device, dispatch a device shadow setup for it
        for(int i=0;devices != null && i<devices.size();++i) {
            try {
                // get the ith device
                Map device = (Map)devices.get(i);
                
                // DEBUG
                this.errorLogger().warning("ShadowDeviceThreadDispatcher: Shadow Device task starting for deviceID: " + (String)device.get("id"));
                
                // create a thread and dispatch it to create the device shadow...
                Thread dispatch = new Thread(new CreateShadowDeviceThread(this.m_pelion_processor,device));
                dispatch.start();
                thread_list.add(dispatch);
                
                // Throttle a bit
                Utils.waitForABit(this.errorLogger(), this.m_wait_dispatch_group_launch_pause_ms);
            }
            catch (Exception ex) {
                // ERROR
                this.errorLogger().warning("ShadowDeviceThreadDispatcher: Exception during device shadow create dispatch: " + ex.getMessage());
            }
        }
        return thread_list;
    }
    
    // wait until all threads of a given dispatch group have completed
    private void waitOnDispatchGroup(List<Thread> list) {
        boolean finished = false;
        if (list != null && list.size() > 0) {
            while(!finished) {
                int count = list.size();
                for(int i=0;i<list.size() && !finished;++i) {
                    if (list.get(i).getState() == Thread.State.TERMINATED) {
                        --count;
                    }
                    Utils.waitForABit(this.errorLogger(), this.m_wait_on_dispatch_group_ms); 
                }
                if (count <= 0) {
                    finished = true;
                }
            }
        }
    }
}