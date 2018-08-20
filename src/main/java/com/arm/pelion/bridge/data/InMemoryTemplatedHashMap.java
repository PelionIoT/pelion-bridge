/**
 * @file InMemoryTemplatedHashMap.java
 * @brief In Memory HashMap implementation with distributable decoration
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
package com.arm.pelion.bridge.data;

import java.util.HashMap;
import com.arm.pelion.bridge.data.interfaces.Distributable;
import java.io.Serializable;

/**
 * InMemoryTemplatedHashMap
 * @author Doug Anson
 * @param <T> - template parameter for the HashMap value type
 */
public class InMemoryTemplatedHashMap<T> extends HashMap<String,T> implements Distributable {
    private Object m_container = null;
    
    // default constructor
    public InMemoryTemplatedHashMap(Object container) {
        super();
        this.m_container = container;
    }
    
    // get the container class
    public Object container() {
        return this.m_container;
    }

    // upsert
    @Override
    public void upsert(String key, Serializable value) {
        // not used
    }

    // delete
    @Override
    public void delete(String key) {
        // not used
    }

    // not used
    @Override
    public void upsert(Serializable value) {
        // not used
    }

    // not used
    @Override
    public void delete(int index) {
        // not used
    }
}
