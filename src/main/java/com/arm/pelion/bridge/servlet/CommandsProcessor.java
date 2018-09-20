/**
 * @file EventsProcessor.java
 * @brief Events Servlet Handler
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
import com.arm.pelion.bridge.preferences.PreferenceManager;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Events Servlet Handler
 *
 * @author Doug Anson
 */
@WebServlet(name = "commands", urlPatterns = {"/commands/*"})
public class CommandsProcessor extends HttpServlet {

    private Manager m_manager = null;
    private ErrorLogger m_error_logger = null;
    private PreferenceManager m_preferences = null;

    // constructor
    public CommandsProcessor(ErrorLogger error_logger,PreferenceManager preferences) {
        super();
        this.m_error_logger = error_logger;
        this.m_preferences = preferences;
        if (this.m_manager == null) {
            this.m_manager = Manager.getInstance(this,error_logger,preferences);
        }
    }

    // get our manager
    public Manager manager() {
        return this.m_manager;
    }

    /**
     * Processes requests for both HTTP
     * <code>GET</code> and
     * <code>POST</code> methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (this.m_manager != null) {
            // process our request
            this.m_manager.processPeerCommand(request, response);
        }
        else {
            // error - no Manager instance
            this.m_error_logger.warning("CommandsProcessor: ERROR: Manager instance is NULL. Ignoring the processing request...");
            
            // send a response
            try {
                response.setContentType("application/json;charset=utf-8");
                response.setHeader("Pragma", "no-cache");
                PrintWriter out = response.getWriter();
                out.println("{}");
            }
            catch (IOException ex) {
                this.m_error_logger.critical("CommandsProcessor: Unable to send response back to requestor", ex);
            }
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP
     * <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // GET not used - just send a response
        try {
            response.setContentType("application/json;charset=utf-8");
            response.setHeader("Pragma", "no-cache");
            PrintWriter out = response.getWriter();
            out.println("{}");
        }
        catch (IOException ex) {
            this.m_error_logger.critical("CommandsProcessor(GET): returning empty result", ex);
        }
    }

    /**
     * Handles the HTTP
     * <code>PUT</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // PUT not used - just send a response
        try {
            response.setContentType("application/json;charset=utf-8");
            response.setHeader("Pragma", "no-cache");
            PrintWriter out = response.getWriter();
            out.println("{}");
        }
        catch (IOException ex) {
            this.m_error_logger.critical("CommandsProcessor(PUT): returning empty result", ex);
        }
    }

    /**
     * Handles the HTTP
     * <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP
     * <code>DELETE</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // DELETE not used - just send a response
        try {
            response.setContentType("application/json;charset=utf-8");
            response.setHeader("Pragma", "no-cache");
            PrintWriter out = response.getWriter();
            out.println("{}");
        }
        catch (IOException ex) {
            this.m_error_logger.critical("CommandsProcessor(DELETE): returning empty result", ex);
        }
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Pelion Bridge 1.0";
    }// </editor-fold>
}
