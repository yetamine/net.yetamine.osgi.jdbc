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

import net.yetamine.osgi.jdbc.tweak.WeavingFilter;

/**
 * Implements {@link WeavingHook} that patches calls to all significant methods
 * of {@link java.sql.DriverManager} and redirects them to the thunk class that
 * invokes the JDBC support core.
 */
final class WeavingHookService implements WeavingHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeavingHookService.class);

    /** Weaving filter to apply. */
    private final WeavingFilter filter;
    /** Resolver for the caller identifiers. */
    private final Function<? super Bundle, OptionalLong> resolver;
    /** Import entry to be added for a woven class. */
    private final String requiredImport;
    /** Bundle with the hook. */
    private final Bundle parentBundle;

    /**
     * Creates a new instance.
     *
     * @param context
     *            the parent bundle context. It must not be {@code null}.
     * @param weavingFilter
     *            the filter to approve weaving of a class. It must not be
     *            {@code null}.
     * @param callerResolver
     *            the resolver that provides the caller identifier to weave for
     *            a bundle. It must not be {@code null}.
     */
    public WeavingHookService(BundleContext context, WeavingFilter weavingFilter, Function<? super Bundle, OptionalLong> callerResolver) {
        resolver = Objects.requireNonNull(callerResolver);
        filter = Objects.requireNonNull(weavingFilter);

        parentBundle = context.getBundle(); // Implicit null check
        final String bundleName = parentBundle.getSymbolicName();
        final String bundleVersion = parentBundle.getVersion().toString();
        final String packageName = WeavingClassVisitor.Thunk.THUNKING_CLASS.classType().getPackage().getName();
        final String importEntry = "%s;bundle-symbolic-name=%s;bundle-version=%s"; // Should be good enough for a singleton
        requiredImport = String.format(importEntry, packageName, bundleName, bundleVersion);
    }

    /**
     * Creates a new instance with the default caller identifier resolver that
     * uses the given bundle's identifier directly.
     *
     * @param context
     *            the parent bundle context. It must not be {@code null}.
     * @param weavingFilter
     *            the filter to approve weaving of a class. It must not be
     *            {@code null}.
     */
    public WeavingHookService(BundleContext context, WeavingFilter weavingFilter) {
        this(context, weavingFilter, bundle -> OptionalLong.of(bundle.getBundleId()));
    }

    /**
     * @see org.osgi.framework.hooks.weaving.WeavingHook#weave(org.osgi.framework.hooks.weaving.WovenClass)
     */
    @Override
    public void weave(WovenClass wovenClass) {
        final BundleWiring wiring = wovenClass.getBundleWiring();
        final Bundle bundle = wiring.getBundle();
        if (parentBundle.equals(bundle)) {
            return;
        }

        final String className = wovenClass.getClassName();
        final OptionalLong caller = resolver.apply(bundle);

        if (!caller.isPresent()) { // Run the resolver first as it should be faster than the filters
            LOGGER.debug("Omitted class {} from '{}': unavailable caller identifier.", className, bundle);
            return;
        }

        if (!filter.test(bundle, className)) { // Apply external filters
            LOGGER.debug("Omitted class {} from '{}': suppressed by filters.", className, bundle);
            return;
        }

        try {
            LOGGER.trace("Weaving class {} from '{}'.", className, bundle);
            final ClassReader cr = new ClassReader(wovenClass.getBytes());
            final ClassLoader cl = wiring.getClassLoader();
            final ClassWriter cw = new WeavingClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, cl);
            final WeavingClassVisitor visitor = new WeavingClassVisitor(cw, caller.getAsLong());
            cr.accept(visitor, ClassReader.SKIP_FRAMES);

            if (!visitor.woven()) { // No need to change this class
                LOGGER.trace("Weaving class {} from '{}' cancelled.", className, bundle);
                return;
            }

            wovenClass.setBytes(cw.toByteArray());
            wovenClass.getDynamicImports().add(requiredImport);
            LOGGER.debug("Woven class {} from '{}'.", className, bundle);
        } catch (Exception e) {
            LOGGER.warn("Failed to weave class {} from '{}'.", className, bundle, e);
        }
    }
}
