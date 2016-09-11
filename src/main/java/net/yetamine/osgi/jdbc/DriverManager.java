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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverAction;
import java.sql.SQLException;
import java.sql.SQLPermission;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The surrogate of {@link java.sql.DriverManager} adjusted to work in OSGi
 * environment.
 */
public final class DriverManager {

    private DriverManager() {
        throw new AssertionError();
    }

    // Driver management part

    /**
     * The {@code SQLPermission} constant that allows the un-register a
     * registered JDBC driver.
     */
    private static final SQLPermission DEREGISTER_DRIVER_PERMISSION = new SQLPermission("deregisterDriver");

    /**
     * List of all registered drivers in their registration order, so that
     * finding a suitable driver is deterministic and earlier drivers are
     * preferred (which is consistent with {@link java.sql.DriverManager},
     * moreover it does not result in driver switching on registering new
     * drivers, only on deregistrations).
     *
     * <p>
     * This list serves as the lock for other structures when modified, so that
     * structures stay consistent. Not synchronizing on the class itself to
     * prevent abusing that.
     */
    private static final List<DriverRegistration> DRIVER_LIST = new CopyOnWriteArrayList<>();

    /**
     * Information about the registered drivers reachable through their
     * references.
     *
     * <p>
     * Synchronization with {@link #DRIVER_LIST} needed.
     */
    private static final Map<DriverReference, DriverRegistration> DRIVER_MAP = new HashMap<>();

    /**
     * Groups of the registered drivers, grouped by their tags.
     *
     * <p>
     * Synchronization with {@link #DRIVER_LIST} needed.
     */
    private static final Map<Object, Set<DriverRegistration>> DRIVER_GROUPS = new HashMap<>();

    /**
     * Registers the given driver.
     *
     * @param driver
     *            the new JDBC driver. It must not be {@code null}.
     *
     * @throws SQLException
     *             if a database access error occurs
     *
     * @see java.sql.DriverManager#registerDriver(Driver)
     */
    public static void registerDriver(Driver driver) throws SQLException {
        registerDriver(driver, null);
    }

    /**
     * Registers the given driver.
     *
     * @param driver
     *            the new JDBC driver. It must not be {@code null}.
     * @param action
     *            the {@code DriverAction} implementation to be used when
     *            {@code DriverManager#deregisterDriver} is called
     *
     * @throws SQLException
     *             if a database access error occurs
     *
     * @see java.sql.DriverManager#registerDriver(Driver, DriverAction)
     */
    public static void registerDriver(Driver driver, DriverAction action) throws SQLException {
        registerDriver(driver, action, null);
    }

    /**
     * Removes the specified driver from the list of registered drivers.
     *
     * @param driver
     *            the JDBC driver to remove
     *
     * @throws SQLException
     *             if a database access error occurs
     * @throws SecurityException
     *             if a security manager exists and it denies permission to
     *             deregister a driver
     *
     * @see java.sql.DriverManager#deregisterDriver(Driver)
     */
    public static void deregisterDriver(Driver driver) throws SQLException {
        if (driver == null) {
            return;
        }

        final DriverReference reference = new DriverReference(driver);
        LOGGER.debug("Deregistering driver: {}", reference);

        final SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            securityManager.checkPermission(DEREGISTER_DRIVER_PERMISSION);
        }

        final DriverRegistration registration;
        synchronized (DRIVER_LIST) {
            registration = DRIVER_MAP.remove(reference);
            if (registration == null) {
                return;
            }

            DRIVER_GROUPS.computeIfPresent(registration.group(), (k, g) -> {
                g.remove(registration.reference());
                return g.isEmpty() ? null : g;
            });

            final boolean removedDriver = DRIVER_LIST.remove(registration);
            assert removedDriver : "DRIVER_LIST didn't contain the driver!";
        }

