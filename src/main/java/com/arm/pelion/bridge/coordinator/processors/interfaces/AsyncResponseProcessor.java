/**
 * @file AsyncResponseProcessor.java
 * @brief Pelion AsyncResposne Processor Interface
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
package com.arm.pelion.bridge.coordinator.processors.interfaces;

import java.util.Map;

/**
 * This Interface defines a contract for an AsyncResponse Handler/Processor
 *
 * @author Doug Anson
 */
public interface AsyncResponseProcessor {
    // process async response
    boolean processAsyncResponse(Map response);
}
