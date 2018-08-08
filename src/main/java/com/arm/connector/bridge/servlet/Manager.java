/**
 * @file    Manager.java
 * @brief Servlet Manager
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
package com.arm.connector.bridge.servlet;

import com.arm.connector.bridge.coordinator.domains.DomainChecker;
import com.arm.connector.bridge.coordinator.domains.DomainManager;
import com.arm.connector.bridge.core.ErrorLogger;
import com.arm.connector.bridge.core.Utils;
import com.arm.connector.bridge.preferences.PreferenceManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Main Servlet Manager
 *
 * @author Doug Anson
 */
public final class Manager {
    public static final String LOG_TAG = "Connector Bridge";            // Log Tag
    public static final String BRIDGE_VERSION_STR = "1.0.0";            // our version (need to tie to build...)
    public static final Double MDS_NON_DOMAIN_VER_BASE = 2.5;           // first version of mDS without domain usage...
    private HttpServlet m_servlet = null;
    private static volatile Manager m_manager = null;
    private ErrorLogger m_error_logger = null;
    private PreferenceManager m_preference_manager = null;
    private HashMap<String, DomainManager> m_domain_managers = null;
    private Runnable m_domain_listener = null;
    private Thread m_domain_listener_thread = null;
    private DomainChecker m_domain_checker = null;
    private String m_mds_version = null;
    private boolean m_mds_uses_domains = true;
    private String m_mds_gw_events_path = null;

    // instance factory
    public static Manager getInstance(HttpServlet servlet,ErrorLogger error_logger,PreferenceManager preferences) {
        if (Manager.m_manager == null) {
            Manager.m_manager = new Manager(error_logger,preferences);
        }
        Manager.m_manager.setServlet(servlet);
        return Manager.m_manager;
    }

    // default constructor
    @SuppressWarnings("empty-statement")
    public Manager(ErrorLogger error_logger,PreferenceManager preferences) {
        // save the error handler
        this.m_error_logger = error_logger;
        this.m_preference_manager = preferences;
        
        // create the domain manager list
        this.m_domain_managers = new HashMap<>();

        // announce our self
        this.errorLogger().info(LOG_TAG + ": Date: " + Utils.dateToString(Utils.now()) + ". Bridge version: v" + BRIDGE_VERSION_STR);

        // configure the error logger logging level
        this.m_error_logger.configureLoggingLevel(this.m_preference_manager);

        // Events URI...
        this.m_mds_gw_events_path = this.m_preference_manager.valueOf("mds_gw_events_path");

        // determine if we are using mDS v2.5 or greater
        this.m_mds_version = this.preferences().valueOf("mds_version");
        try {
            Double mds_version = Double.valueOf(this.m_mds_version);
            if (mds_version >= MDS_NON_DOMAIN_VER_BASE) {
                this.m_mds_uses_domains = false;
            }
        }
        catch (NumberFormatException ex) {
            // silent
            ;
        }

        // setup our domain listener
        if (this.m_mds_uses_domains) {
            boolean enable_domain_listener = this.preferences().booleanValueOf("mds_domain_listener");
            if (enable_domain_listener) {
                // allocate a domain checker
                this.m_domain_checker = new DomainChecker(this.m_error_logger, this.m_preference_manager);

                // start a listener to watch for new domains...
                this.m_domain_listener = new DomainListener(this.preferences().valueOf("mds_address"),
                        this.preferences().intValueOf("mds_domain_listener_sleep_interval_ms"));
                this.m_domain_listener_thread = new Thread(this.m_domain_listener);
                this.m_domain_listener_thread.start();
            }
            else {
                // just add the default configured mDS domain per the config file...
                this.addDomainManager(new DomainManager(this.m_error_logger, this.m_preference_manager, this.preferences().valueOf("mds_domain")));
            }
        }
        else {
            // no domains are used... (so we will account for a single domain using the non-domain value for internal accounting...)
            this.addDomainManager(new DomainManager(this.m_error_logger, this.m_preference_manager, this.preferences().valueOf("mds_def_domain")));
        }
    }

