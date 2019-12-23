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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.osgi.framework.Bundle;

import net.yetamine.osgi.jdbc.tweak.BundleControl;
import net.yetamine.osgi.jdbc.tweak.BundleControl.Condition;

/**
 * Implements {@link BundleControl} that tracks services registered with the
 * same interface and aggregates them in a single instance.
 */
final class BundleControlOptions implements BundleControl.Options {

    /** Declaring bundle. */
    private final Bundle bundle;
    /** Driver names declared by the bundle. */
    private final Set<String> declaredDrivers;
    /** Driver names chosen for loading. */
    private final Set<String> loadableDrivers;
    /** Condition for making the drivers available. */
    private Condition driversAvailable = Condition.WHEN_RUNNING;

    /**
     * Creates a new instance.
     *
     * @param target
     *            the target bundle. It must not be {@code null}.
     * @param drivers
     *            the drivers declared by the bundle. It must not be
     *            {@code null}.
     */
    public BundleControlOptions(Bundle target, Collection<String> drivers) {
        bundle = Objects.requireNonNull(target);

        declaredDrivers = Collections.unmodifiableSet(new HashSet<>(drivers));
        if (declaredDrivers.contains(null)) {
            throw new NullPointerException();
        }

        loadableDrivers = new HashSet<>(declaredDrivers);
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final String f = "BundleControl.Options[bundle=%s, declared=%s, loadable=%s, available=%s]";
        return String.format(f, bundle, declaredDrivers, loadableDrivers, driversAvailable);
    }

    /**
     * @see net.yetamine.osgi.jdbc.tweak.BundleControl.Options#bundle()
     */
    @Override
    public Bundle bundle() {
        return bundle;
    }

    /**
     * @see net.yetamine.osgi.jdbc.tweak.BundleControl.Options#declaredDrivers()
     */
    @Override
    public Set<String> declaredDrivers() {
        return declaredDrivers;
    }

    /**
     * @see net.yetamine.osgi.jdbc.tweak.BundleControl.Options#loadableDrivers()
     */
    @Override
    public Set<String> loadableDrivers() {
        return loadableDrivers;
    }

    /**
     * @see net.yetamine.osgi.jdbc.tweak.BundleControl.Options#driversAvailable()
     */
    @Override
    public Condition driversAvailable() {
        return driversAvailable;
    }

    /**
     * @see net.yetamine.osgi.jdbc.tweak.BundleControl.Options#driversAvailable(net.yetamine.osgi.jdbc.tweak.BundleControl.Condition)
     */
    @Override
    public void driversAvailable(Condition value) {
        driversAvailable = Objects.requireNonNull(value);
    }
}
