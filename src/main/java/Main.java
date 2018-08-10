
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
import com.arm.pelion.bridge.core.ErrorLogger;
import com.arm.pelion.bridge.preferences.PreferenceManager;
import com.arm.pelion.bridge.servlet.EventsProcessor;
import com.arm.pelion.bridge.servlet.Manager;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;

/**
 * Primary entry point for the connector-bridge Jetty application
 *
 * @author Doug Anson
 */
public class Main {
    // Defaults
    private static int DEF_CORE_POOL_SIZE = 1000;
    private static int DEF_MAX_POOL_SIZE = 1000000;
    private static int DEF_KEEP_ALIVE = 60;
    
    // Main entry point for the Bridge...
    public static void main(String[] args) throws Exception {
        
        // Error Logger
        ErrorLogger error_logger = new ErrorLogger();
        
        // Preferences Manager
        PreferenceManager preferences = new PreferenceManager(error_logger,Manager.LOG_TAG);

        // configure the error logging level
        error_logger.configureLoggingLevel(preferences);

        // initialize the server
        Server server = new Server(preferences.intValueOf("mds_gw_port"));
        
        // get the thread pooling configuration
        int core_pool_size = preferences.intValueOf("threads_core_pool_size");
        if (core_pool_size <= 0) {
            core_pool_size = DEF_CORE_POOL_SIZE;
        }
        int max_pool_size = preferences.intValueOf("threads_max_pool_size");
        if (max_pool_size <= 0) {
            max_pool_size = DEF_MAX_POOL_SIZE;
        }
        int keep_alive_time = preferences.intValueOf("threads_keep_alive_time");
        if (keep_alive_time <= 0) {
            keep_alive_time = DEF_KEEP_ALIVE;
        }

        // create the SSL context and establish the handler for the context
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath(preferences.valueOf("mds_gw_context_path"));
        server.setHandler(context);

        // check for and add SSL support if configured...
        if (preferences.booleanValueOf("mds_gw_use_ssl") == true) {
            // Enable SSL Support
            SslSocketConnector sslConnector = new SslSocketConnector();
            sslConnector.setPort(preferences.intValueOf("mds_gw_port") + 1);
            sslConnector.setHost("0.0.0.0");
            sslConnector.setKeystore("keystore.jks");
            sslConnector.setPassword(preferences.valueOf("mds_gw_keystore_password"));
            server.addConnector(sslConnector);
        }
        
        // Create the Eventing Processor
        EventsProcessor eventsProcessor = new EventsProcessor(error_logger,preferences);
        
        // Initialize the listeners within the Manager of the Eventing Processor
        final Manager manager = eventsProcessor.manager();
        manager.initListeners();

        // add a shutdown hook for graceful shutdowns...
        Runtime.getRuntime().addShutdownHook(
            new Thread() {
                @Override
                public void run() {
                    System.out.println(Manager.LOG_TAG + ": Resetting notification handlers...");
                    manager.resetNotifications();

                    System.out.println(Manager.LOG_TAG + ": Stopping Listeners...");
                    manager.stopListeners();
                }
            }
        );

        // eventing process servlet bindings (wildcarded)
        context.addServlet(new ServletHolder(eventsProcessor), preferences.valueOf("mds_gw_events_path") + "/*");

        // DEBUG for the Threading Pool Config
        System.out.println(Manager.LOG_TAG + ": Thread Executor Pool: corePool: " + core_pool_size + " maxPool: " + max_pool_size + " keepalive (sec): " + keep_alive_time);
        
        // set the max threads in our thread pool
        server.setThreadPool(new ExecutorThreadPool(core_pool_size, max_pool_size, keep_alive_time, TimeUnit.SECONDS));
                
        // start me!
        server.start();

        // Direct the manager to establish the webhooks to Connector/mDS/Cloud
        manager.initWebhooks();

        // Join me!
        server.join();
    }
}
