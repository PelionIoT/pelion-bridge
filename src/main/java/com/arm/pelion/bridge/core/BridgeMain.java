/**
 * @file BridgeMain.java
 * @brief pelion-bridge main method
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

package com.arm.pelion.bridge.core;

import com.arm.pelion.bridge.loggerservlet.LoggerWebSocketServlet;
import com.arm.pelion.bridge.preferences.PreferenceManager;
import com.arm.pelion.bridge.servlet.EventsProcessor;
import com.arm.pelion.bridge.servlet.Manager;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;

/**
 * BridgeMain: Main entry point for the pelion-bridge application
 *
 * @author Doug Anson
 */
public class BridgeMain implements Runnable {
    // Defaults
    private static int DEF_THREAD_COUNT_CHECK_WAIT_MS = 60000;      // 1 minute between thread count checks
    private static int DEF_CORE_POOL_SIZE = 10;                     // default size of pool of threads
    private static int DEF_MAX_POOL_SIZE = 1000000;                 // max size of pool of threads
    
    // thread count wait time in ms
    private int m_thread_count_check_wait_ms = DEF_THREAD_COUNT_CHECK_WAIT_MS;
    
    // Bridge Components
    private ErrorLogger m_logger = null;
    private PreferenceManager m_preferences = null;
    private EventsProcessor m_events_processor = null;
    private Manager m_manager = null;
    
    // Thread count
    private int m_thread_count = 1;   // ourself
    private boolean m_running = false; 
    
    // Jetty Server
    private Server m_server = null;
    private Server m_ws_service = null;
    
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
        this.m_manager.setBridgeMain(this);
        
        // get the thread pooling configuration
        int core_pool_size = this.m_preferences.intValueOf("threads_core_pool_size");
        if (core_pool_size <= 0) {
            core_pool_size = DEF_CORE_POOL_SIZE;
        }
        int max_pool_size = this.m_preferences.intValueOf("threads_max_pool_size");
        if (max_pool_size <= 0) {
            max_pool_size = DEF_MAX_POOL_SIZE;
        }
        
        // Threading Pool Config
        this.errorLogger().warning("Main: Jetty Thread Executor Pool: initial pool: " + core_pool_size + " max: " + max_pool_size);
        
        // initialize the server
        ExecutorThreadPool threadPool = new ExecutorThreadPool(max_pool_size, core_pool_size);
        this.m_server = new Server(threadPool);
        
        // create the SSL context and establish the handler for the context
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath(this.m_preferences.valueOf("mds_gw_context_path"));
        this.m_server.setHandler(context);

        // Enable SSL Support
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath("keystore.jks");
        sslContextFactory.setKeyStorePassword(this.m_preferences.valueOf("mds_gw_keystore_password"));
        ServerConnector sslConnector = new ServerConnector(this.m_server, sslContextFactory);
        sslConnector.setHost("0.0.0.0");
        sslConnector.setPort(this.m_preferences.intValueOf("mds_gw_port"));
        sslConnector.setIdleTimeout(TimeUnit.HOURS.toMillis(2));
        sslConnector.setAcceptQueueSize(10000);
        sslConnector.setReuseAddress(true);
        this.m_server.addConnector(sslConnector);

        // eventing process servlet bindings (wildcarded)
        context.addServlet(new ServletHolder(this.m_events_processor), this.m_preferences.valueOf("mds_gw_events_path") + "/*");
        
        // setup our websocket server (must support WSS)
        this.m_ws_service = new Server();
        ServerConnector logger_server_connector = new ServerConnector(this.m_ws_service,sslContextFactory);
        logger_server_connector.setHost("0.0.0.0");
        logger_server_connector.setPort(this.m_preferences.intValueOf("websocket_streaming_port"));
        logger_server_connector.setIdleTimeout(TimeUnit.HOURS.toMillis(2));
        logger_server_connector.setAcceptQueueSize(1000);
        logger_server_connector.setReuseAddress(true);
        this.m_ws_service.addConnector(logger_server_connector);
        
