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

package net.yetamine.osgi.jdbc;

import java.sql.Driver;

/**
 * Constants available by the JDBC support for {@link Driver} services.
 */
public final class DriverConstants {

    /**
     * The name of the service property of a {@link Long} value with the
     * identifier of the bundle that originally registered the driver.
     */
    public static final String DRIVER_BUNDLE = "driver.bundle";

    /**
     * The name of the service property of a {@link String} value with the class
     * name of the driver implementation. Although this value is often not equal
     * to the class name specified in the driver service declaration, both share
     * usually a significant package prefix, hence it can be deduced their
     * relationship.
     */
    public static final String DRIVER_CLASS = "driver.class";

    /**
     * The name of the service property of a {@link String} value with the
     * version of the driver as reported by the driver in the usual format
     * <i>major.minor</i>.
     */
    public static final String DRIVER_VERSION = "driver.version";

    /**
     * Prevents creating instances of this class.
     */
    private DriverConstants() {
        throw new AssertionError();
    }
}
