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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.yetamine.osgi.jdbc.tweak.WeavingFilter;

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

        final Set<Bundle> exclusions = requirementClosure(Collections.singleton(serviceContext.getBundle()));
        LOGGER.debug("Opening weaving service with following bundles excluded from weaving: {}", exclusions);
        filterTracker.open(); // Start the tracking before the service starts weaving!
        final Function<Bundle, OptionalLong> resolver = WeavingHookService::defaultCallerResolver;
        final WeavingFilter protection = (bundle, className) -> !exclusions.contains(bundle);
        final WeavingHook hook = new WeavingHookService(serviceContext, protection, filterTracker, resolver);
        service = serviceContext.registerService(WeavingHook.class, hook, null);
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

        LOGGER.debug("Closing weaving service.");
        service.unregister();
        service = null;

        filterTracker.close(); // Stop the tracking after unregistering the weaving hook!
    }

    /**
     * Computes the requirement closure of the given seed bundles.
     *
     * @param bundles
     *            the seed bundles to exclude. It must not be {@code null}.
     */
    private static Set<Bundle> requirementClosure(Collection<Bundle> bundles) {
        // Compute dependencies of the seed bundle tos prevent weaving anything from them
        final Deque<Bundle> resolving = new ArrayDeque<>();
        bundles.forEach(bundle -> resolving.push(Objects.requireNonNull(bundle)));
        final Set<Bundle> result = new HashSet<>();

        // Depth-first search for all required bundles to exclude them for sure
        for (Bundle pending; ((pending = resolving.poll()) != null);) {
            if (result.add(pending)) {
                final BundleWiring wiring = pending.adapt(BundleWiring.class);
                if (wiring == null) {
                    continue;
                }

                wiring.getRequiredWires(BundleRevision.PACKAGE_NAMESPACE).forEach(wire -> {
                    resolving.push(wire.getProviderWiring().getBundle());
                });
            }
        }

        return Collections.unmodifiableSet(result);
    }
}
