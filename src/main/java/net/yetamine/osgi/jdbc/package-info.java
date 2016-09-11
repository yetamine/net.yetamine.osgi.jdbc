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

/**
 * Support for JDBC drivers in OSGi.
 *
 * <p>
 * This bundle provides a surrogate of {@link java.sql.DriverManager}. The
 * surrogate implementation works fine in OSGi environment, unlike the original,
 * because it respects the OSGi class loading delegation model. In order to use
 * JDBC drivers and clients without modifications (except for providing the set
 * of required OSGi bundle headers), this bundle employs hooks to support the
 * common scenarios.
 *
 * <p>
 * One of the installed hooks intercepts all calls, from both drivers and
 * clients, to the {@link java.sql.DriverManager} and redirects them to the
 * surrogate implementation which actually replaces original implementation.
 * Drivers registered to the original implementation (e.g., provided on the boot
 * classpath and registered via the system class loader) are bridged to the OSGi
 * environment as well, but the in-OSGi-provided drivers are preferred.
 *
 * <p>
 * The other hook scans bundles for definitions of {@link java.sql.Driver} for
 * the declarations for {@link java.util.ServiceLoader} and registers them in
 * the surrogate implementation instead.
 *
 * <p>
 * Altogether, OSGi code using the {@link java.sql.DriverManager} and drivers
 * declaring themselves for it via {@link java.util.ServiceLoader} can be used
 * without any modifications. The troubles may occur with the code which loads
 * and handles drivers explicitly in a non-standard way. Even loading a driver
 * itself might be harmless as long as the bundle deals with imports correctly
 * and the driver binds to the {@link java.sql.DriverManager} as it should.
 */
package net.yetamine.osgi.jdbc;
