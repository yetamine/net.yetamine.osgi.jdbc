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

package net.yetamine.osgi.jdbc.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.sql.Driver;
import java.util.Collection;

import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.yetamine.osgi.jdbc.DriverManager;

/**
 * A loader of JDBC drivers.
 */
final class JdbcDriverLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcDriverLoader.class);

    /**
     * Examines the given bundle and loads all its drivers.
     *
     * @param bundle
     *            the bundle to examine. It must not be {@code null}.
     *
     * @return the number of drivers loaded from the given bundle successfully
     */
    public static int loadDrivers(Bundle bundle) {
        LOGGER.debug("Loading JDBC drivers from bundle '{}'.", bundle);
        final URL url = AccessController.doPrivileged((PrivilegedAction<URL>) () -> {
            return bundle.getResource("/META-INF/services/java.sql.Driver");
        });

        if (url == null) { // Missing the descriptor completely
            LOGGER.debug("No JDBC drivers found in bundle '{}'.", bundle);
            return 0;
        }

        final BundleWiring wiring = AccessController.doPrivileged((PrivilegedAction<BundleWiring>) () -> {
            return bundle.adapt(BundleWiring.class);
        });

        if (wiring == null) { // Maybe a fragment, or simply not allowed to get the wiring
            LOGGER.debug("No BundleWiring available for bundle '{}'. Bundle skipped.", bundle);
            return 0;
        }

        int result = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null;) {
                final String name = line.trim();
                if (name.isEmpty()) {
                    continue;
                }

                try {
                    AccessController.doPrivileged((PrivilegedExceptionAction<Class<?>>) () -> {
                        final Class<?> clazz = bundle.loadClass(name); // Ensure the class loads
                        // Force initialization (OSGi does not trigger initializers)
                        Class.forName(clazz.getName(), true, clazz.getClassLoader());
                        return clazz;
                    });

                    ++result; // One more bundle loaded
                    LOGGER.info("Loaded JDBC driver '{}' from bundle '{}'.", name, bundle);
                } catch (Exception e) {
                    LOGGER.error("Failed to load JDBC driver '{}' from bundle '{}'.", name, bundle, e);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load the JDBC driver list for bundle '{}'.", bundle, e);
        }

        LOGGER.debug("Loaded {} driver(s) from bundle '{}'.", result, bundle);
        return result;
    }

    /**
     * Deregisters all drivers beloging to the group.
     *
     * @param bundle
     *            the bundle to report as the group origin. It must not be
     *            {@code null}.
     * @param group
     *            the driver group to unregister. It must not be {@code null}.
     */
    public static void unloadDrivers(Bundle bundle, Object group) {
        final Collection<Driver> drivers = DriverManager.getDrivers(group);
        if (drivers.isEmpty()) {
            return;
        }

        LOGGER.debug("Unloading drivers from bundle '{}'.", bundle);

        drivers.forEach(driver -> {
            try { // Make this safe, try all the drivers
                DriverManager.deregisterDriver(driver);
                LOGGER.debug("Unloaded driver '{}' from bundle '{}'.", driver, bundle);
            } catch (Exception e) {
                LOGGER.warn("Could not unload driver '{}' from bundle '{}'.", driver, bundle, e);
            }
        });

        LOGGER.debug("Finished unloading drivers from bundle '{}'.", bundle);
    }

    private JdbcDriverLoader() {
        throw new AssertionError();
    }
}
