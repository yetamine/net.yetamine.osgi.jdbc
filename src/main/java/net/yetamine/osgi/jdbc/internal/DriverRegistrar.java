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
import java.sql.DriverAction;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import net.yetamine.osgi.jdbc.DriverConstants;
import net.yetamine.osgi.jdbc.support.DriverReference;

/**
 * Provides a bridge between the registration interface in the style of the
 * original {@link java.sql.DriverManager} and OSGi, so that all registered
 * drivers can be published as OSGi services as well.
 */
public final class DriverRegistrar implements BundleController {

    /* Implementation notes:
     *
     * This class is thread-safe, using 'this' as the lock. Because it is used
     * just internally, within the bundle, there is no real danger of a client
     * to interfere with such a locking policy.
     *
     * Moreover, thanks to publishing the drivers as OSGi services, it is no
     * need to be highly concurrent and intrinsic locks are good enough.
     */

    /** Drivers available via this bridging registrar. */
    private final Map<DriverReference, DriverRegistration> drivers = new HashMap<>();
    /** Drivers published as OSGi services by the driver registration. */
    private final Map<DriverRegistration, ServiceRegistration<?>> services = new HashMap<>();
    /** Set of the identifiers of currently operational bundles. */
    private final Set<Long> operational = new HashSet<>();
    /** Bundle context for OSGi interactions. */
    private BundleContext bundleContext;

    /**
     * Creates a new instance.
     */
    DriverRegistrar() {
        // Default constructor
    }

    // Thunk support

    /**
     * Registers the given driver.
     *
     * <p>
     * If the driver has been registered, this method does nothing. The callback
     * is not updated nor extended to reflect a possible change to mimic closely
     * {@link java.sql.DriverManager#registerDriver(Driver, DriverAction)}.
     *
     * @param driver
     *            the driver to register. It must not be {@code null}.
     * @param action
     *            the callback for the driver-related events. It must not be
     *            {@code null}.
     * @param bundleId
     *            the identifier of the registering bundle
     */
    public void register(Driver driver, DriverAction action, long bundleId) {
        final DriverRegistration registration = new DriverRegistration(driver, action, bundleId);
        final DriverReference reference = registration.reference(); // Non-null, passing all checks

        synchronized (this) {
            if ((drivers.putIfAbsent(reference, registration) == null) && (bundleContext != null)) {
                publish(registration);
            }
        }
    }

    /**
     * Unregisters the given driver.
     *
     * <p>
     * If the given bundle identifier does not match the registering bundle or
     * if the driver is not registered, this method does nothing; otherwise it
     * unregisters the driver and invokes the callback which was provided when
     * the driver was registered (if any such callback exists).
     *
     * @param driver
     *            the driver to unregister. It must not be {@code null}.
     * @param bundleId
     *            the identifier of the registering bundle
     */
    public void unregister(Driver driver, long bundleId) {
        final DriverReference reference = new DriverReference(driver);
        final DriverRegistration registration;

        synchronized (this) {
            registration = drivers.get(reference);

            // Here we check that the registering bundle is attempting to unregister
            // the driver. It might follow more DriverManager and require a permission
            // for the case when the call comes from a different bundle. On the other
            // hand, it might interfere with the service-driven approach, right?
            if ((registration == null) || (registration.bundleId() != bundleId)) {
                return;
            }

            // Remove the registration record actually
            final DriverRegistration removed = drivers.remove(reference);
            assert (removed == registration);
            conceal(registration);
        }

        registration.action().deregister(); // Outside of the synchronized block
    }

    // BundleController

    /**
     * @see net.yetamine.osgi.jdbc.internal.BundleController#suspend(org.osgi.framework.Bundle)
     */
    @Override
    public void suspend(Bundle bundle) {
        final long bundleId = bundle.getBundleId();

        synchronized (this) {
            if (operational.remove(bundleId)) {
                registrations(bundleId).forEach(this::conceal);
            }
        }
    }

    /**
     * @see net.yetamine.osgi.jdbc.internal.BundleController#resume(org.osgi.framework.Bundle)
     */
    @Override
    public void resume(Bundle bundle) {
        final long bundleId = bundle.getBundleId();

        synchronized (this) {
            if (operational.add(bundleId)) {
                registrations(bundleId).forEach(this::publish);
            }
        }
    }

    /**
     * @see net.yetamine.osgi.jdbc.internal.BundleController#cancel(org.osgi.framework.Bundle)
     */
    @Override
    public void cancel(Bundle bundle) {
        final long bundleId = bundle.getBundleId(); // Implicit null check

        // Retrieve and remove all registrations for the bundle. Save the
        // registrations in the list and rather process their callbacks
        // outside of the synchronized block.

        final List<DriverRegistration> registrations = new ArrayList<>();

        synchronized (this) {
            operational.remove(bundleId);
            for (Iterator<DriverRegistration> it = drivers.values().iterator(); it.hasNext();) {
                final DriverRegistration registration = it.next();
                if (registration.bundleId() == bundleId) {
                    registrations.add(registration);
                    it.remove(); // First remove
                    conceal(registration);
                }
            }
        }

        final List<Throwable> exceptions = registrations.stream()   // Process removed registrations outside the lock
                .map(DriverRegistrar::deregister)                   // Invoke the callback
                .filter(Objects::nonNull)                           // And collect failures
                .collect(Collectors.toList());

        if (exceptions.isEmpty()) { // All successful, nothing to throw
            return;
        }

        // Make the umbrella exception to throw
        final String f = "Unregistration for bundle %d failed.";
        final RuntimeException exception = new RuntimeException(String.format(f, bundleId));
        exceptions.forEach(exception::addSuppressed);
        throw exception;
    }

