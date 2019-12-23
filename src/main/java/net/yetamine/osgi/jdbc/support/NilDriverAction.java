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

import java.sql.DriverAction;

/**
 * Implements a no-operation {@link DriverAction}.
 */
public final class NilDriverAction implements DriverAction {

    /** Sole instance of this class. */
    private static final DriverAction INSTANCE = new NilDriverAction();

    /**
     * Creates a new instance.
     */
    private NilDriverAction() {
        // Default constructor
    }

    /**
     * Returns an instance of nothing-doing action.
     *
     * @return an instance of nothing-doing action
     */
    public static DriverAction instance() {
        return INSTANCE;
    }

    /**
     * Returns an instance of nothing-doing action if the given argument is
     * {@code null}.
     *
     * @param driverAction
     *            the driver action to return if not {@code null}
     *
     * @return a driver action, never {@code null}
     */
    public static DriverAction ifNull(DriverAction driverAction) {
        return (driverAction != null) ? driverAction : instance();
    }

    /**
     * @see java.sql.DriverAction#deregister()
     */
    @Override
    public void deregister() {
        // Do nothing
    }
}
