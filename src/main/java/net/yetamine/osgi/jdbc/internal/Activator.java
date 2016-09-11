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
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activates this bundle.
 */
public final class Activator implements BundleActivator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    /** Weaving hook for redirecting to surrogate DriverManager. */
    private ServiceRegistration<?> weavingHook;
    /** Bundle tracker to use. */
    private JdbcDriverTracker tracker;

    /**
     * Creates a new instance.
     */
    public Activator() {
        // Default constructor
    }

    /**
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    public synchronized void start(BundleContext context) throws Exception {
        LOGGER.info("Activating JDBC support.");
        weavingHook = context.registerService(WeavingHook.class, new JdbcWeavingHook(context), null);
        tracker = new JdbcDriverTracker(context);
        tracker.open();
        LOGGER.info("JDBC support active.");
    }

    /**
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public synchronized void stop(BundleContext context) throws Exception {
        LOGGER.info("Deactivating JDBC support.");
        weavingHook.unregister();
        tracker.close();
        LOGGER.info("JDBC support inactive.");
    }
}
