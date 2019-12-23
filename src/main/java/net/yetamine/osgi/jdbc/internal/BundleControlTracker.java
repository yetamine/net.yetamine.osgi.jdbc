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

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.yetamine.osgi.jdbc.tweak.BundleControl;

/**
 * Implements {@link BundleControl} that tracks services registered with the
 * same interface and aggregates them in a single instance.
 */
final class BundleControlTracker extends ServiceTracker<BundleControl, BundleControl> implements BundleControl {

    private static final Logger LOGGER = LoggerFactory.getLogger(BundleControlTracker.class);

    /** Sequence of the services to call. */
    private final OrderedSequence<ServiceReference<BundleControl>, BundleControl> controls = new OrderedSequence<>(
            (a, b) -> b.key().compareTo(a.key()) // Use the reversed order, so that the most preferred controls go last!
    );

    /**
     * Creates a new instance.
     *
     * @param bundleContext
     *            the bundle context to use. It must not be {@code null}.
     */
    public BundleControlTracker(BundleContext bundleContext) {
        super(bundleContext, BundleControl.class, null);
    }

    /**
     * @see net.yetamine.osgi.jdbc.tweak.BundleControl#adjust(net.yetamine.osgi.jdbc.tweak.BundleControl.Options)
     */
    @Override
    public void adjust(Options options) {
        controls.items().forEach(item -> {
            final BundleControl control = item.value();
            try { // This may fail with an exception!
                control.adjust(options);
            } catch (Throwable t) {
                controls.remove(item.key(), item::equals);
                LOGGER.warn("Disabled control '{}' due to an exception.", control, t);
            }
        });
    }

    // Tracking methods

    /**
     * @see org.osgi.util.tracker.ServiceTracker#addingService(org.osgi.framework.ServiceReference)
     */
    @Override
    public BundleControl addingService(ServiceReference<BundleControl> reference) {
        final BundleControl result = context.getService(reference);
        LOGGER.debug("Adding bundle control '{}'.", result);
        controls.add(reference, result);
        return result;
    }

    /**
     * @see org.osgi.util.tracker.ServiceTracker#modifiedService(org.osgi.framework.ServiceReference,
     *      java.lang.Object)
     */
    @Override
    public void modifiedService(ServiceReference<BundleControl> reference, BundleControl service) {
        LOGGER.debug("Updating bundle control '{}'.", service);
        controls.set(reference, service);
    }

    /**
     * @see org.osgi.util.tracker.ServiceTracker#removedService(org.osgi.framework.ServiceReference,
     *      java.lang.Object)
     */
    @Override
    public void removedService(ServiceReference<BundleControl> reference, BundleControl service) {
        LOGGER.debug("Removing bundle control '{}'.", service);
        controls.remove(reference);
        context.ungetService(reference);
    }
}
