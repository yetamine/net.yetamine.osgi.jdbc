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

import java.sql.Driver;
import java.util.Collections;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;

import net.yetamine.osgi.jdbc.DriverSequence;

/**
 * Implements an empty {@link DriverSequence}.
 */
public final class NilDriverSequence implements DriverSequence {

    /** Sole instance of this class. */
    private static final DriverSequence INSTANCE = new NilDriverSequence();

    /**
     * Creates a new instance.
     */
    private NilDriverSequence() {
        // Default constructor
    }

    /**
     * Returns an instance of a driver provider that provides nothing.
     *
     * @return an instance of a driver provider that provides nothing
     */
    public static DriverSequence instance() {
        return INSTANCE;
    }

    /**
     * @see net.yetamine.osgi.jdbc.DriverSequence#iterator()
     */
    @Override
    public Iterator<Driver> iterator() {
        return Collections.emptyIterator();
    }

    /**
     * @see net.yetamine.osgi.jdbc.DriverSequence#stream()
     */
    @Override
    public Stream<Driver> stream() {
        return Stream.empty();
    }

    /**
     * @see java.lang.Iterable#spliterator()
     */
    @Override
    public Spliterator<Driver> spliterator() {
        return Spliterators.emptySpliterator();
    }
}
