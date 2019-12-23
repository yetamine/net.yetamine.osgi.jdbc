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

package net.yetamine.osgi.jdbc.support;

import java.sql.Driver;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import net.yetamine.osgi.jdbc.DriverProvider;
import net.yetamine.osgi.jdbc.DriverSequence;

/**
 * An driver provider encapsulating a chain of driver providers in the order of
 * their preference.
 *
 * <p>
 * This class may be particularly useful for combining an implementation with
 * the fallback to the {@link DriverManager} using {@link DriverManagerAdapter}.
 * In such cases, it is enough to chain the implementation and the adapter with
 * this class.
 */
public final class DriverProviderChain implements DriverProvider {

    /** Actual chaining driver sequence. */
    private final DriverSequence sequence;

    /**
     * Creates a new instance.
     *
     * @param driverProviders
     *            the driver providers to use. It must not be {@code null} and
     *            it must not contain {@code null} elements.
     */
    private DriverProviderChain(Collection<DriverProvider> driverProviders) {
        sequence = new Sequence(driverProviders);
    }

    /**
     * Creates a new instance.
     *
     * @param driverProviders
     *            the driver providers to use. It must not be {@code null} and
     *            it must not contain {@code null} elements.
     *
     * @return the new instance
     */
    public static DriverProvider of(DriverProvider... driverProviders) {
        final List<DriverProvider> providers = Arrays.asList(driverProviders);
        providers.forEach(Objects::requireNonNull);
        return new DriverProviderChain(providers);
    }

    /**
     * Creates a new instance.
     *
     * @param driverProviders
     *            the driver providers to use. It must not be {@code null} and
     *            it must not contain {@code null} elements.
     *
     * @return the new instance
     */
    public static DriverProvider from(Collection<? extends DriverProvider> driverProviders) {
        final Collection<DriverProvider> providers = new ArrayList<>(driverProviders);
        providers.forEach(Objects::requireNonNull);
        return new DriverProviderChain(providers);
    }

    /**
     * @see net.yetamine.osgi.jdbc.DriverProvider#drivers()
     */
    @Override
    public DriverSequence drivers() {
        return sequence;
    }

    /**
     * Implements the chaining sequence.
     */
    private static final class Sequence implements DriverSequence {

        /** Collection of the providers in their preferred order. */
        private final Collection<DriverProvider> providers;

        /**
         * Creates a new instance.
         *
         * @param driverProviders
         *            the collection of the driver providers to use. It must not
         *            be {@code null}.
         */
        public Sequence(Collection<DriverProvider> driverProviders) {
            providers = Objects.requireNonNull(driverProviders);
        }

        /**
         * @see net.yetamine.osgi.jdbc.DriverSequence#iterator()
         */
        @Override
        public Iterator<Driver> iterator() {
            return stream().iterator();
        }

        /**
         * @see net.yetamine.osgi.jdbc.DriverSequence#stream()
         */
        @Override
        public Stream<Driver> stream() {
            return providers.stream().flatMap(provider -> provider.drivers().stream());
        }
    }
}