    // Activator support

    /**
     * Binds this instance to the bundle in whose context the instance should
     * serve.
     *
     * <p>
     * If the given context differs from the current one, the instance must
     * {@link #release()} in order to release from the current context, and
     * switch to the given context then, which means that all available drivers
     * shall be registered again.
     *
     * @param context
     *            the context of the bundle to use for OSGi interactions. It
     *            must not be {@code null}.
     */
    public synchronized void bind(BundleContext context) {
        if (context.equals(bundleContext)) { // Implicit null check
            return;
        }

        release();
        bundleContext = context;
        drivers.values().forEach(this::publish);
    }

    /**
     * Unbinds this instance from the current bundle and therefore releases all
     * OSGi resources related to the bundle, which means unregistering drivers,
     * that were registered on behalf of the bundle before.
     */
    public synchronized void release() {
        drivers.values().forEach(this::conceal);
        assert services.isEmpty();
        bundleContext = null;
    }

    // Implementation internals

    /**
     * Publishes a driver as an OSGi service on behalf of the current bundle.
     *
     * <p>
     * This method needs the common lock being held by the caller and
     * {@link #bundleContext} being non-{@code null}.
     *
     * @param registration
     *            the driver registration to employ. It must not be
     *            {@code null}.
     */
    private void publish(DriverRegistration registration) {
        assert Thread.holdsLock(this);
        assert (bundleContext != null);

        if ((services.get(registration) != null) || !operational.contains(registration.bundleId())) {
            return;
        }

        final DriverReference reference = registration.reference();
        final Dictionary<String, Object> properties = new Hashtable<>();
        properties.put(DriverConstants.DRIVER_BUNDLE, registration.bundleId());
        properties.put(DriverConstants.DRIVER_VERSION, reference.driverVersion());
        properties.put(DriverConstants.DRIVER_CLASS, reference.driverClass().getTypeName());
        services.put(registration, bundleContext.registerService(Driver.class, reference.driver(), properties));
    }

    /**
     * Conceals the driver from the OSGi framework, i.e., unregisters the driver
     * service registered by {@link #publish(DriverRegistration)} before if
     * exists.
     *
     * <p>
     * This method needs the common lock being held by the caller, however,
     * {@link #bundleContext} may be {@code null} (the presence of service
     * registration matters).
     *
     * @param registration
     *            the driver registration to employ. It must not be
     *            {@code null}.
     */
    private void conceal(DriverRegistration registration) {
        assert Thread.holdsLock(this);

        final ServiceRegistration<?> service = services.remove(registration);
        if (service != null) { // Just cancel it out
            service.unregister();
        }
    }

    /**
     * Returns a {@link Stream} of registrations for the given bundle.
     *
     * <p>
     * This method needs the common lock being held by the caller, however,
     * {@link #bundleContext} may be {@code null} (the presence of service
     * registration matters).
     *
     * @param bundleId
     *            the identifier of the bundle
     *
     * @return the registrations created on behalf of the given bundle
     */
    private Stream<DriverRegistration> registrations(long bundleId) {
        assert Thread.holdsLock(this);
        return drivers.values().stream().filter(r -> r.bundleId() == bundleId);
    }

    /**
     * Finishes the unregistration by invoking {@link DriverAction#deregister()}
     * for the associated callback and returns the exception if any occurred.
     *
     * @param registration
     *            the registration to effectively destroy. It must not be
     *            {@code null}.
     *
     * @return the exception if any occurred, {@code null} otherwise (the
     *         success case)
     */
    private static Throwable deregister(DriverRegistration registration) {
        try { // This might be risky and fail
            registration.action().deregister();
            return null; // Success, no exception
        } catch (Throwable t) {
            return new RuntimeException(String.format("Unregistration problem with %s.", registration), t);
        }
    }

    /**
     * Registration record of a driver.
     */
    private static final class DriverRegistration {

        /** Driver reference. */
        private final DriverReference reference;
        /** Driver action reference. */
        private final DriverAction action;
        /** Registering bundle identifier. */
        private final long bundleId;

        /**
         * Creates a new instance.
         *
         * @param driverReference
         *            the driver reference. It must not be {@code null}.
         * @param driverAction
         *            the action to use. It must not be {@code null}.
         * @param registeringBundleId
         *            the identifier of the registering bundle
         */
        public DriverRegistration(DriverReference driverReference, DriverAction driverAction, long registeringBundleId) {
            reference = Objects.requireNonNull(driverReference);
            action = Objects.requireNonNull(driverAction);
            bundleId = registeringBundleId;
        }

        /**
         * Creates a new instance.
         *
         * @param driver
         *            the driver instance. It must not be {@code null}.
         * @param driverAction
         *            the action to use. It must not be {@code null}.
         * @param registeringBundleId
         *            the identifier of the registering bundle
         */
        public DriverRegistration(Driver driver, DriverAction driverAction, long registeringBundleId) {
            this(new DriverReference(driver), driverAction, registeringBundleId);
        }

        /**
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return reference.toString();
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj instanceof DriverRegistration) {
                final DriverRegistration o = (DriverRegistration) obj;
                return (bundleId == o.bundleId) && reference.equals(o.reference);
            }

            return false;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            return Objects.hash(bundleId, reference);
        }

        /**
         * Returns the driver reference.
         *
         * @return the driver reference
         */
        public DriverReference reference() {
            return reference;
        }

        /**
         * Returns the action of the driver.
         *
         * @return the action of the driver, never {@code null}
         */
        public DriverAction action() {
            return action;
        }

        /**
         * Returns the registering bundle identifier.
         *
         * @return the registering bundle identifier
         */
        public long bundleId() {
            return bundleId;
        }
    }
}
