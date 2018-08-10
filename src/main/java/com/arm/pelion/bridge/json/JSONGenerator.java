/**
 * @file    JSONGenerator.java
 * @brief JSON Generator wrapper class
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
package com.arm.pelion.bridge.json;

/**
 *
 * @author Doug Anson
 */
public class JSONGenerator {

    // default constructor
    public JSONGenerator() {
    }

    // create JSON
    public String generateJson(Object json) {
        String str_json = com.codesnippets4all.json.generators.JsonGeneratorFactory.getInstance().newJsonGenerator().generateJson(json);
        if (str_json != null && str_json.length() > 0) {
            int last_index = str_json.length() - 1;
            if (str_json.charAt(0) == '[' && str_json.charAt(last_index) == ']') {
                return str_json.substring(1, last_index);
            }
        }
        return str_json;
    }
}
