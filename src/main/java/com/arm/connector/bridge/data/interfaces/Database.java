/**
 * @file    Database.java
 * @brief Interface specifying the functions of a distributed database
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
package com.arm.connector.bridge.data.interfaces;

import java.io.Serializable;

/**
 * Database interface
 * @author Doug Anson
 */
public interface Database extends Distributable {
    // initialize DB and table
    public void initialize(String tablename);
    
    // load
    public void load();
    
    // close
    public void close();
    
    // upsert
    @Override
    public void upsert(String key,Serializable value);
    
    // delete (row)
    @Override
    public void delete(String key);
}
