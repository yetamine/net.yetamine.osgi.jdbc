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

import org.osgi.framework.Bundle;

/**
 * Provides an interface for controlling how driver bundles are exposed.
 */
interface BundleController {

    /**
     * Suspends all drivers registered by the given bundle.
     *
     * <p>
     * Suspending drivers actually means that their registrations as OSGi
     * services are cancelled, but the driver registrations are retained,
     * therefore {@link #resume(Bundle)} can restore the state.
     *
     * @param bundle
     *            the bundle to suspend. It must not be {@code null}.
     */
    void suspend(Bundle bundle);

    /**
     * Resumes all drivers registered by the given bundle.
     *
     * <p>
     * Registers all drivers registered by the given bundle as OSGi services if
     * not done yet.
     *
     * @param bundle
     *            the bundle to resume. It must not be {@code null}.
     */
    void resume(Bundle bundle);

    /**
     * Cancels all driver registrations for the given bundle.
     *
     * <p>
     * This method unregisters all driver OSGi services for the given bundle and
     * then invokes the drivers' unregistration callback. Therefore calling this
     * method should occur only when abandoning the bundle instance.
     *
     * @param bundle
     *            the bundle to cancel. It must not be {@code null}.
     */
    void cancel(Bundle bundle);
}
