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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.yetamine.osgi.jdbc.DriverProvider;
import net.yetamine.osgi.jdbc.support.DriverManagerAdapter;

/**
 * Activates this bundle.
 */
public final class Activator implements BundleActivator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    /** Common driver registrar, used by the thunk. */
    private static final DriverRegistrar REGISTRAR = new DriverRegistrar();
    /** Common driver provider for the thunk, published as a service too. */
    private static volatile DriverProvider PROVIDER = DriverManagerAdapter.instance();

    /** Driver providing service. */
    private DriverTracking providingService;
    /** Driver loading service. */
    private DriverLoading loadingService;
    /** Weaving hook service. */
    private DriverWeaving weavingService;

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
        return PROVIDER;
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
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    public synchronized void start(BundleContext context) throws Exception {
        LOGGER.info("Activating JDBC support.");

        providingService = new DriverTracking(context);
        weavingService = new DriverWeaving(context);
        loadingService = new DriverLoading(context, DriverMediator.instance());

        weavingService.open();      // Firstly, start weaving, so the all loaded drivers could be woven
        loadingService.open();      // After weaving hook is ready, loading may start
        providingService.open();    // Finally, when some drivers are on the way, allow publishing them

        // All successful, wire it
        REGISTRAR.bind(context);
        PROVIDER = providingService.provider();

        LOGGER.info("Activated JDBC support.");
    }

    /**
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public synchronized void stop(BundleContext context) throws Exception {
        LOGGER.info("Deactivating JDBC support.");

        PROVIDER = DriverManagerAdapter.instance();
        REGISTRAR.release();

        providingService.close();   // Firstly, stop the provider to let its dependencies to shut down as soon as possible
        loadingService.close();     // Then ensure no more drivers could be loaded
        weavingService.close();     // Then it is possible to switch off the weaving

        LOGGER.info("Deactivated JDBC support.");
    }
}
