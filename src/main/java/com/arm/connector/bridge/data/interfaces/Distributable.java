/**
 * @file    DistributableHashMap.java
 * @brief Interface specifying the decorated features of a distributable hashmap
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
 * Distributable 
 * @author Doug Anson
 */
public interface Distributable {
    // upsert (for key/value pairs)
    public void upsert(String key,Serializable value);
    
    // delete (for key/value pairs)
    public void delete(String key);
    
    // upsert (for array values)
    public void upsert(Serializable value);
    
    // delete (for array values)
    public void delete(int index);
}
