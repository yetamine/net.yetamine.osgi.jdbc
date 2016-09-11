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

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.util.tracker.BundleTracker;

/**
 * A bundle tracker to trigger the driver loader actions.
 */
final class JdbcDriverTracker extends BundleTracker<Object> {

    /**
     * Creates a new instance.
     *
     * @param bundleContext
     *            the context of this bundle. It must not be {@code null}.
     */
    public JdbcDriverTracker(BundleContext bundleContext) {
        super(bundleContext, Bundle.ACTIVE, null);
    }

    /**
     * @see org.osgi.util.tracker.BundleTracker#addingBundle(org.osgi.framework.Bundle,
     *      org.osgi.framework.BundleEvent)
     */
    @Override
    public Object addingBundle(Bundle bundle, BundleEvent event) {
        final Object group = JdbcWeavingHook.driverGroup(bundle);
        if (JdbcDriverLoader.loadDrivers(bundle) > 0) {
            return group;
        }

        return null; // Do not track this bundle
    }

    /**
     * @see org.osgi.util.tracker.BundleTracker#removedBundle(org.osgi.framework.Bundle,
     *      org.osgi.framework.BundleEvent, java.lang.Object)
     */
    @Override
    public void removedBundle(Bundle bundle, BundleEvent event, Object object) {
        if (object != null) { // The 'object' is actually the group tag
            JdbcDriverLoader.unloadDrivers(bundle, object);
        }
    }
}
