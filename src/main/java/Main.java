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

import com.arm.pelion.bridge.core.BridgeMain;

/**
 * Main: Main entry point for the pelion-bridge application
 *
 * @author Doug Anson
 */
public class Main {
    public static String m_args[] = null;
    private static BridgeMain m_bridge = null;
    
    // Main entry point for the Bridge...
    public static void main(String[] args) throws Exception { 
        Main.m_args = args;
        Main.restart();
    }
    
    // Main Restart
    public static void restart() {
        if (Main.m_bridge != null) {
            // delete the old bridge
            Main.m_bridge.stop();
        }
        
        // create a new Bridge
        Main.m_bridge = new BridgeMain(Main.m_args);
        
        // Start the new Bridge
        Main.m_bridge.start();
    }
}
