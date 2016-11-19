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

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleRevision;

/**
 * Mediates the callback between bundle tracking and registration facilities.
 */
final class DriverMediator implements DriverLoading.Action {

    /** Sole instance of this class. */
    private static final DriverLoading.Action INSTANCE = new DriverMediator();

    /**
     * Creates a new instance.
     */
    private DriverMediator() {
        // Default constructor
    }

    /**
     * Returns an instance.
     *
     * @return an instance
     */
    public static DriverLoading.Action instance() {
        return INSTANCE;
    }

    /**
     * @see net.yetamine.osgi.jdbc.internal.DriverLoading.Action#load(org.osgi.framework.Bundle,
     *      java.lang.String)
     */
    public boolean load(Bundle bundle, String driver) throws Exception {
        return AccessController.doPrivileged((PrivilegedExceptionAction<Boolean>) () -> {
            final BundleRevision revision = bundle.adapt(BundleRevision.class);
            if ((revision == null) || ((revision.getTypes() & BundleRevision.TYPE_FRAGMENT) != 0)) {
                // This is a fragment and these are not supported for hosting
                // drivers actually; it could be perhaps possible, but at the
                // cost of non-trivial bundle handling, so let's abort
                return Boolean.FALSE;
            }

            final Class<?> clazz = bundle.loadClass(driver);
            Class.forName(clazz.getName(), true, clazz.getClassLoader());
            return Boolean.TRUE;
        });
    }

    /**
     * @see net.yetamine.osgi.jdbc.internal.DriverLoading.Action#unload(org.osgi.framework.Bundle)
     */
    public void unload(Bundle bundle) {
        Activator.registrar().unregister(bundle.getBundleId());
    }
}
