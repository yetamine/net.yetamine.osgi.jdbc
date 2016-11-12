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

package net.yetamine.osgi.jdbc;

import java.sql.SQLException;

/**
 * Thrown when the OSGi bridging support encounters an error unrelated to an
 * actual database operation, but rather associated with a management issue,
 * e.g., a timing error and unsatisfied runtime conditions.
 *
 * <p>
 * However, although JDBC driver support dynamic registration, clients usually
 * don't count with runtime failures of such a kind and rather can deal with a
 * {@link SQLException}. Therefore this class, although related more to runtime
 * errors, inherits from that exception to provide a dedicated and yet safe way
 * for the clients to cope with this kind of problems.
 */
public class DriverSupportException extends SQLException {

    /** Serialization version: 1 */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new instance with no details.
     */
    public DriverSupportException() {
        // Default constructor
    }

    /**
     * Create a new instance with the specified detail message.
     *
     * @param message
     *            the detail message
     */
    public DriverSupportException(String message) {
        super(message);
    }

    /**
     * Create a new instance with the specified cause and a detail message
     * constructed from the cause (if not {@code null}).
     *
     * @param cause
     *            the cause
     */
    public DriverSupportException(Throwable cause) {
        super(cause);
    }

    /**
     * Create a new instance with the specified detail message and cause.
     *
     * @param message
     *            the detail message
     * @param cause
     *            the cause
     */
    public DriverSupportException(String message, Throwable cause) {
        super(message, cause);
    }
}
