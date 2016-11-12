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

package net.yetamine.osgi.jdbc.thunk;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverAction;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Enumeration;
import java.util.Properties;

import net.yetamine.osgi.jdbc.internal.Activator;
import net.yetamine.osgi.jdbc.support.IteratorEnumeration;
import net.yetamine.osgi.jdbc.support.NilDriverAction;

/**
 * The bridging interface for the woven code to invoke JDBC support services.
 *
 * <p>
 * The interface of this class is similar to {@link java.sql.DriverManager}, but
 * all methods have an extra parameter which identifies the invoking bundle. The
 * identifier shall be woven in the code by the weaving hook service when
 * loading the class.
 */
public final class DriverSupportThunk {

    /* Implementation note:
     *
     * The interface of this class does not contain the methods that are not
     * significant for driver management or connection creation. However, all
     * retained methods must keep the names of the original and differ by the
     * identifier parameter only.
     *
     * The identifier parameter must be always the last, which is easiest for
     * modifying original calls (just one more argument, a constant actually,
     * must be pushed and the target class name changed). The method name match
     * similarly helps patching the code (it is enough to redirect calls to the
     * methods with the same name and signature, except for the extra parameter).
     *
     * The identifier parameter uses the bundle identifier naturally, because
     * it remains stable as required. Moreover, it makes everything simpler.
     * If it were a security or other similar problem, random identifiers may
     * be used instead and mapped to bundles inside the non-public part, kept
     * out of reach of other code.
     */

    private DriverSupportThunk() {
        throw new AssertionError();
    }

    // Driver management part

    /**
     * Registers the given driver.
     *
     * @param driver
     *            the new JDBC driver. It must not be {@code null}.
     * @param caller
     *            the identifier of the caller
     *
     * @throws SQLException
     *             if a database access error occurs
     *
     * @see java.sql.DriverManager#registerDriver(Driver)
     */
    public static void registerDriver(Driver driver, long caller) throws SQLException {
        Activator.registrar().register(caller, driver, NilDriverAction.instance());
    }

    /**
     * Registers the given driver.
     *
     * @param driver
     *            the new JDBC driver. It must not be {@code null}.
     * @param action
     *            the {@code DriverAction} implementation to be used when
     *            {@code DriverManager#deregisterDriver} is called
     * @param caller
     *            the identifier of the caller
     *
     * @throws SQLException
     *             if a database access error occurs
     *
     * @see java.sql.DriverManager#registerDriver(Driver, DriverAction)
     */
    public static void registerDriver(Driver driver, DriverAction action, long caller) throws SQLException {
        Activator.registrar().register(caller, driver, NilDriverAction.ifNull(action));
    }

    /**
     * Removes the specified driver from the list of registered drivers.
     *
     * @param driver
     *            the JDBC driver to remove
     * @param caller
     *            the identifier of the caller
     *
     * @throws SQLException
     *             if a database access error occurs
     * @throws SecurityException
     *             if a security manager exists and it denies permission to
     *             deregister a driver
     *
     * @see java.sql.DriverManager#deregisterDriver(Driver)
     */
    public static void deregisterDriver(Driver driver, long caller) throws SQLException {
        Activator.registrar().unregister(caller, driver);
    }

    /**
     * Retrieves all of the currently available JDBC drivers.
     *
     * <p>
     * This method consults {@link java.sql.DriverManager} as well in order to
     * return drivers that might be available to everybody via the system class
     * loader. However, it prefers own drivers and adds them first in the list.
     *
     * @param caller
     *            the identifier of the caller
     *
     * @return the list of JDBC drivers
     *
     * @see java.sql.DriverManager#getDrivers()
     */
    public static Enumeration<Driver> getDrivers(long caller) {
        return IteratorEnumeration.from(Activator.provider().drivers().iterator());
    }

    /**
     * Attempts to locate a driver that understands the given URL.
     *
     * @param url
     *            a database URL
     * @param caller
     *            the identifier of the caller
     *
     * @return a driver that can connect to the given URL
     *
     * @throws SQLException
     *             if a database access error occurs
     *
     * @see java.sql.DriverManager#getDriver(String)
     */
    public static Driver getDriver(String url, long caller) throws SQLException {
        return Activator.provider().driver(url);
    }

    // Connection retrieval

    /**
     * Attempts to establish a connection to the given database URL.
     *
     * @param url
     *            a database URL
     * @param properties
     *            a list of arbitrary string tag/value pairs as connection
     *            arguments
     * @param caller
     *            the identifier of the caller
     *
     * @return a connection to the URL
     *
     * @throws SQLException
     *             if a database access error occurs or the URL is {@code null}
     * @throws SQLTimeoutException
     *             if the driver has determined that the timeout value specified
     *             by the {@link java.sql.DriverManager#setLoginTimeout} method
     *             has been exceeded and has at least tried to cancel the
     *             current database connection attempt
     *
     * @see java.sql.DriverManager#getConnection(String, Properties)
     */
    public static Connection getConnection(String url, Properties properties, long caller) throws SQLException {
        return Activator.provider().connection(url, properties);
    }

    /**
     * Attempts to establish a connection to the given database URL.
     *
     * @param url
     *            a database URL
     * @param user
     *            the database user on whose behalf the connection is being made
     * @param password
     *            the user's password
     * @param caller
     *            the identifier of the caller
     *
     * @return a connection to the URL
     *
     * @throws SQLException
     *             if a database access error occurs or the URL is {@code null}
     * @throws SQLTimeoutException
     *             if the driver has determined that the timeout value specified
     *             by the {@link java.sql.DriverManager#setLoginTimeout} method
     *             has been exceeded and has at least tried to cancel the
     *             current database connection attempt
     *
     * @see java.sql.DriverManager#getConnection(String, String, String)
     */
    public static Connection getConnection(String url, String user, String password, long caller) throws SQLException {
        final Properties properties = new java.util.Properties();

        if (user != null) {
            properties.put("user", user);
        }

        if (password != null) {
            properties.put("password", password);
        }

        return getConnection(url, properties, caller);
    }

    /**
     * Attempts to establish a connection to the given database URL.
     *
     * @param url
     *            a database URL
     * @param caller
     *            the identifier of the caller
     *
     * @return a connection to the URL
     *
     * @throws SQLException
     *             if a database access error occurs or the URL is {@code null}
     * @throws SQLTimeoutException
     *             if the driver has determined that the timeout value specified
     *             by the {@link java.sql.DriverManager#setLoginTimeout} method
     *             has been exceeded and has at least tried to cancel the
     *             current database connection attempt
     *
     * @see java.sql.DriverManager#getConnection(String)
     */
    public static Connection getConnection(String url, long caller) throws SQLException {
        return Activator.provider().connection(url, new Properties());
    }
}
