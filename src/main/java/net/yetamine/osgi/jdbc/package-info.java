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
 * <h1>Support for JDBC drivers in OSGi</h1>
 *
 * This bundle provides support for replacing {@link java.sql.DriverManager} in
 * OSGi environment, while allowing JDBC drivers and clients to run in an OSGi
 * container with no code modifications (except for providing the code as OSGi
 * bundles with the necessary manifest headers).
 *
 * <h2>Implementation details</h2>
 *
 * The support, provided by this bundle, installs hooks to intercept calls from
 * both drivers and clients to {@link java.sql.DriverManager} and redirects the
 * driver- and connection-related calls to surrogate implementations, which are
 * OSGi-friendly. This is achieved by a weaving hook that patches those classes
 * on-the-fly. In order to support drivers provided by bootstrap delegation,
 * {@link java.sql.DriverManager} can be used as a fallback.
 *
 * <p>
 * The other hook, installed by this bundle, scans other bundles for declaring
 * {@link java.sql.Driver} implementations for {@link java.util.ServiceLoader}.
 * These classes are loaded (after weaving to use the surrogate implementation)
 * to trigger the driver registration. The drivers, registered as the result of
 * this action, are published as OSGi services.
 *
 * <p>
 * The described approach has some limitations: it can't handle non-static code
 * that invokes {@link java.sql.DriverManager} via reflection or any code which
 * can't be processed by the weaving hook for any reasons.
 *
 * <h2>Published interfaces</h2>
 *
 * {@link net.yetamine.osgi.jdbc.DriverProvider} provides an imitation of the
 * subset of {@link java.sql.DriverManager}'s methods which deals with making
 * connections. The JDBC support registers a service with this interface that
 * collects all registered drivers and yet includes the drivers available via
 * {@link java.sql.DriverManager}.
 *
 * <p>
 * Implementations of all interfaces in this package must be thread-safe.
 */
@org.osgi.annotation.versioning.Version("1.0.2")
package net.yetamine.osgi.jdbc;
