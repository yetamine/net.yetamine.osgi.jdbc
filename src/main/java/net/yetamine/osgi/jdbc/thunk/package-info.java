/*
 * Copyright 2016 Yetamine
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Provides the thunk for calling the OSGi support core.
 *
 * <p>
 * This package should be considered an implementation detail and it should not
 * be used directly by any code, in spite of being published. Its publishing is
 * necessary, so that the woven classes could call it.
 */
package net.yetamine.osgi.jdbc.thunk;
