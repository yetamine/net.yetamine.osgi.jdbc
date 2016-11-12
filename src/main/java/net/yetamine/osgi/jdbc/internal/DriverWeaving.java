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

import java.util.Objects;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates all components that handle class weaving.
 */
final class DriverWeaving implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DriverWeaving.class);

    /** Bundle context for registering the service and tracker. */
    private final BundleContext serviceContext;
    /** Filter tracker to provide a unified filter. */
    private final WeavingFilterTracker filterTracker;
    /** Registration of the weaving hook. */
    private ServiceRegistration<?> service;

    /**
     * Creates a new instance.
     *
     * @param bundleContext
     *            the bundle context for which this service should run. It must
     *            not be {@code null}.
     */
    public DriverWeaving(BundleContext bundleContext) {
        serviceContext = Objects.requireNonNull(bundleContext);
        filterTracker = new WeavingFilterTracker(serviceContext);
    }

    /**
     * Makes this instance operational if not operational yet.
     */
    public synchronized void open() {
        if (service != null) {
            return;
        }

        LOGGER.debug("Opening weaving service.");
        filterTracker.open(); // Start the tracking before the service starts weaving!
        final WeavingHook hook = new WeavingHookService(serviceContext, filterTracker);
        service = serviceContext.registerService(WeavingHook.class, hook, null);
    }

    /**
     * Closes all operations related to this instance, putting it out of
     * service.
     *
     * @see java.lang.AutoCloseable#close()
     */
    public synchronized void close() {
        if (service == null) {
            return;
        }

        LOGGER.debug("Closing weaving service.");
        service.unregister();
        service = null;

        filterTracker.close(); // Stop the tracking after unregistering the weaving hook!
    }
}
