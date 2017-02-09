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
 * Tests if the given class from a bundle may be woven in order to exploit the
 * JDBC support.
 *
 * <p>
 * The JDBC support should consult all available OSGi services registering this
 * interface before weaving a class. If {@link #test(Bundle, String)} of any of
 * them returns {@code false}, the class should not be woven.
 *
 * <p>
 * Because filters are used by the JDBC support's weaving hook, all rules and
 * restrictions of weaving hook implementations apply on the as well. Using a
 * minimal requirement set and dependencies is strongly recommended, which
 * includes, e.g., logging facilities.
 */
@FunctionalInterface
public interface WeavingFilter {

    /**
     * Indicates whether the given class from the given bundle may be woven.
     *
     * <p>
     * This method may throw no exception. The JDBC support should disable any
     * instance that throws any exception.
     *
     * @param bundle
     *            the bundle hosting the given class. It must not be
     *            {@code null}.
     * @param className
     *            the name of the class to be woven. It must not be
     *            {@code null}.
     *
     * @return {@code false} if the class should not be woven, {@code true}
     *         otherwise
     */
    boolean test(Bundle bundle, String className);
}
