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

package net.yetamine.osgi.jdbc.support;

import java.sql.Driver;
import java.util.Objects;

/**
 * Reference to a driver.
 *
 * <p>
 * This class allows to avoid using the driver's {@link Object#equals(Object)}
 * and {@link Object#hashCode()} methods which might be misleading or harmful.
 * Two instances of this class are hence equal only if they refer to the same
 * driver instance.
 */
public final class DriverReference {

    /** Represented driver. */
    private final Driver driver;

    /**
     * Creates a new instance.
     *
     * @param instance
     *            the driver instance. It must not be {@code null}.
     */
    public DriverReference(Driver instance) {
        driver = Objects.requireNonNull(instance);
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof DriverReference) && (driver == ((DriverReference) obj).driver);
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return driver.hashCode();
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final String f = "Driver[class=%s, version=%s, id=%08x]";
        return String.format(f, driverClass().getTypeName(), driverVersion(), System.identityHashCode(driver));
    }

    /**
     * Returns the driver class name.
     *
     * @return the driver class
     */
    public Class<? extends Driver> driverClass() {
        return driver.getClass();
    }

    /**
     * Returns the driver version as reported by the driver as a {@link String}
     * in the format <i>major.minor</i>
     *
     * @return the driver version
     */
    public String driverVersion() {
        return String.format("%d.%d", driver.getMajorVersion(), driver.getMinorVersion());
    }

    /**
     * Returns the driver.
     *
     * @return the driver
     */
    public Driver driver() {
        return driver;
    }
}
