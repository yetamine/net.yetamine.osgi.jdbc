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

import net.yetamine.osgi.jdbc.DriverProvider;
import net.yetamine.osgi.jdbc.DriverSequence;

/**
 * Implements a {@link DriverProvider} providing no drivers.
 */
public final class NilDriverProvider implements DriverProvider {

    /** Sole instance of this class. */
    private static final DriverProvider INSTANCE = new NilDriverProvider();

    /**
     * Creates a new instance.
     */
    private NilDriverProvider() {
        // Default constructor
    }

    /**
     * Returns an instance that provides no drivers.
     *
     * @return an instance that provides no drivers
     */
    public static DriverProvider instance() {
        return INSTANCE;
    }

    /**
     * @see net.yetamine.osgi.jdbc.DriverProvider#drivers()
     */
    @Override
    public DriverSequence drivers() {
        return NilDriverSequence.instance();
    }
}
