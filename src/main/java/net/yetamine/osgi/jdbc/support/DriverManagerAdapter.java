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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import net.yetamine.osgi.jdbc.DriverProvider;
import net.yetamine.osgi.jdbc.DriverSequence;

/**
 * Adapts {@link DriverManager} to {@link DriverProvider} interface.
 *
 * <p>
 * The implementation is a singleton that other clients, including other
 * implementations of {@link DriverProvider}, may use to use out-of-OSGi
 * services, e.g., drivers registered prior OSGi framework launch. This adapter
 * therefore may serve as a natural fallback for the other implementations that
 * might not use {@link DriverManager} directly because of either classloading
 * restrictions or interference with the weaving hook installed by this bundle.
 */
public final class DriverManagerAdapter implements DriverProvider {

    /* Implementation note:
     *
     * For this class to work better, 'DynamicImport-Package: *' should be
     * specified for this bundle, which allows this class to see more drivers,
     * although possibly not all. Usually, the drivers should be intercepted
     * by the weaving hook and bridging, but those that can't be intercepted
     * needs this kind of support.
     */

    /** Sole instance of this class. */
    private static final DriverProvider INSTANCE = new DriverManagerAdapter();

    /**
     * Creates a new instance.
     */
    private DriverManagerAdapter() {
        // Default constructor
    }

    /**
     * Returns an instance.
     *
     * @return an instance
     */
    public static DriverProvider instance() {
        return INSTANCE;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return getClass().getName();
    }

    /**
     * @see net.yetamine.osgi.jdbc.DriverProvider#connection(java.lang.String,
     *      java.util.Properties)
     */
    @Override
    public Connection connection(String url, Properties properties) throws SQLException {
        return DriverManager.getConnection(url, properties);
    }

    /**
     * @see net.yetamine.osgi.jdbc.DriverProvider#driver(java.lang.String)
     */
    @Override
    public Driver driver(String url) throws SQLException {
        return DriverManager.getDriver(url);
    }

    /**
     * @see net.yetamine.osgi.jdbc.DriverProvider#drivers()
     */
    @Override
    public DriverSequence drivers() {
        return () -> EnumerationIterator.from(DriverManager.getDrivers());
    }
}