    public void initListeners() {
        for (DomainManager manager : this.m_domain_managers.values()) {
            if (manager.getOrchestrator().peerListenerActive() == false) {
                manager.getOrchestrator().initPeerListener();
            }
        }
    }

    public void stopListeners() {
        for (DomainManager manager : this.m_domain_managers.values()) {
            if (manager.getOrchestrator().peerListenerActive() == true) {
                manager.getOrchestrator().stopPeerListener();
            }
        }
    }

    public void initWebhooks() {
        for (DomainManager manager : this.m_domain_managers.values()) {
            manager.getOrchestrator().initializeDeviceServerWebhook();
        }
    }

    public void resetNotifications() {
        for (DomainManager manager : this.m_domain_managers.values()) {
            manager.getOrchestrator().resetDeviceServerWebhook();
        }
    }

    public void processConsole(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        DomainManager manager = this.getDomainManager(request);
        manager.processConsole(request, response);
    }

    public void processNotification(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        DomainManager manager = this.getDomainManager(request);
        if (manager != null) {
            manager.processNotification(request, response);
        }
    }

    public final void addDomainManager(DomainManager domain_manager) {
        this.m_domain_managers.put(domain_manager.domain(), domain_manager);
    }

    public DomainManager getDomainManager(HttpServletRequest request) {
        return this.getDomainManager(this.eventNotificationURIToDomain(request));
    }

    // TO DO
    private String eventNotificationURIToDomain(HttpServletRequest request) {
        String domain = "<unset>";

        // DEBUG
        //this.errorLogger().info("URI: " + request.getRequestURI()  + " path: " + request.getServletPath() + " method: " + request.getMethod());
        // make sure that we are looking event notification path
        if (request.getServletPath().equalsIgnoreCase(this.m_mds_gw_events_path)) {
            // convert the URI to an array
            String[] components = request.getRequestURI().replace('/', ' ').split(" ");

            // mDS domain is the last component
            domain = components[components.length - 1];

            // DEBUG
            //this.errorLogger().info("servletRequestToDomain: domain=[" + domain + "]");
        }
        else {
            // this is a console request - nail it to "console"
            domain = "console";
        }

        // return the domain
        return domain;
    }

    private DomainManager getDomainManager(String domain) {
        return this.m_domain_managers.get(domain);
    }

    private void setServlet(HttpServlet servlet) {
        this.m_servlet = servlet;
    }

    public HttpServlet getServlet() {
        return this.m_servlet;
    }

    public ErrorLogger errorLogger() {
        return this.m_error_logger;
    }

    public final PreferenceManager preferences() {
        return this.m_preference_manager;
    }

    public void checkForNewDomain() {
        // get the current domain list
        ArrayList<String> domains = this.m_domain_checker.getDomainList();
        for (int i = 0; domains != null && i < domains.size(); ++i) {
            DomainManager domain_manager = this.getDomainManager(domains.get(i));
            if (domain_manager == null) {
                // add the new domain manager
                this.errorLogger().info(LOG_TAG + ": Adding Domain Manager for domain: " + domains.get(i));
                this.addDomainManager(new DomainManager(this.m_error_logger, this.m_preference_manager, domains.get(i)));
            }
        }
    }

    public class DomainListener implements Runnable {

        private String m_mds_ip_address = null;
        private int m_sleep_interval_ms = 0;
        private boolean m_do_loop = false;

        DomainListener(String mds_ip_address, int sleep_interval_ms) {
            this.m_mds_ip_address = mds_ip_address;
            this.m_sleep_interval_ms = sleep_interval_ms;
            this.m_do_loop = true;
        }

        @Override
        @SuppressWarnings("empty-statement")
        public void run() {
            while (this.m_do_loop) {
                try {
                    // check for a new mDS domain
                    checkForNewDomain();

                    // initialize any listeners that are not already initialized..
                    initListeners();

                    // sleep for a bit...
                    Thread.sleep(this.m_sleep_interval_ms);
                }
                catch (InterruptedException ex) {
                    // silent
                    ;
                }
            }
        }

        public synchronized void stopListener() {
            this.m_do_loop = false;
        }
    }
}
