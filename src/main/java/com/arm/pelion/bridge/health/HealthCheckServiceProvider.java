/**
 * @file HealthCheckServiceProvider.java
 * @brief Health check service provider implementation for Pelion bridge
 * @author Doug Anson
 * @version 1.0
 * @see
 *
 * Copyright 2018. ARM Ltd. All rights reserved.
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
package com.arm.pelion.bridge.health;

import com.arm.pelion.bridge.coordinator.Orchestrator;
import com.arm.pelion.bridge.coordinator.processors.arm.PelionProcessor;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PeerProcessorInterface;
import com.arm.pelion.bridge.coordinator.processors.interfaces.PelionProcessorInterface;
import com.arm.pelion.bridge.core.BaseClass;
import com.arm.pelion.bridge.core.Utils;
import com.arm.pelion.bridge.health.interfaces.HealthCheckServiceInterface;
import com.arm.pelion.bridge.health.interfaces.HealthStatisticListenerInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Health Check Service Provider Instance
 * @author Doug Anson
 */
public class HealthCheckServiceProvider extends BaseClass implements HealthCheckServiceInterface, Runnable {
    private Orchestrator m_orchestrator = null;
    private ArrayList<HealthStatisticListenerInterface> m_listeners = null;
    private HashMap<String,HealthStatistic> m_statistics = null;
    private boolean m_running = false;
    private int m_health_status_update_ms = 0;
    private ArrayList<BaseValidatorClass> m_validator_list = null;
    
    // primary constructor
    public HealthCheckServiceProvider(Orchestrator orchestrator,int health_status_update_ms) {
        super(orchestrator.errorLogger(),orchestrator.preferences());
        this.m_orchestrator = orchestrator;
        this.m_statistics = new HashMap<>();
        this.m_validator_list = new ArrayList<>();
        this.m_listeners = new ArrayList<>();
        this.m_health_status_update_ms = health_status_update_ms;
    }
    
    // add a listener
    public void addListener(HealthStatisticListenerInterface listener) {
        if (listener != null) {
            this.m_listeners.add(listener);
        }
    }

    // update a given health statistic
    @Override
    public void updateHealthStatistic(HealthStatistic statistic) {
        this.m_statistics.put(statistic.name(),statistic);
    }
    
    // get the orchestrator
    @Override
    public Orchestrator getOrchestrator() {
        return this.m_orchestrator;
    }

    // get the pelion processor
    @Override
    public PelionProcessorInterface getPelionProcessor() {
        return (PelionProcessorInterface)this.m_orchestrator.pelion_processor();
    }

    // get the peer processor list
    @Override
    public List<PeerProcessorInterface> getPeerProcessorList() {
        return this.m_orchestrator.peer_processor_list();
    }

    // initialize our stats
    @Override
    public void initialize() {
        // determine whether we are using long polling or webhooks...
        PelionProcessor p = (PelionProcessor)this.getPelionProcessor();
        if (p.webHookEnabled()) {
            // webhooks in use
            this.m_validator_list.add(new WebhookValidator(this));
        }
        else {
            // long polling in use
            this.m_validator_list.add(new LongPollValidator(this));
        }
        
        // MQTT connection validator
        this.m_validator_list.add(new MQTTConnectionValidator(this));
        
        // Database validator
        this.m_validator_list.add(new DatabaseValidator(this));
        
        // Shadow Count Statistic
        this.m_validator_list.add(new ShadowCountStatistic(this));
        
        // ADD other validators here...
        
        // Run all..
        for(int i=0;i<this.m_validator_list.size();++i) {
            Thread t = new Thread(this.m_validator_list.get(i));
            t.start();
        }
    }
    
    // create a JSON output of the stats
    @Override
    public String statisticsJSON() {
        return this.m_orchestrator.getJSONGenerator().generateJson(this.createStatisticsJSON());
    }
    
    // create a JSON output of the stat descriptons 
    @Override
    public String descriptionsJSON() {
        return this.m_orchestrator.getJSONGenerator().generateJson(this.createDescriptonJSON());
    }
    
    // get the current time (formatted) 
    private String getCurrentFormattedTime() {
        return new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
    }
    
    // create a map of key,value pairs 
    private Map createStatisticsJSON() {
        HashMap<String,Object> stats = new HashMap<>();
        for (Map.Entry<String, HealthStatistic> entry : this.m_statistics.entrySet()) {
            String name = entry.getKey();
            HealthStatistic statistic = entry.getValue();
            stats.put(name,statistic.value());
        }
        
        // add a timestamp
        stats.put("timestamp",(String)this.getCurrentFormattedTime());
        
        // return the status
        return (Map)stats;
    }
    
    // create a map of key,description pairs 
    private Map createDescriptonJSON() {
        HashMap<String,String> descriptions = new HashMap<>();
        for (Map.Entry<String, HealthStatistic> entry : this.m_statistics.entrySet()) {
            String name = entry.getKey();
            HealthStatistic statistic = entry.getValue();
            descriptions.put(name,statistic.description());
        }
        return (Map)descriptions;
    }
    
    // check and publish changes to listeners
    private void checkAndPublish() {
        String json = this.statisticsJSON();
        for(int i=0;i<this.m_listeners.size();++i) {
            this.m_listeners.get(i).publish(json);
        }
    }

    // run statistics loop
    @Override
    public void run() {
        this.m_running = true;
        this.healthStatsUpdateLoop();
    }
    
    // halt 
    public void halt() {
        this.m_running = false;
    }
    
    // main health statistics update loop
    private void healthStatsUpdateLoop() {
        while (this.m_running == true) {
            // validate the webhook
            this.checkAndPublish();

            // sleep for a bit...
            Utils.waitForABit(this.errorLogger(),this.m_health_status_update_ms);
        }
    }
}
