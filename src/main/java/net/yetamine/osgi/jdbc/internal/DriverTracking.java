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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
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
    private final DriverTracker serviceTracker;
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
        serviceTracker = new DriverTracker(serviceContext);
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
     * Represents a driver publication as a service with the given ranking, as
     * seen by the tracker, that determines the preference of the driver, when
     * drivers are being chosen.
     *
     * <p>
     * This class requires safe publication and both reading and writing the
     * ranking needs appropriate protection. This implies that any use of
     * {@link #ordering()} needs the protection as well, since it employs
     * rankings of the compared instances.
     */
    private static final class DriverService {

        /** Driver instance. */
        private final Driver driver;
        /** Driver ranking. */
        private int ranking; // Volatile would be unnecessarily expensive

        /**
         * Creates a new instance.
         *
         * @param service
         *            the driver. It must not be {@code null}.
         * @param preference
         *            the ranking of the driver
         */
        public DriverService(Driver service, int preference) {
            driver = Objects.requireNonNull(service);
            ranking = preference;
        }

        /**
         * Provides a comparator according {@link #ranking()}.
         *
         * @return a comparator according {@link #ranking()}
         */
        public static Comparator<DriverService> ordering() {
            return Comparator.<DriverService> comparingInt(DriverService::ranking).reversed();
        }

        /**
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return String.format("DriverService[%s, ranking=%d]", driver, ranking);
        }

        /**
         * Returns the driver.
         *
         * @return the driver
         */
        public Driver driver() {
            return driver;
        }

        /**
         * Returns the ranking of this driver.
         *
         * <p>
         * The higher ranking, the more preferred driver. The relative order of
         * the driver, influenced by the time of the driver being added, should
         * be preserved, but it is not guaranteed.
         *
         * @return the ranking of this driver
         */
        int ranking() {
            return ranking;
        }

        /**
         * Sets the ranking of this driver.
         *
         * @param value
         *            the ranking to set
         */
        void ranking(int value) {
            ranking = value;
        }
    }

    /**
     * A {@link DriverProvider} implementation tracking available {@link Driver}
     * services in order to provide them to its own clients that want to use the
     * drivers collectively.
     */
    private static final class DriverTracker extends ServiceTracker<Driver, DriverService> implements DriverProvider, DriverSequence {

        /* Implementation note:
         *
         * This class is internal enough and not published anywhere (besides the OSGi framework
         * itself), so that we can affort the shortcut and inherit from both DriverProvider and
         * DriverSequence to avoid an unncessary object allocation just to return a distinct
         * DriverSequence instance.
         */

        /**
         * Drivers available for the provider to offer.
         *
         * <p>
         * This list shall be kept sorted by the ranking of the contained
         * drivers. Any change of a ranking shall be followed with re-sorting
         * the list. The action should be atomic, so this instance serves as the
         * lock for that too. As the side-effect, it makes the ranking update
         * safely published.
         */
        private final List<DriverService> drivers = new CopyOnWriteArrayList<>();

        /**
         * Creates a new instance.
         *
         * @param bundleContext
         *            the bundle context to use. It must not be {@code null}.
         */
        public DriverTracker(BundleContext bundleContext) {
            super(bundleContext, Driver.class, null);
        }

        // DriverProvider

        /**
         * @see net.yetamine.osgi.jdbc.DriverProvider#drivers()
         */
        public DriverSequence drivers() {
            return this;
        }

        // DriverSequence

        /**
         * @see net.yetamine.osgi.jdbc.DriverSequence#iterator()
         */
        public Iterator<Driver> iterator() {
            return stream().iterator();
        }

        /**
         * @see net.yetamine.osgi.jdbc.DriverSequence#stream()
         */
        public Stream<Driver> stream() {
            return drivers.stream().map(DriverService::driver);
        }

        // Tracking methods

        /**
         * @see org.osgi.util.tracker.ServiceTracker#addingService(org.osgi.framework.ServiceReference)
         */
        @Override
        public DriverService addingService(ServiceReference<Driver> reference) {
            final DriverService result = new DriverService(context.getService(reference), ranking(reference));
            LOGGER.debug("Adding {}.", result);

            synchronized (drivers) {
                drivers.add(result);
                drivers.sort(DriverService.ordering());
            }

            return result;
        }

        /**
         * @see org.osgi.util.tracker.ServiceTracker#modifiedService(org.osgi.framework.ServiceReference,
         *      java.lang.Object)
         */
        @Override
        public void modifiedService(ServiceReference<Driver> reference, DriverService service) {
            final int ranking = ranking(reference);

            synchronized (drivers) {
                if (service.ranking() != ranking) {
                    LOGGER.debug("Updating ranking of {} to {}.", service, ranking);
                    service.ranking(ranking);
                    drivers.sort(DriverService.ordering());
                }
            }
        }

        /**
         * @see org.osgi.util.tracker.ServiceTracker#removedService(org.osgi.framework.ServiceReference,
         *      java.lang.Object)
         */
        @Override
        public void removedService(ServiceReference<Driver> reference, DriverService service) {
            LOGGER.debug("Removing {}.", service);
            drivers.remove(service);
            context.ungetService(reference);
        }

        /**
         * Returns the ranking of the service by its reference.
         *
         * @param reference
         *            the reference to use. It must not be {@code null}.
         *
         * @return the ranking of the service
         */
        private static int ranking(ServiceReference<?> reference) {
            final Object result = reference.getProperty(Constants.SERVICE_RANKING);
            return (result instanceof Integer) ? (Integer) result : 0;
        }
    }
}
