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
import java.sql.Driver;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.util.tracker.BundleTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the extender that monitors bundle loading and triggers loading
 * {@link Driver} implementations published using the {@link ServiceLoader}
 * extensions.
 *
 * <p>
 * A driver shall be registered by loading its service class; the class should
 * register the driver itself, which unfortunately does not allow reloading an
 * unregistered driver. Therefore all registered drivers are kept registered as
 * long as their registering bundle remains resolved.
 */
final class DriverLoading implements AutoCloseable {

    /**
     * Defines the action(s) to be taken on a driver bundle event that shall
     * trigger driver loading or unloading.
     */
    public interface Action {

        /**
         * Requests loading the given driver from the specified bundle.
         *
         * @param bundle
         *            the bundle to load the drivers from. It must not be
         *            {@code null}.
         * @param driver
         *            the names of the driver to load. It must not be
         *            {@code null}.
         *
         * @return {@code false} if the driver loading could not complete and
         *         shall be retried on the next occasion in the future (e.g.,
         *         when the bundle state changes), or {@code true} if the
         *         operation should not repeated for this driver
         *
         * @throws Exception
         *             if the operation failed and should not be retried
         */
        boolean load(Bundle bundle, String driver) throws Exception;

        /**
         * Requests unloading all drivers related to the bundle.
         *
         * @param bundle
         *            the bundle to be unloaded. It must not be {@code null}.
         */
        void unload(Bundle bundle);
    }

    static final Logger LOGGER = LoggerFactory.getLogger(DriverLoading.class);

    /** Filter tracker to provide a unified filter. */
    final LoadingFilterTracker filterTracker;
    /** Action to take on an event. */
    final Action action;

    /** Bundle context for registering the service and tracker. */
    private final BundleContext serviceContext;
    /** Bundle tracking and listening support. */
    private final DriverBundleTracker bundleTracker;

    /**
     * Creates a new instance.
     *
     * @param bundleContext
     *            the bundle context for which this service should run. It must
     *            not be {@code null}.
     * @param actionHandler
     *            the handler to take the action on an event. It must not be
     *            {@code null}.
     */
    public DriverLoading(BundleContext bundleContext, Action actionHandler) {
        action = Objects.requireNonNull(actionHandler);
        serviceContext = Objects.requireNonNull(bundleContext);
        bundleTracker = new DriverBundleTracker(serviceContext);
        filterTracker = new LoadingFilterTracker(serviceContext);
    }

    /**
     * Makes this instance operational if not operational yet.
     */
    public synchronized void open() {
        LOGGER.debug("Opening driver loading service.");
        filterTracker.open();
        bundleTracker.open();
    }

    /**
     * Closes all operations related to this instance, putting it out of
     * service.
     *
     * @see java.lang.AutoCloseable#close()
     */
    public synchronized void close() {
        LOGGER.debug("Closing driver loading service.");
        bundleTracker.close();
        filterTracker.close();
    }

    /**
     * Discovers drivers published by a bundle.
     *
     * @param bundle
     *            the bundle to inspect. It must not be {@code null}.
     *
     * @return a set of found driver names; the result may be an immutable
     *         instance if empty, otherwise it must be a mutable instance
     */
    static Set<String> discover(Bundle bundle) {
        LOGGER.debug("Discovering drivers from '{}'.", bundle);
        final URL url = AccessController.doPrivileged((PrivilegedAction<URL>) () -> {
            return bundle.getResource("/META-INF/services/java.sql.Driver");
        });

        if (url == null) { // Missing the descriptor completely
            LOGGER.debug("No drivers declared in '{}'.", bundle);
            return Collections.emptySet();
        }

        final BundleWiring wiring = AccessController.doPrivileged((PrivilegedAction<BundleWiring>) () -> {
            return bundle.adapt(BundleWiring.class);
        });

        if (wiring == null) { // Maybe a fragment, or simply not allowed to get the wiring
            LOGGER.debug("Skipped '{}' due to unavailable BundleWiring.", bundle);
            return Collections.emptySet();
        }

        final Set<String> result = new HashSet<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            r.lines().map(String::trim).forEach(result::add);
        } catch (IOException e) {
            LOGGER.warn("Failed to load the driver list from '{}'.", bundle, e);
        }

