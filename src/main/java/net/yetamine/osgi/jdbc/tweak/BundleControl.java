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

package net.yetamine.osgi.jdbc.tweak;

import java.util.Set;
import java.util.function.Predicate;

import org.osgi.framework.Bundle;

/**
 * Adjusts the options for handling a driver bundle.
 *
 * <p>
 * The JDBC support should consult all available OSGi services registering this
 * interface when a driver bundle gets resolved. The services must be consulted
 * in their ranking order, so that the most preferred ones have the last word
 * and may override the options set by less preferred services.
 */
@FunctionalInterface
public interface BundleControl {

    /**
     * Represents a condition related to a state of a bundle which allows an
     * action to occur.
     */
    enum Condition implements Predicate<Bundle> {

        /**
         * This condition is never satisfied.
         */
        NEVER {

            /**
             * @see java.util.function.Predicate#test(java.lang.Object)
             */
            @Override
            public boolean test(Bundle t) {
                return false;
            }
        },

        /**
         * This conditions is satisfied if the bundle is not <i>INSTALLED</i> or
         * <i>UNINSTALLED</i>, i.e., the bundle can be linked and its code might
         * be run.
         */
        WHEN_LINKABLE {

            /**
             * @see java.util.function.Predicate#test(java.lang.Object)
             */
            @Override
            public boolean test(Bundle t) {
                return ((t.getState() & ~(Bundle.INSTALLED | Bundle.UNINSTALLED)) != 0);
            }
        },

        /**
         * This condition is satisfied when the given bundle is <i>ACTIVE</i> or
         * as long as stays in this state. It should be the commonly used option.
         */
        WHEN_RUNNING {

            /**
             * @see java.util.function.Predicate#test(java.lang.Object)
             */
            @Override
            public boolean test(Bundle t) {
                return ((t.getState() & Bundle.ACTIVE) != 0);
            }
        };

        /**
         * Tests if the given bundle satisfies the condition.
         *
         * @param t
         *            the bundle to test. It must not be {@code null}.
         *
         * @return {@code true} if the bundle satisfies this condition
         *
         * @see java.util.function.Predicate#test(java.lang.Object)
         */
        @Override
        public abstract boolean test(Bundle t);
    }

    /**
     * Represents the options that the control may adjust.
     */
    interface Options {

        /**
         * Returns the bundle that this instance is related to.
         *
         * @return the bundle
         */
        Bundle bundle();

        /**
         * Returns the set of driver names declared by the {@link #bundle()},
         * which is unmodifiable (i.e., it is always possible to learn all
         * declared drivers).
         *
         * @return the set of driver names declared by the {@link #bundle()}
         */
        Set<String> declaredDrivers();

        /**
         * Returns the set of drivers chosen for loading.
         *
         * <p>
         * The set is modifiable set, i.e., adding and removing values is
         * possible, but it must be a subset of {@link #declaredDrivers()};
         * implementations may prevent adding illegal values by throwing an
         * exception on any such attempt, or be lenient and just ignore any
         * illegal values when dealing with the actual content of the set.
         *
         * <p>
         * The returned set should contain all {@link #declaredDrivers()}
         * initially, so that all declared drivers shall be registered by
         * default.
         *
         * @return the set of driver chosen for loading
         */
        Set<String> loadableDrivers();

        /**
         * Returns the condition for the drivers registered by the
         * {@link #bundle()} to be visible as OSGi services.
         *
         * @return the condition for the drivers to be visible as services
         */
        Condition driversAvailable();

        /**
         * Sets the condition for the drivers registerd by the {@link #bundle()}
         * to be visible as OSGi services.
         *
         * @param value
         *            the value to set. It must not be {@code null}.
         */
        void driversAvailable(Condition value);
    }

    /**
     * Adjusts possibly the control options for a bundle.
     *
     * <p>
     * This method may throw no exception. The JDBC support should disable any
     * instance that throws any exception.
     *
     * @param options
     *            the options to be adjusted. It must not be {@code null}.
     */
    void adjust(Options options);
}
