
/**
 * @file    Main.java
 * @brief main entry for the connector bridge
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

import com.arm.pelion.bridge.preferences.PreferenceManager;
import com.arm.pelion.bridge.servlet.EventsProcessor;
import com.arm.pelion.bridge.servlet.Manager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;

/**
 * BridgeMain: Main entry point for the pelion-bridge application
 *
 * @author Doug Anson
 */
public class BridgeMain {
    // Defaults
    private static int DEF_CORE_POOL_SIZE = 1000;
    private static int DEF_MAX_POOL_SIZE = 1000000;
    private static int DEF_KEEP_ALIVE = 60;
    
    // Bridge Components
    private ErrorLogger m_logger = null;
    private PreferenceManager m_preferences = null;
    private EventsProcessor m_events_processor = null;
    private Manager m_manager = null;
    
    // Jetty Server
    private Server m_server = null;
    
    // constructor
    public BridgeMain(String[] args) {
        // Error Logger
        this.m_logger = new ErrorLogger();
        
        // Preferences Manager
        this.m_preferences = new PreferenceManager(this.m_logger,Manager.LOG_TAG);

        // configure the error logging level
        this.m_logger.configureLoggingLevel(this.m_preferences);
        
        // Create the Eventing Processor
        this.m_events_processor = new EventsProcessor(this.m_logger,this.m_preferences);
        
        // Initialize the listeners within the Manager of the Eventing Processor
        this.m_manager = this.m_events_processor.manager();
        
        // initialize the server
        this.m_server = new Server(this.m_preferences.intValueOf("mds_gw_port"));
        
        // get the thread pooling configuration
        int core_pool_size = this.m_preferences.intValueOf("threads_core_pool_size");
        if (core_pool_size <= 0) {
            core_pool_size = DEF_CORE_POOL_SIZE;
        }
        int max_pool_size = this.m_preferences.intValueOf("threads_max_pool_size");
        if (max_pool_size <= 0) {
            max_pool_size = DEF_MAX_POOL_SIZE;
        }
        int keep_alive_time = this.m_preferences.intValueOf("threads_keep_alive_time");
        if (keep_alive_time <= 0) {
            keep_alive_time = DEF_KEEP_ALIVE;
        }

        // create the SSL context and establish the handler for the context
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath(this.m_preferences.valueOf("mds_gw_context_path"));
        this.m_server.setHandler(context);

        // Enable SSL Support
        SslSocketConnector sslConnector = new SslSocketConnector();
        sslConnector.setPort(this.m_preferences.intValueOf("mds_gw_port") + 1);
        sslConnector.setHost("0.0.0.0");
        sslConnector.setKeystore("keystore.jks");
        sslConnector.setPassword(this.m_preferences.valueOf("mds_gw_keystore_password"));
        this.m_server.addConnector(sslConnector);
        
        // set the max threads in our thread pool
        this.m_server.setThreadPool(new ExecutorThreadPool(core_pool_size, max_pool_size, keep_alive_time, TimeUnit.SECONDS));
        
        // DEBUG for the Threading Pool Config
        System.out.println(Manager.LOG_TAG + ": Thread Executor Pool: corePool: " + core_pool_size + " maxPool: " + max_pool_size + " keepalive (sec): " + keep_alive_time);

        // eventing process servlet bindings (wildcarded)
        context.addServlet(new ServletHolder(this.m_events_processor), this.m_preferences.valueOf("mds_gw_events_path") + "/*");

        // add a shutdown hook for graceful shutdowns...
        Runtime.getRuntime().addShutdownHook(
            new Thread() {
                @Override
                public void run() {
                    System.out.println(Manager.LOG_TAG + ": Resetting notification handlers...");
                    m_manager.resetNotifications();

                    System.out.println(Manager.LOG_TAG + ": Stopping Listeners...");
                    m_manager.stopListeners();
                }
            }
        );
    }
    
    // start the bridge
    public void start() {
        try {
            // DEBUG
            this.errorLogger().warning("Main: Starting Bridge instance...");
            
            // initialize the listeners
            this.m_manager.initListeners();

            // start me!
            this.m_server.start();

            // Direct the manager to establish the webhooks to Connector/mDS/Cloud
            this.m_manager.initWebhooks();

            // Join me!
            this.m_server.join();
        }
        catch (Exception ex) {
            this.errorLogger().critical("Main: EXCEPTION during bridge start(): " + ex.getMessage(),ex);
        }
    }
    
    // stop the bridge
    public void stop() {
        try {
            // stop the current service
            this.errorLogger().warning("Main: Stopping current bridge service...");
            this.m_server.stop();
            
            // current bridge server stoped
            this.errorLogger().warning("Main: Bridge service has been stopped");
        }
        catch (Exception ex) {
            // ERROR
            this.errorLogger().critical("Main: EXCEPTION during bridge service stop: " + ex.getMessage());
        }
    }
    
    // restart
    public void restart() {
        try {
            // restarting the bridge
            this.errorLogger().warning("Main: restarting bridge...");
            Class<?> main = Class.forName("Main");
            if (main != null) {
                Method restart_method = main.getMethod("restart");
                if (restart_method != null) {
                    // invoke the restart() method
                    this.errorLogger().warning("Main: calling restart() to restart the bridge...");
                    restart_method.invoke(main.newInstance());
                }
                else {
                    // no restart method found
                    this.errorLogger().critical("Main: UNABLE to restart bridge (no restart() method found)");
                }
            }
            else {
                // no Main class found
                this.errorLogger().critical("Main: UNABLE to restart bridge (no Main class found)");
            }
        }
        catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException | SecurityException | InvocationTargetException ex) {
            this.errorLogger().critical("Main: EXCEPTION in restart(): " + ex.getMessage(),ex);
        }
    }
    
    // error logger
    private ErrorLogger errorLogger() {
        return this.m_logger;
    }
}
