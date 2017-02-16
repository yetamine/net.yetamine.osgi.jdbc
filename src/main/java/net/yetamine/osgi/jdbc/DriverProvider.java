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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * An interface for providing driver services to consumers; this interface
 * essentially mimics the part of {@link DriverManager}'s interface, which
 * offers these services and implementations should behave in a similar way.
 */
public interface DriverProvider {

    /**
     * Lists all drivers currently available through this instance in the order
     * of their preference.
     *
     * @return all drivers currently available
     */
    DriverSequence drivers();

    /**
     * Finds a driver that can accept the given URL.
     *
     * <p>
     * The default implementation iterates through {@link #drivers()} to find
     * the first one that accepts the given URL and returns the driver. If no
     * driver accepts the URL, it throws an {@link SQLException}.
     *
     * @param url
     *            the URL that the returned driver should accept
     *
     * @return the driver that can accept the given URL
     *
     * @throws SQLException
     *             if a database access error occurs or no driver is available
     */
    default Driver driver(String url) throws SQLException {
        for (Driver driver : drivers()) {
            if (driver.acceptsURL(url)) {
                return driver;
            }
        }

        throw new SQLException(String.format("No suitable driver found for '%s'.", url), "08001");
    }

    /**
     * Makes a connection for the given URL.
     *
     * <p>
     * The default implementation converts the properties from the {@link Map}
     * representation to a {@link Properties} instance if not {@code null} and
     * invokes {@link #connection(String, Properties)}.
     *
     * @param url
     *            the URL for the connection
     * @param properties
     *            the connection properties to pass to the driver. This should
     *            usually contain at least <i>user</i> and <i>password</i>
     *            entries.
     *
     * @return the connection
     *
     * @throws SQLException
     *             if a database access error occurs or no driver is available
     */
    default Connection connection(String url, Map<?, ?> properties) throws SQLException {
        if (properties == null) { // Just redirect with no properties
            return connection(url, (Properties) null);
        }

        final Properties configuration = new Properties();
        properties.forEach(configuration::put);
        return connection(url, configuration);
    }

    /**
     * Makes a connection for the given URL.
     *
     * <p>
     * The default implementation iterates through {@link #drivers()} to find
     * the first one that returns a connection for the given URL. If no driver
     * returns a valid connection, it throws an {@link SQLException}.
     *
     * @param url
     *            the URL for the connection. It must not be {@code null}.
     * @param properties
     *            the connection properties to pass to the driver. This should
     *            usually contain at least <i>user</i> and <i>password</i>
     *            entries.
     *
     * @return the connection
     *
     * @throws SQLException
     *             if a database access error occurs or no driver is available
     */
    default Connection connection(String url, Properties properties) throws SQLException {
        if (url == null) { // Keep this compatible with java.sql.DriverManager
            throw new SQLException("The URL must not be null.", "08001");
        }

        List<DriverFailure> failures = null;

        for (Driver driver : drivers()) {
            try { // Attempt to get a connection, but don't throw until failing completely
                final Connection result = driver.connect(url, properties);
                if (result != null) {
                    return result;
                }
            } catch (SQLException e) {
                if (failures == null) {
                    failures = new ArrayList<>();
                }

                failures.add(new DriverFailure(driver, e));
            }
        }

        if (failures == null) {
            throw new SQLException(String.format("No driver found for connecting to '%s'.", url), "08001");
        }

        final String list = failures.stream().map(DriverFailure::toString).collect(Collectors.joining(", "));
        final String f = "All suitable drivers failed to connect to '%s'; tried %s.";
        final SQLException e = new SQLException(String.format(f, url, list), "08001");

        if (failures.size() == 1) {
            // If there is a single failure, let's make the stack trace more
            // traditional (and likely to contain the problem)
            e.initCause(failures.get(0).throwable());
        } else {
            // Otherwise add all the exceptions, but do not prefer some as the
            // cause, because it is not quite certain which driver is responsible
            failures.forEach(failure -> e.addSuppressed(failure.throwable()));
        }

        throw e;
    }
}

/**
 * A description of a failure.
 */
final class DriverFailure {

    /** Exception describing the failure. */
    private final Throwable throwable;
    /** Failing driver. */
    private final Driver driver;

    /**
     * Creates a new instance.
     *
     * @param d
     *            the driver. It must not be {@code null}.
     * @param t
     *            the exception. It must not be {@code null}.
     */
    public DriverFailure(Driver d, Throwable t) {
        throwable = Objects.requireNonNull(t);
        driver = Objects.requireNonNull(d);
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        // @formatter:off
        return String.format("%s@%d.%d",
                driver.getClass().getTypeName(),
                driver.getMajorVersion(),
                driver.getMinorVersion()
            );
        // @formatter:on
    }

    /**
     * Returns the exception that describes the failure.
     *
     * @return the exception that describes the failure
     */
    public Throwable throwable() {
        return throwable;
    }

    /**
     * Returns the driver that failed.
     *
     * @return the driver that failed
     */
    public Driver driver() {
        return driver;
    }
}
