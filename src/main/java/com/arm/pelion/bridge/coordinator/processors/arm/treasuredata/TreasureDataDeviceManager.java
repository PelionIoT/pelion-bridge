/**
 * @file TreasureDataDeviceManager.java
 * @brief TreasureData Device Manager
 * @author Doug Anson
 * @version 1.0
 * @see
 *
 * Copyright 2020. ARM Ltd. All rights reserved.
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
package com.arm.pelion.bridge.coordinator.processors.arm.treasuredata;

import com.arm.pelion.bridge.coordinator.processors.core.DeviceManager;
import java.util.Map;
import com.treasuredata.client.*;
import com.treasure_data.logger.*;

/**
 * TreasureData Device Manager
 * @author Doug Anson
 */
public class TreasureDataDeviceManager extends DeviceManager {
    private TreasureDataProcessor m_processor = null;
    
    // TreasureData API
    private TDClient m_td_api = null;
    private TreasureDataLogger m_td_logger = null;
    
    // constructor
    public TreasureDataDeviceManager(TreasureDataProcessor processor,TDClient td_api, TreasureDataLogger td_logger,String suffix) {
        super(processor.orchestrator(),null,suffix);
        this.m_processor = processor;
        this.m_td_api = td_api;
        this.m_td_logger = td_logger;
    }
           
    // create the TreasureData device twin
    public boolean createDevice(Map device,String prefix) {
        String ep = (String)device.get("ep");
        String ept = (String)device.get("ept");
        this.m_processor.setEndpointTypeForEndpointName((String)device.get("ep"), (String)device.get("ept"));
        return true;
    }    
}