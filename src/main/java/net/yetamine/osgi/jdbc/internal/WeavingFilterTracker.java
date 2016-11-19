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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.yetamine.osgi.jdbc.tweak.WeavingFilter;

/**
 * Implements {@link WeavingFilter} that tracks services registered with the
 * same interface and aggregates them to provide a single voting result when
 * some class shall be woven.
 */
final class WeavingFilterTracker extends ServiceTracker<WeavingFilter, WeavingFilter> implements WeavingFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeavingFilterTracker.class);

    /** Map of the filters to ask, with the references for removing them. */
    private final Map<ServiceReference<WeavingFilter>, WeavingFilter> filters = new ConcurrentHashMap<>();

    /**
     * Creates a new instance.
     *
     * @param bundleContext
     *            the bundle context to use. It must not be {@code null}.
     */
    public WeavingFilterTracker(BundleContext bundleContext) {
        super(bundleContext, WeavingFilter.class, null);
    }

    /**
     * @see net.yetamine.osgi.jdbc.tweak.WeavingFilter#acceptable(org.osgi.framework.Bundle,
     *      java.lang.String)
     */
    public boolean acceptable(Bundle bundle, String className) {
        for (Map.Entry<ServiceReference<WeavingFilter>, WeavingFilter> entry : filters.entrySet()) {
            final WeavingFilter service = entry.getValue();
            if (service == null) { // Removed meanwhile?
                continue;
            }

            try { // This may fail with an exception!
                if (!service.acceptable(bundle, className)) {
                    return false;
                }
            } catch (Throwable t) {
                remove(entry.getKey()); // Disable the filter then
                LOGGER.warn("Disabled filter '{}' due to an exception.", service, t);
            }
        }

        return true;
    }

    // Tracking methods

    /**
     * @see org.osgi.util.tracker.ServiceTracker#addingService(org.osgi.framework.ServiceReference)
     */
    @Override
    public WeavingFilter addingService(ServiceReference<WeavingFilter> reference) {
        LOGGER.debug("Adding {}.", reference);
        return filters.computeIfAbsent(reference, r -> context.getService(reference));
    }

    /**
     * @see org.osgi.util.tracker.ServiceTracker#removedService(org.osgi.framework.ServiceReference,
     *      java.lang.Object)
     */
    @Override
    public void removedService(ServiceReference<WeavingFilter> reference, WeavingFilter service) {
        if (filters.remove(reference) != null) {
            LOGGER.debug("Removing {}.", reference);
            context.ungetService(reference);
        }
    }
}
