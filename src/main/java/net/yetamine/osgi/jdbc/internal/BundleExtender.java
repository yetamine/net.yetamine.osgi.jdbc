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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.sql.Driver;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.util.tracker.BundleTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.yetamine.osgi.jdbc.tweak.BundleControl;

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
final class BundleExtender implements AutoCloseable {

    static final Logger LOGGER = LoggerFactory.getLogger(BundleExtender.class);

    /** Control tracker to provide a unified control. */
    private final BundleControlTracker controlTracker;
    /** Bundle tracker (the core of the extender). */
    private final Tracker bundleTracker;

    /**
     * Creates a new instance.
     *
     * @param bundleContext
     *            the bundle context for which this service should run. It must
     *            not be {@code null}.
     * @param bundleController
     *            the bundle controller to manage bundle states. It must not be
     *            {@code null}.
     */
    public BundleExtender(BundleContext bundleContext, BundleController bundleController) {
        controlTracker = new BundleControlTracker(bundleContext);
        bundleTracker = new Tracker(bundleContext, bundleController, controlTracker);
    }

    /**
     * Makes this instance operational if not operational yet.
     */
    public synchronized void open() {
        LOGGER.debug("Opening extender.");
        controlTracker.open();
        bundleTracker.open();
    }

    /**
     * Closes all operations related to this instance, putting it out of
     * service.
     *
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public synchronized void close() {
        LOGGER.debug("Closing extender.");
        bundleTracker.close();
        controlTracker.close();
    }

    /**
     * A tracker implementing the core of the extender.
     */
    private static final class Tracker extends BundleTracker<Context> {

        /** Controller for resuming and suspending bundles. */
        private final BundleController controller;
        /** Control for querying bundle options. */
        private final BundleControl control;

        /**
         * Creates a new instance.
         *
         * @param bundleContext
         *            the context of this bundle. It must not be {@code null}.
         * @param bundleController
         *            the controller for resuming and suspending bundles. It
         *            must not be {@code null}.
         * @param bundleControl
         *            the control for querying bundle options. It must not be
         *            {@code null}.
         */
        public Tracker(BundleContext bundleContext, BundleController bundleController, BundleControl bundleControl) {
            super(bundleContext, ~(Bundle.INSTALLED | Bundle.UNINSTALLED), null);
            controller = Objects.requireNonNull(bundleController);
            control = Objects.requireNonNull(bundleControl);
        }

        /**
         * @see org.osgi.util.tracker.BundleTracker#addingBundle(org.osgi.framework.Bundle,
         *      org.osgi.framework.BundleEvent)
         */
        @Override
        public Context addingBundle(Bundle bundle, BundleEvent event) {
            final Set<String> drivers = discover(bundle);
            final BundleControl.Options options = new BundleControlOptions(bundle, drivers);
            control.adjust(options); // Adjust the options with the bundle control services

            final Set<String> loadable = options.loadableDrivers().stream()     // Make a clean copy
                    .filter(drivers::contains)                                  // Limit to the subset
                    .collect(Collectors.collectingAndThen(                      // Make an optimized set
                            Collectors.toCollection(HashSet::new),              // Make a mutable set
                            c -> c.isEmpty() ? Collections.emptySet() : c)      // But replace it with the empty singleton if empty
            );

            final BundleControl.Condition condition = options.driversAvailable();
            final Context result = new Context(controller, bundle, loadable, condition);
            result.update();
            return result;
        }

        /**
         * @see org.osgi.util.tracker.BundleTracker#modifiedBundle(org.osgi.framework.Bundle,
         *      org.osgi.framework.BundleEvent, java.lang.Object)
         */
        @Override
        public void modifiedBundle(Bundle bundle, BundleEvent event, Context extenderContext) {
            extenderContext.update();
        }

