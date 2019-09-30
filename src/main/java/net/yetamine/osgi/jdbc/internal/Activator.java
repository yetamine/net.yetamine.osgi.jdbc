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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.sql.DriverManager;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.yetamine.osgi.jdbc.DriverProvider;
import net.yetamine.osgi.jdbc.DriverSequence;
import net.yetamine.osgi.jdbc.support.DriverManagerAdapter;
import net.yetamine.osgi.jdbc.support.DriverReference;

/**
 * Activates this bundle.
 */
public final class Activator implements BundleActivator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    /** Common driver registrar, used by the thunk. */
    private static final DriverRegistrar REGISTRAR = new DriverRegistrar();
    /** Common driver provider for the thunk, published as a service too. */
    private static volatile DriverProvider provider = DriverManagerAdapter.instance();
    /** Common supportive executor for running the weaving-related tasks. */
    private static volatile Executor executor = Runnable::run;

    /** Weaving hook service. */
    private DriverWeaving weavingService;
    /** Bundle extender implementation. */
    private BundleExtender bundleExtender;
    /** Driver tracking and providing service. */
    private DriverTracking providingService;
    /** Executor service to manage. */
    private ExecutorService executorService;

    /**
     * Creates a new instance.
     */
    public Activator() {
        // Default constructor
    }

    /**
     * Returns the current driver provider available for the thunk.
     *
     * @return the current driver provider
     */
    public static DriverProvider provider() {
        return provider;
    }

    /**
     * Returns the driver registrar available for the thunk.
     *
     * @return the driver registrar
     */
    public static DriverRegistrar registrar() {
        return REGISTRAR;
    }

    /**
     * Returns the current executor.
     *
     * @return the current executor
     */
    public static Executor executor() {
        return executor;
    }

    /**
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    public synchronized void start(BundleContext context) throws Exception {
        LOGGER.info("Activating JDBC support.");

        initializeDriverManager();

        bundleExtender = new BundleExtender(context, registrar());
        providingService = new DriverTracking(context);
        weavingService = new DriverWeaving(context);

        executorService = Executors.newSingleThreadExecutor(r -> {
            final Thread thread = new Thread(r, "net.yetamine.osgi.jdbc/WeavingSupport");
            thread.setDaemon(true);
            return thread;
        });

        executor = executorService::execute;   // Bind the execution method to avoid casting to ExecutorService

        weavingService.open();      // Firstly, start weaving, so the all loaded drivers could be woven
        bundleExtender.open();      // After weaving hook is ready, loading may start
        providingService.open();    // Finally, when some drivers are on the way, allow publishing them

        REGISTRAR.bind(context); // OK, wire it
        provider = providingService.provider();

        LOGGER.info("Activated JDBC support.");
    }

    /**
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public synchronized void stop(BundleContext context) throws Exception {
        LOGGER.info("Deactivating JDBC support.");

        provider = DriverManagerAdapter.instance();
        REGISTRAR.release();

        providingService.close();   // Firstly, stop the provider to let its dependencies to shut down as soon as possible
        bundleExtender.close();     // Then ensure no more drivers could be loaded or published
        weavingService.close();     // Then it is possible to switch off the weaving

        executor = Runnable::run;
        executorService.shutdown();

        LOGGER.info("Deactivated JDBC support.");
    }

    /**
     * Forces {@link DriverManager} initialization, so that it does load initial
     * drivers in a clean context (hopefully). This is the best-effort way to
     * mitigate the shadows of the TCCL.
     */
    private static void initializeDriverManager() {
        final DriverSequence drivers;

        final ClassLoader current = Thread.currentThread().getContextClassLoader();
        try { // Trigger loading DriverManager and fetch the available drivers
            drivers = AccessController.doPrivileged((PrivilegedAction<DriverSequence>) () -> {
                Thread.currentThread().setContextClassLoader(null);
                return DriverManagerAdapter.instance().drivers();
            });
        } finally {
            Thread.currentThread().setContextClassLoader(current);
        }

        // Reuse the nice DriverReference::toString (and lazy formatting)
        drivers.forEach(driver -> LOGGER.info("Found bootstrap driver {}", new DriverReference(driver)));
    }
}
