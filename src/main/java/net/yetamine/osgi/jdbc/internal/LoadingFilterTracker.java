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

import net.yetamine.osgi.jdbc.tweak.LoadingFilter;

/**
 * Implements {@link LoadingFilter} that tracks services registered with the
 * same interface and aggregates them to provide a single voting result when
 * some driver shall be loaded.
 */
final class LoadingFilterTracker extends ServiceTracker<LoadingFilter, LoadingFilter> implements LoadingFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadingFilterTracker.class);

    /** Map of the filters to ask, with the references for removing them. */
    private final Map<ServiceReference<LoadingFilter>, LoadingFilter> filters = new ConcurrentHashMap<>();

    /**
     * Creates a new instance.
     *
     * @param bundleContext
     *            the bundle context to use. It must not be {@code null}.
     */
    public LoadingFilterTracker(BundleContext bundleContext) {
        super(bundleContext, LoadingFilter.class, null);
    }

    /**
     * @see net.yetamine.osgi.jdbc.tweak.LoadingFilter#loadable(org.osgi.framework.Bundle,
     *      java.lang.String)
     */
    public boolean loadable(Bundle bundle, String driverClass) {
        boolean voting = false; // Record if anybody did vote

        for (Map.Entry<ServiceReference<LoadingFilter>, LoadingFilter> entry : filters.entrySet()) {
            final LoadingFilter service = entry.getValue();
            if (service == null) { // Removed meanwhile?
                continue;
            }

            try { // This may fail with an exception!
                if (!service.loadable(bundle, driverClass)) {
                    return false;
                }
            } catch (Throwable t) {
                remove(entry.getKey()); // Disable the filter then
                LOGGER.warn("Disabled filter '{}' due to an exception.", t);
            }

            voting = true;
        }

        // When nobody voted, let's apply the default policy: load from active bundles only
        return voting || ((bundle.getState() & Bundle.ACTIVE) == Bundle.ACTIVE);
    }

    // Tracking methods

    /**
     * @see org.osgi.util.tracker.ServiceTracker#addingService(org.osgi.framework.ServiceReference)
     */
    @Override
    public LoadingFilter addingService(ServiceReference<LoadingFilter> reference) {
        return filters.computeIfAbsent(reference, r -> context.getService(reference));
    }

    /**
     * @see org.osgi.util.tracker.ServiceTracker#removedService(org.osgi.framework.ServiceReference,
     *      java.lang.Object)
     */
    @Override
    public void removedService(ServiceReference<LoadingFilter> reference, LoadingFilter service) {
        if (filters.remove(reference) != null) {
            context.ungetService(reference);
        }
    }
}