        // default context handler for WS
        ServletContextHandler logger_context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        logger_context.setContextPath("/");
        this.m_ws_service.setHandler(logger_context);
        
        // Logging Service context handler
        ServletHolder logEvents = new ServletHolder("ws-logger", LoggerWebSocketServlet.class);
        logger_context.addServlet(logEvents, "/logger/*");
        
        // add a shutdown hook for graceful shutdowns...
        Runtime.getRuntime().addShutdownHook(
            new Thread() {
                @Override
                public void run() {
                    errorLogger().warning("Main: Stopping Listeners...");
                    m_manager.stopListeners();

                    errorLogger().warning("Main: Removing webhook...");
                    m_manager.removeWebhook();
                }
            }
        );
        
        try {
            // start our thread counter task
            Thread t = new Thread(this);
            t.start();
        }
        catch (Exception ex) {
            this.errorLogger().warning("Main: Exception caught while starting thread count updater task: " + ex.getMessage());
        }
    }
    
    // start the bridge
    public void start() {
        try {
            // DEBUG
            this.errorLogger().warning("Main: Starting Bridge instance...");
            
            // initialize the listeners
            this.m_manager.initListeners();

            // Start the Websocket Service
            this.errorLogger().warning("Main: Starting logger service");
            this.m_ws_service.start();   
            
            // Start the Bridge Service
            this.errorLogger().warning("Main: Starting bridge service");
            this.m_server.start();

            // Direct the manager to establish the webhooks to Connector/mDS/Cloud
            this.m_manager.initWebhooks();
            
            // Start statistics generation
            this.m_manager.startStatisticsMonitoring();

            // Join
            this.m_server.join();
            this.m_ws_service.join();
        }
        catch (Exception ex) {
            this.errorLogger().critical("Main: EXCEPTION during bridge start(): " + ex.getMessage(),ex);
        }
    }
    
    // stop the bridge
    public void stop() {
        try {
            // stop the bridge service
            this.errorLogger().warning("Main: Stopping current bridge service...");
            this.m_server.stop();
            
            // stop the WS service
            this.errorLogger().warning("Main: Stopping websocket service...");
            this.m_ws_service.stop();
            
            // current bridge server stoped
            this.errorLogger().warning("Main: All services have been stopped");
        }
        catch (Exception ex) {
            // ERROR
            this.errorLogger().critical("Main: EXCEPTION during service(s) stop: " + ex.getMessage());
        }
    }
    
    // restart
    public void restart() {
        try {
            // Simply call the script to kill and restart this bridge
            this.errorLogger().critical("Main: Killing and restarting the bridge....");
            Utils.waitForABit(null, 1000);
            
            //
            // restart.sh lives in the HOME directory of the "arm" account running the bridge...
            // java is running in ${HOME}/service/target
            //
            Runtime.getRuntime().exec("../../restart.sh");
        }
        catch (IOException ex) {
            this.errorLogger().critical("Main: Exception caught during killing and Restarting the bridge: " + ex.getMessage());
        }
    }
    
    // error logger
    private ErrorLogger errorLogger() {
        return this.m_logger;
    }
    
    // update the active thread count
    private void updateActiveThreadCount() {
        try {
            int count = 0;
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            for (Thread t : threadSet) {
                if (t.isDaemon() || t.isAlive()) {
                    ++count;
                }
            }
                        
            // add one for main...
            ++count;
            
            // record the count
            this.m_thread_count = count;
        }
        catch (Exception ex) {
            this.errorLogger().warning("Main: Exception caught while counting threads: " + ex.getMessage());
            this.m_thread_count = -1;
        }
    }
    
    // thread count looper
    private void updateActiveThreadCountLoop() {
        while(this.m_running == true) {
            // Wait a bit
            Utils.waitForABit(this.errorLogger(),this.m_thread_count_check_wait_ms);  
            
            // Get the updated thread count
            this.updateActiveThreadCount();
        }
    }
    
    // get our active thread count
    public int getActiveThreadCount() {
        return this.m_thread_count;
    }

    @Override
    public void run() {
        this.m_running = true;
        this.updateActiveThreadCountLoop();
    }
}