        // Finish the removal
        assert (registration != null);
        LOGGER.debug("Found driver to deregister: {}", reference);
        registration.action().deregister();
        LOGGER.info("Deregistered driver: {}", reference);
    }

    /**
     * Retrieves all of the currently loaded JDBC drivers.
     *
     * <p>
     * This method consults {@link java.sql.DriverManager} as well in order to
     * return drivers that might be available to everybody via the system class
     * loader. However, it prefers own drivers and adds them first in the list.
     *
     * @return the list of JDBC drivers
     *
     * @see java.sql.DriverManager#getDrivers()
     */
    public static Enumeration<Driver> getDrivers() {
        final List<Driver> result = DRIVER_LIST.stream()            // @formatter:break
                .map(DriverRegistration::driver)                    // Extract the drivers only
                .collect(Collectors.toCollection(ArrayList::new));  // Make a mutable list for sure

        // Consult the system-wide available drivers (visible even for this class)
        for (Enumeration<Driver> drivers = java.sql.DriverManager.getDrivers(); drivers.hasMoreElements();) {
            result.add(drivers.nextElement());
        }

        return Collections.enumeration(result);
    }

    /**
     * Attempts to locate a driver that understands the given URL.
     *
     * @param url
     *            a database URL
     *
     * @return a driver that can connect to the given URL
     *
     * @throws SQLException
     *             if a database access error occurs
     *
     * @see java.sql.DriverManager#getDriver(String)
     */
    public static Driver getDriver(String url) throws SQLException {
        LOGGER.debug("Searching for a driver for URL: {}", url);
        for (DriverRegistration registration : DRIVER_LIST) {
            final Driver result = registration.driver();
            if (result.acceptsURL(url)) {
                return result;
            }
        }

        LOGGER.debug("Unable to find a driver for URL '{}'. Reverting to java.sql.DriverManager.", url);
        return java.sql.DriverManager.getDriver(url);
    }

    // Extensions for tagged registrations

    /**
     * Registers the given driver with the given tag.
     *
     * @param driver
     *            the new JDBC driver. It must not be {@code null}.
     * @param action
     *            the {@code DriverAction} implementation to be used when
     *            {@code DriverManager#deregisterDriver} is called
     * @param tag
     *            the tag to use for filtering the drivers. It may be
     *            {@code null} if no tag shall be given, otherwise it must be an
     *            object that is suitable as a key (immutable, with well-defined
     *            equality and hashing support)
     *
     * @throws SQLException
     *             if a database access error occurs
     *
     * @see DriverManager#registerDriver(Driver, DriverAction)
     */
    public static void registerDriver(Driver driver, DriverAction action, Object tag) throws SQLException {
        final DriverRegistration registration = new DriverRegistration(new DriverReference(driver), action, tag);

        synchronized (DRIVER_LIST) {
            if (DRIVER_MAP.putIfAbsent(registration.reference(), registration) == null) {
                DRIVER_GROUPS.compute(registration.group(), (k, g) -> {
                    final Set<DriverRegistration> s = (g != null) ? g : new LinkedHashSet<>();
                    s.add(registration);
                    return s;
                });

                DRIVER_LIST.add(registration);
            }
        }

        LOGGER.info("Registered driver: {}", registration);
    }

    /**
     * Retrieves those of the currently loaded JDBC drivers which were
     * registered with the given tag (for the first time).
     *
     * <p>
     * This method does not consult {@link java.sql.DriverManager}.
     *
     * @param tag
     *            the tag to search for
     *
     * @return the list of JDBC drivers
     *
     * @see DriverManager#getDrivers()
     */
    public static Collection<Driver> getDrivers(Object tag) {
        synchronized (DRIVER_LIST) {
            final Collection<DriverRegistration> drivers = DRIVER_GROUPS.get(tag);

            if (drivers == null) {
                return Collections.emptyList();
            }

            return drivers.stream().map(DriverRegistration::driver).collect(Collectors.toList());
        }
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
     *
     * @return a connection to the URL
     *
     * @throws SQLException
     *             if a database access error occurs or the URL is {@code null}
     * @throws SQLTimeoutException
     *             if the driver has determined that the timeout value specified
     *             by the {@link #setLoginTimeout} method has been exceeded and
     *             has at least tried to cancel the current database connection
     *             attempt
     *
     * @see java.sql.DriverManager#getConnection(String, Properties)
     */
    public static Connection getConnection(String url, Properties properties) throws SQLException {
        if (url == null) { // Keep this compatible with java.sql.DriverManager
            throw new SQLException("The url cannot be null", "08001");
        }

        LOGGER.debug("Connecting to URL: {}", url);

        final List<SQLException> failures = new ArrayList<>();
        for (DriverRegistration registration : DRIVER_LIST) {
            try {
                final Connection result = registration.driver().connect(url, properties);

                if (result != null) {
                    LOGGER.debug("Connection succeeeded for URL: {}", url);
                    return result;
                }
            } catch (SQLException e) {
                failures.add(e);
            }

            LOGGER.debug("Skipping driver: {}", registration);
        }

        try { // Revert to the system support
            return java.sql.DriverManager.getConnection(url, properties);
        } catch (SQLException e) {
            failures.forEach(e::addSuppressed);
            throw e;
        }
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
     *
     * @return a connection to the URL
     *
     * @throws SQLException
     *             if a database access error occurs or the URL is {@code null}
     * @throws SQLTimeoutException
     *             if the driver has determined that the timeout value specified
     *             by the {@link #setLoginTimeout} method has been exceeded and
     *             has at least tried to cancel the current database connection
     *             attempt
     *
     * @see java.sql.DriverManager#getConnection(String, String, String)
     */
    public static Connection getConnection(String url, String user, String password) throws SQLException {
        final Properties properties = new java.util.Properties();

        if (user != null) {
            properties.put("user", user);
        }

        if (password != null) {
            properties.put("password", password);
        }

        return getConnection(url, properties);
    }

    /**
     * Attempts to establish a connection to the given database URL.
     *
     * @param url
     *            a database URL
     *
     * @return a connection to the URL
     *
     * @throws SQLException
     *             if a database access error occurs or the URL is {@code null}
     * @throws SQLTimeoutException
     *             if the driver has determined that the timeout value specified
     *             by the {@link #setLoginTimeout} method has been exceeded and
     *             has at least tried to cancel the current database connection
     *             attempt
     *
     * @see java.sql.DriverManager#getConnection(String)
     */
    public static Connection getConnection(String url) throws SQLException {
        return getConnection(url, new Properties());
    }

    // Timeout handling

    /**
     * Sets the maximum time in seconds that a driver will wait while attempting
     * to connect to a database once the driver has been identified.
     *
     * @param seconds
     *            the lgin time limit in seconds; zero means there is no limit
     *
     * @see java.sql.DriverManager#getLoginTimeout()
     */
    public static void setLoginTimeout(int seconds) {
        java.sql.DriverManager.setLoginTimeout(seconds);
    }

    /**
     * Gets the maximum time in seconds that a driver can wait when attempting
     * to log in to a database.
     *
     * @return the driver login time limit in seconds
     *
     * @see java.sql.DriverManager#setLoginTimeout(int)
     */
    public static int getLoginTimeout() {
        return java.sql.DriverManager.getLoginTimeout();
    }

    // Logging support part

    /** Logger used for this class (only). */
    private static final Logger LOGGER = LoggerFactory.getLogger(DriverManager.class);

    /**
     * Retrieves the log writer.
     *
     * @return a {@link PrintWriter} object
     *
     * @see java.sql.DriverManager#setLogWriter(PrintWriter)
     */
    public static PrintWriter getLogWriter() {
        return java.sql.DriverManager.getLogWriter();
    }

    /**
     * Sets the logging/tracing {@link PrintWriter} object.
     *
     * @param out
     *            the new logging/tracing {@link PrintWriter} object;
     *            {@code null} to disable logging and tracing
     *
     * @see java.sql.DriverManager#getLogWriter()
     */
    public static void setLogWriter(PrintWriter out) {
        java.sql.DriverManager.setLogWriter(out);
    }

    /**
     * Sets the logging/tracing {@link PrintStream}.
     *
     * @param out
     *            the new logging/tracing {@link PrintStream}; to disable, set
     *            to {@code null}
     *
     * @deprecated Use {@link #setLogWriter}
     *
     * @see java.sql.DriverManager#setLogStream(PrintStream)
     */
    @Deprecated
    public static void setLogStream(PrintStream out) {
        // Do nothing as this method may disappear soon
    }

    /**
     * Retrieves the logging/tracing {@link PrintStream}.
     *
     * @return the logging/tracing {@link PrintStream}; if disabled, is
     *         {@code null}
     *
     * @deprecated Use {@code getLogWriter}
     *
     * @see java.sql.DriverManager#getLogStream()
     */
    @Deprecated
    public static PrintStream getLogStream() {
        return null; // Do nothing as this method may disappear soon
    }

    /**
     * Prints a message to the current JDBC log stream.
     *
     * @param message
     *            a log or tracing message
     */
    public static void println(String message) {
        java.sql.DriverManager.println(message);
    }
}

// Implementation support

/**
 * Reference to a driver.
 *
 * <p>
 * This class allows to avoid using the driver's {@link Object#equals(Object)}
 * and {@link Object#hashCode()} methods which might be misleading or harmful.
 * Two instances of this class are hence equal only if they refer to the same
 * driver instance.
 */
final class DriverReference implements Supplier<Driver> {

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
        return String.format("driver[className=%s]", driver.getClass().getTypeName());
    }

    /**
     * Returns the driver.
     *
     * @return the driver
     *
     * @see java.util.function.Supplier#get()
     */
    public Driver get() {
        return driver;
    }
}

/**
 * Registration record of a driver.
 */
final class DriverRegistration {

    /** Driver reference. */
    private final DriverReference reference;
    /** Driver action. */
    private final DriverAction action;
    /** Driver group. */
    private final Object group;

    /**
     * Creates a new instance.
     *
     * @param driverReference
     *            the driver reference. It must not be {@code null}.
     * @param driverAction
     *            the action to use
     * @param tag
     *            the tag for the group
     */
    public DriverRegistration(DriverReference driverReference, DriverAction driverAction, Object tag) {
        reference = Objects.requireNonNull(driverReference);
        action = NilDriverAction.ifNull(driverAction);
        group = tag;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return reference.toString();
    }

    /**
     * Returns the driver reference.
     *
     * @return the driver reference
     */
    public DriverReference reference() {
        return reference;
    }

    /**
     * Returns the driver object.
     *
     * @return the driver object
     */
    public Driver driver() {
        return reference.get();
    }

    /**
     * Returns the action of the driver.
     *
     * @return the action of the driver, never {@code null}
     */
    public DriverAction action() {
        return action;
    }

    /**
     * Returns the group of the driver.
     *
     * @return the group of the driver
     */
    public Object group() {
        return group;
    }
}

/**
 * Implements a no-operation {@link DriverAction}.
 */
final class NilDriverAction implements DriverAction {

    /** Sole instance of this class. */
    private static final DriverAction INSTANCE = new NilDriverAction();

    /**
     * Creates a new instance.
     */
    private NilDriverAction() {
        // Default constructor
    }

    /**
     * Returns an instance of nothing-doing action.
     *
     * @return an instance of nothing-doing action
     */
    public static DriverAction instance() {
        return INSTANCE;
    }

    /**
     * Returns an instance of nothing-doing action if the given argument is
     * {@code null}.
     *
     * @param driverAction
     *            the driver action to return if not {@code null}
     *
     * @return a driver action, never {@code null}
     */
    public static DriverAction ifNull(DriverAction driverAction) {
        return (driverAction != null) ? driverAction : instance();
    }

    /**
     * @see java.sql.DriverAction#deregister()
     */
    public void deregister() {
        // Do nothing
    }
}