        /**
         * @see org.osgi.util.tracker.BundleTracker#removedBundle(org.osgi.framework.Bundle,
         *      org.osgi.framework.BundleEvent, java.lang.Object)
         */
        @Override
        public void removedBundle(Bundle bundle, BundleEvent event, Context extenderContext) {
            controller.cancel(bundle);
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
        private static Set<String> discover(Bundle bundle) {
            LOGGER.debug("Discovering drivers from '{}'.", bundle);
            final URL url = AccessController.doPrivileged((PrivilegedAction<URL>) () -> {
                return bundle.getResource("/META-INF/services/java.sql.Driver");
            });

            if (url == null) { // Missing the descriptor completely
                LOGGER.debug("No drivers declared in '{}'.", bundle);
                return Collections.emptySet();
            }

            final Set<String> result = new HashSet<>();
            final Charset cs = StandardCharsets.UTF_8;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream(), cs))) {
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
    }

    /**
     * Represents the bundle extender context.
     */
    private static final class Context implements AutoCloseable {

        /** Extended bundle. */
        private final Bundle bundle;
        /** Bundle controller to use. */
        private final BundleController controller;
        /** List of drivers awaiting the condition. */
        private final Collection<String> pending;
        /** Condition for triggering the update. */
        private final Predicate<? super Bundle> condition;

        /**
         * Creates a new instance.
         *
         * @param bundleController
         *            the bundle controller to use for managing the bundle's
         *            drivers. It must not be {@code null}.
         * @param extendedBundle
         *            the bundle to extend. It must not be {@code null}.
         * @param driverNames
         *            the set of driver names. It must be an instance that
         *            allows shrinking (removing elements) until empty and
         *            possibly even immutable if empty. The caller thereby
         *            passes the ownership to the new instance (when the
         *            instance is mutable).
         * @param extenderCondition
         *            the condition for registering drivers as services. It must
         *            not be {@code null}.
         */
        public Context(BundleController bundleController, Bundle extendedBundle, Set<String> driverNames, Predicate<? super Bundle> extenderCondition) {
            controller = Objects.requireNonNull(bundleController);
            condition = Objects.requireNonNull(extenderCondition);
            bundle = Objects.requireNonNull(extendedBundle);
            pending = Objects.requireNonNull(driverNames);
        }

        /**
         * Updates the context by triggering load of remaining drivers.
         */
        public void update() {
            if (!condition.test(bundle)) {
                controller.suspend(bundle);
                return;
            }

            controller.resume(bundle);
            for (Iterator<String> it = pending.iterator(); it.hasNext();) {
                final String driver = it.next();
                try { // Loading the driver
                    if (load(driver) != null) {
                        it.remove();
                    }
                } catch (Exception e) {
                    LOGGER.warn("Loading driver '{}' from '{}' failed.", driver, bundle, e);
                }
            }
        }

        /**
         * @see java.lang.AutoCloseable#close()
         */
        @Override
        public void close() {
            controller.cancel(bundle);
        }

        /**
         * Loads a driver service class.
         *
         * @param driver
         *            the driver name. It must be a valid driver service class
         *            name.
         *
         * @return the service class, or {@code null} if wiring to the bundle
         *         could not be retrieved
         *
         * @throws Exception
         *             if the wiring exists, but the class loading failed
         */
        private Class<?> load(String driver) throws Exception {
            return AccessController.doPrivileged((PrivilegedExceptionAction<Class<?>>) () -> {
                final BundleWiring wiring = bundle.adapt(BundleWiring.class);
                if (wiring == null) { // Maybe a fragment, or simply not allowed to get the wiring
                    LOGGER.warn("Skipped driver '{}' from '{}' due to missing wiring.", driver, bundle);
                    return null;
                }

                final Class<?> result = bundle.loadClass(driver);
                Class.forName(result.getName(), true, result.getClassLoader());
                LOGGER.info("Loaded driver '{}' from '{}'.", driver, bundle);
                return result;
            });
        }
    }
}
