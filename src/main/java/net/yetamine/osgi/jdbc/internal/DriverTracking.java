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

import java.sql.Driver;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Stream;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.yetamine.osgi.jdbc.DriverProvider;
import net.yetamine.osgi.jdbc.DriverSequence;
import net.yetamine.osgi.jdbc.support.DriverManagerAdapter;
import net.yetamine.osgi.jdbc.support.DriverProviderChain;

/**
 * Tracks available {@link Driver} services, aggregates them and registers as a
 * single {@link DriverProvider} service (available as {@link #provider()} too).
 */
final class DriverTracking implements AutoCloseable {

    static final Logger LOGGER = LoggerFactory.getLogger(DriverTracking.class);

    /** Bundle context for registering the service and tracker. */
    private final BundleContext serviceContext;
    /** Actual service core tracking the drivers. */
    private final Tracker serviceTracker;
    /** Public {@link DriverProvider} instance. */
    private final DriverProvider provider;
    /** Registration of {@link #provider}. */
    private ServiceRegistration<?> service;

    /**
     * Creates a new instance.
     *
     * @param bundleContext
     *            the bundle context for which this service should run. It must
     *            not be {@code null}.
     */
    public DriverTracking(BundleContext bundleContext) {
        serviceContext = Objects.requireNonNull(bundleContext);
        serviceTracker = new Tracker(serviceContext);
        provider = DriverProviderChain.of(serviceTracker, DriverManagerAdapter.instance());
    }

    /**
     * Makes this instance operational if not operational yet.
     */
    public synchronized void open() {
        if (service != null) {
            return;
        }

        LOGGER.debug("Opening driver tracking service.");
        service = serviceContext.registerService(DriverProvider.class, provider, null);
        serviceTracker.open();
    }

    /**
     * Closes all operations related to this instance, putting it out of
     * service.
     *
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public synchronized void close() {
        if (service == null) {
            return;
        }

        LOGGER.debug("Closing driver tracking service.");
        serviceTracker.close();
        service.unregister();
        service = null;
    }

    /**
     * Returns the public interface of this service (which shall be registered
     * as the OSGi service by {@link #open()}).
     *
     * @return the public interface of this service
     */
    public DriverProvider provider() {
        return provider;
    }

    /**
     * A {@link DriverProvider} implementation tracking available {@link Driver}
     * services in order to provide them to its own clients that want to use the
     * drivers collectively.
     */
    private static final class Tracker extends ServiceTracker<Driver, Driver> implements DriverSequence, DriverProvider {

        /* Implementation notes:
         *
         * We need a fast lock-free sequence of the drivers in the order of
         * their preference, but using the ServiceTracker's support would not
         * satisfy our requirements well enough. So we rather use an own way.
         */

        /** Drivers available for the provider to offer. */
        private final OrderedSequence<ServiceReference<Driver>, Driver> drivers = new OrderedSequence<>(
                Comparator.comparing(OrderedSequence.Item::key) // Use the usual service ordering
        );

        /**
         * Creates a new instance.
         *
         * @param bundleContext
         *            the bundle context to use. It must not be {@code null}.
         */
        public Tracker(BundleContext bundleContext) {
            super(bundleContext, Driver.class, null);
        }

        // DriverProvider

        /**
         * @see net.yetamine.osgi.jdbc.DriverProvider#drivers()
         */
        @Override
        public DriverSequence drivers() {
            return this;
        }

        // DriverSequence

        /**
         * @see net.yetamine.osgi.jdbc.DriverSequence#iterator()
         */
        @Override
        public Iterator<Driver> iterator() {
            return drivers.values().iterator();
        }

        /**
         * @see net.yetamine.osgi.jdbc.DriverSequence#stream()
         */
        @Override
        public Stream<Driver> stream() {
            return drivers.values().stream();
        }

        // Tracking methods

        /**
         * @see org.osgi.util.tracker.ServiceTracker#addingService(org.osgi.framework.ServiceReference)
         */
        @Override
        public Driver addingService(ServiceReference<Driver> reference) {
            final Driver result = context.getService(reference);
            LOGGER.debug("Adding driver service '{}'.", result);
            drivers.add(reference, result);
            return result;
        }

        /**
         * @see org.osgi.util.tracker.ServiceTracker#modifiedService(org.osgi.framework.ServiceReference,
         *      java.lang.Object)
         */
        @Override
        public void modifiedService(ServiceReference<Driver> reference, Driver service) {
            LOGGER.debug("Updating driver service '{}'.", service);
            drivers.set(reference, service);
        }

        /**
         * @see org.osgi.util.tracker.ServiceTracker#removedService(org.osgi.framework.ServiceReference,
         *      java.lang.Object)
         */
        @Override
        public void removedService(ServiceReference<Driver> reference, Driver service) {
            LOGGER.debug("Removing driver service '{}'.", service);
            drivers.remove(reference);
            context.ungetService(reference);
        }
    }
}
