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
import java.util.OptionalLong;
import java.util.function.Function;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.yetamine.osgi.jdbc.support.WeavingSupport;
import net.yetamine.osgi.jdbc.tweak.WeavingFilter;

/**
 * Implements {@link WeavingHook} that patches calls to all significant methods
 * of {@link java.sql.DriverManager} and redirects them to the thunk class that
 * invokes the JDBC support core.
 */
final class WeavingHookService implements WeavingHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeavingHookService.class);

    /** Re-entrant protection filter. */
    private final WeavingFilter protection;
    /** Weaving filter to apply. */
    private final WeavingFilter filter;
    /** Resolver for the caller identifiers. */
    private final Function<? super Bundle, OptionalLong> resolver;
    /** Import entry to be added for a woven class. */
    private final String requiredImport;
    /** Re-entrancy protection flag. */
    private final ThreadLocal<Boolean> reentering = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Creates a new instance.
     *
     * @param bundle
     *            the parent bundle. It must not be {@code null}.
     * @param protectionFilter
     *            the protection filter to prevent re-entrant weaving. It must
     *            not be {@code null}.
     * @param customFilter
     *            the custom filter to approve weaving of a class. It must not
     *            be {@code null}.
     * @param callerResolver
     *            the resolver that provides the caller identifier to weave for
     *            a bundle. It must not be {@code null}.
     */
    public WeavingHookService(Bundle bundle, WeavingFilter protectionFilter, WeavingFilter customFilter, Function<? super Bundle, OptionalLong> callerResolver) {
        protection = Objects.requireNonNull(protectionFilter);
        resolver = Objects.requireNonNull(callerResolver);
        filter = Objects.requireNonNull(customFilter);

        final String bundleName = bundle.getSymbolicName();
        final String bundleVersion = bundle.getVersion().toString();
        final String packageName = WeavingClassVisitor.Thunk.THUNKING_CLASS.classType().getPackage().getName();
        final String importEntry = "%s;bundle-symbolic-name=%s;bundle-version=%s"; // Should be good enough for a singleton
        requiredImport = String.format(importEntry, packageName, bundleName, bundleVersion);

        // This is important! Trigger the WeavingSupport before any weaving actually starts,
        // so that the class is loaded. The invocation moreover tests the protection filter

        WeavingSupport.execute(() -> {
            if (protection.test(bundle, WeavingSupport.class.getName())) {
                LOGGER.error("WeavingSupport test failed. This may indicate an internal error.");
            }
        });
    }

    /**
     * Creates a new instance.
     *
     * @param bundleContext
     *            the parent bundle context. It must not be {@code null}.
     * @param protectionFilter
     *            the protection filter to prevent re-entrant weaving. It must
     *            not be {@code null}.
     * @param customFilter
     *            the custom filter to approve weaving of a class. It must not
     *            be {@code null}.
     * @param callerResolver
     *            the resolver that provides the caller identifier to weave for
     *            a bundle. It must not be {@code null}.
     */
    public WeavingHookService(BundleContext bundleContext, WeavingFilter protectionFilter, WeavingFilter customFilter, Function<? super Bundle, OptionalLong> callerResolver) {
        this(bundleContext.getBundle(), protectionFilter, customFilter, callerResolver);
    }

    /**
     * Resolves the caller identifier by the given bundle.
     *
     * <p>
     * This method can be used as the caller resolver for this class. It just
     * maps the bundle identifier to the caller's.
     *
     * @param bundle
     *            the bundle to resolve. It must not be {@code null}.
     *
     * @return the caller identifier
     */
    public static OptionalLong defaultCallerResolver(Bundle bundle) {
        return OptionalLong.of(bundle.getBundleId());
    }

    /**
     * @see org.osgi.framework.hooks.weaving.WeavingHook#weave(org.osgi.framework.hooks.weaving.WovenClass)
     */
    @Override
    public void weave(WovenClass wovenClass) {
        if (reentering.get()) {
            WeavingSupport.execute(() -> {
                final String className = wovenClass.getClassName();
                final Bundle bundle = wovenClass.getBundleWiring().getBundle();
                LOGGER.trace("Omitted class {} from '{}': excluded due to weaving recursion.", className, bundle);
            });

            return;
        }

        reentering.set(Boolean.TRUE);
        try { // Protected patching
            patch(wovenClass);
        } finally {
            reentering.set(Boolean.FALSE);
        }
    }

    /**
     * Patches the given class to weave if possible and necessary.
     *
     * @param wovenClass
     *            the woven class definition. It must not be {@code null}.
     */
    private void patch(WovenClass wovenClass) {
        final BundleWiring wiring = wovenClass.getBundleWiring();
        final Bundle bundle = wiring.getBundle();
        final String className = wovenClass.getClassName();

        // Protect the service itself from recursive weaving
        if (!protection.test(bundle, className)) {
            WeavingSupport.execute(() -> {
                final String f = "Omitted class {} from '{}': excluded by the weaving protection filter.";
                LOGGER.debug(f, className, bundle);
            });

            return;
        }

        // Run the resolver first as it should be faster than the filters
        final OptionalLong caller = resolver.apply(bundle);

        if (!caller.isPresent()) {
            WeavingSupport.execute(() -> {
                final String f = "Omitted class {} from '{}': unavailable caller identifier.";
                LOGGER.debug(f, className, bundle);
            });

            return;
        }

        // Apply external filters
        if (!filter.test(bundle, className)) {
            WeavingSupport.execute(() -> {
                final String f = "Omitted class {} from '{}': suppressed by filters.";
                LOGGER.debug(f, className, bundle);
            });

            return;
        }

        try {
            WeavingSupport.execute(() -> LOGGER.trace("Weaving class {} from '{}'.", className, bundle));
            final ClassReader cr = new ClassReader(wovenClass.getBytes());
            final ClassLoader cl = wiring.getClassLoader();
            final ClassWriter cw = new WeavingClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, cl);
            final WeavingClassVisitor visitor = new WeavingClassVisitor(cw, caller.getAsLong());
            cr.accept(visitor, ClassReader.SKIP_FRAMES);

            if (!visitor.woven()) { // No need to change this class
                WeavingSupport.execute(() -> LOGGER.trace("Weaving class {} from '{}' cancelled.", className, bundle));
                return;
            }

            wovenClass.setBytes(cw.toByteArray());
            wovenClass.getDynamicImports().add(requiredImport);
            WeavingSupport.execute(() -> LOGGER.debug("Woven class {} from '{}'.", className, bundle));
        } catch (Exception e) {
            WeavingSupport.execute(() -> LOGGER.warn("Failed to weave class {} from '{}'.", className, bundle, e));
        }
    }
}
