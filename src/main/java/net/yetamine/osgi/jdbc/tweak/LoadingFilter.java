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

import org.osgi.framework.Bundle;

/**
 * Tests if the given driver from a bundle may be loaded.
 *
 * <p>
 * The JDBC support should consult all available OSGi services registering this
 * interface before loading a driver. If the {@link #loadable(Bundle, String)}
 * method of any of them returns {@code false}, the driver should not be loaded.
 *
 * <p>
 * A driver may be loaded when its bundle gets resolved or activated. The JDBC
 * support loads drivers by default from activated bundles only, but a filter
 * may override the behavior and let it load drivers from a particular bundle
 * already after resolving the bundle.
 */
@FunctionalInterface
public interface LoadingFilter {

    /**
     * Indicates whether the given driver from the given bundle may be loaded.
     *
     * <p>
     * This method may throw no exception. The JDBC support should disable any
     * instance that throws any exception.
     *
     * @param bundle
     *            the bundle hosting the given driver. It must not be
     *            {@code null}.
     * @param driverClass
     *            the name of the class to be loaded. It must not be
     *            {@code null}.
     *
     * @return {@code false} if the driver should not be loaded, {@code true}
     *         otherwise
     */
    boolean loadable(Bundle bundle, String driverClass);
}
