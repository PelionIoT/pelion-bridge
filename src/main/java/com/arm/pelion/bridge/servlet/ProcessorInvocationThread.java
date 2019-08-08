/**
 * @file ProcessorInvocationThread.java
 * @brief Processor Invocation Thread for the servlet request
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
package com.arm.pelion.bridge.servlet;

import com.arm.pelion.bridge.core.ErrorLogger;
import com.arm.pelion.bridge.servlet.interfaces.ServletProcessor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Processor Invocation Thread for the servlet request
 * @author Doug Anson
 */
public class ProcessorInvocationThread implements Runnable {
    private HttpServletRequest m_request = null;
    private HttpServletResponse m_response = null;
    private ServletProcessor m_processor = null;
    private ErrorLogger m_logger = null;
    
    // constructor
    public ProcessorInvocationThread(HttpServletRequest request, HttpServletResponse response, ServletProcessor processor,ErrorLogger logger) {
        this.m_request = request;
        this.m_response = response;
        this.m_processor = processor;
        this.m_logger = logger;
    }

    @Override
    public void run() {
        if (this.m_processor != null && this.m_request != null && this.m_response != null) {
            this.m_logger.info("PIT: Processing Request...");
            this.m_processor.invokeRequest(this.m_request, this.m_response);
        }
        else if (this.m_request == null) {
            this.m_logger.warning("PIT: Unable to process Request:  NULL Request");
        }
        else if (this.m_response == null) {
            this.m_logger.warning("PIT: Unable to process Request:  NULL Response");
        }
        else {
            this.m_logger.warning("PIT: Unable to process Request:  NULL Processor");
        }
    }
}