        final int size = result.size();

        if (size > 0) { // There are some drivers to return after all, return the mutable set
            LOGGER.debug("Discovered {} driver(s) in '{}': {}", size, bundle, result);
            return result;
        }

        LOGGER.debug("Discovered no drivers in '{}'.", bundle);
        return Collections.emptySet();
    }

    /**
     * Represents the bundle extender context.
     */
    private final class ExtenderContext implements AutoCloseable {

        /** Extended bundle. */
        private final Bundle bundle;
        /** List of pending drivers. */
        private final Collection<String> pending;

        /**
         * Creates a new instance.
         *
         * @param extendedBundle
         *            the bundle to extend. It must not be {@code null}.
         * @param driverNames
         *            the set of driver names. It must be an instance that
         *            allows shrinking (removing elements) until empty and
         *            possibly even immutable if empty. The caller thereby
         *            passes the ownership to the new instance (when the
         *            instance is mutable).
         */
        public ExtenderContext(Bundle extendedBundle, Set<String> driverNames) {
            bundle = Objects.requireNonNull(extendedBundle);
            pending = Objects.requireNonNull(driverNames);
        }

        /**
         * Creates a new instance.
         *
         * @param extendedBundle
         *            the bundle to extend. It must not be {@code null}.
         */
        public ExtenderContext(Bundle extendedBundle) {
            this(extendedBundle, discover(extendedBundle));
        }

        /**
         * Updates the context by triggering load of remaining drivers.
         */
        public synchronized void update() {
            if (pending.isEmpty()) {
                return;
            }

            for (Iterator<String> it = pending.iterator(); it.hasNext();) {
                final String driver = it.next();

                try {
                    if (!filterTracker.loadable(bundle, driver)) { // Let filters vote at first
                        LOGGER.debug("Skipped JDBC driver '{}' from '{}' due to filtering.", driver, bundle);
                        continue;
                    }

                    if (!action.load(bundle, driver)) {
                        // Loading the driver didn't complete, but it shall not be
                        // removed from the pending set, since it might be retried
                        continue;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to load JDBC driver '{}' from '{}'.", driver, bundle, e);
                }

                it.remove(); // Done with this driver for ever
            }
        }

        /**
         * @see java.lang.AutoCloseable#close()
         */
        public void close() {
            action.unload(bundle);
        }
    }

    /**
     * A tracker to monitor driver bundles.
     */
    private final class DriverBundleTracker extends BundleTracker<ExtenderContext> {

        /**
         * Creates a new instance.
         *
         * @param bundleContext
         *            the context of this bundle. It must not be {@code null}.
         */
        public DriverBundleTracker(BundleContext bundleContext) {
            super(bundleContext, Bundle.RESOLVED | Bundle.STARTING | Bundle.ACTIVE | Bundle.STOPPING, null);
        }

        /**
         * @see org.osgi.util.tracker.BundleTracker#addingBundle(org.osgi.framework.Bundle,
         *      org.osgi.framework.BundleEvent)
         */
        @Override
        public ExtenderContext addingBundle(Bundle bundle, BundleEvent event) {
            final ExtenderContext result = new ExtenderContext(bundle);
            result.update();
            return result;
        }

        /**
         * @see org.osgi.util.tracker.BundleTracker#modifiedBundle(org.osgi.framework.Bundle,
         *      org.osgi.framework.BundleEvent, java.lang.Object)
         */
        @Override
        public void modifiedBundle(Bundle bundle, BundleEvent event, ExtenderContext extenderContext) {
            extenderContext.update();
        }

        /**
         * @see org.osgi.util.tracker.BundleTracker#removedBundle(org.osgi.framework.Bundle,
         *      org.osgi.framework.BundleEvent, java.lang.Object)
         */
        @Override
        public void removedBundle(Bundle bundle, BundleEvent event, ExtenderContext extenderContext) {
            extenderContext.close();
        }
    }
}
