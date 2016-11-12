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

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Objects;

/**
 * An adapter for {@link Enumeration} to {@link Iterator}.
 *
 * @param <E>
 *            the type of the elements
 */
@FunctionalInterface
public interface EnumerationIterator<E> extends Iterator<E> {

    /**
     * Returns the enumeration instance bound to this iterator.
     *
     * @return the enumeration instance bound to this iterator
     */
    Enumeration<? extends E> enumeration();

    /**
     * @see java.util.Iterator#hasNext()
     */
    default boolean hasNext() {
        return enumeration().hasMoreElements();
    }

    /**
     * @see java.util.Iterator#next()
     */
    default E next() {
        return enumeration().nextElement();
    }

    /**
     * Makes an instance from the given {@link Enumeration}.
     *
     * @param <E>
     *            the type of the elements
     * @param enumeration
     *            the enumeration to encapsulate. It must not be {@code null}.
     *
     * @return an instance from the given {@link Enumeration}
     */
    static <E> EnumerationIterator<E> from(Enumeration<? extends E> enumeration) {
        Objects.requireNonNull(enumeration);
        return () -> enumeration;
    }
}
