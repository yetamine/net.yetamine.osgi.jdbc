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

import java.sql.Driver;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Represents a driver sequence which should be ordered by the preference of a
 * provider, i.e., according to the provider, the first driver in the sequence
 * should be more preferred than the second and so on.
 */
@FunctionalInterface
public interface DriverSequence extends Iterable<Driver> {

    /**
     * Returns an iterator over the represented driver sequence.
     *
     * @see java.lang.Iterable#iterator()
     */
    @Override
    Iterator<Driver> iterator();

    /**
     * Returns a stream with the represented driver sequence.
     *
     * <p>
     * The default implementation uses {@link #spliterator()} implementation to
     * construct a sequential stream.
     *
     * @return a stream with the represented driver sequence
     */
    default Stream<Driver> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
