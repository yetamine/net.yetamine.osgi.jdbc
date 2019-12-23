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
 * An adapter for {@link Iterator} to {@link Enumeration}.
 *
 * @param <E>
 *            the type of the elements
 */
@FunctionalInterface
public interface IteratorEnumeration<E> extends Enumeration<E> {

    /**
     * Returns the iterator instance bound to this enumeration.
     *
     * @return the iterator instance bound to this enumeration
     */
    Iterator<? extends E> iterator();

    /**
     * @see java.util.Enumeration#hasMoreElements()
     */
    @Override
    default boolean hasMoreElements() {
        return iterator().hasNext();
    }

    /**
     * @see java.util.Enumeration#nextElement()
     */
    @Override
    default E nextElement() {
        return iterator().next();
    }

    /**
     * Makes an instance from the given {@link Iterator}.
     *
     * @param <E>
     *            the type of the elements
     * @param iterator
     *            the iterator to encapsulate. It must not be {@code null}.
     *
     * @return an instance from the given {@link Iterator}
     */
    static <E> IteratorEnumeration<E> from(Iterator<? extends E> iterator) {
        Objects.requireNonNull(iterator);
        return () -> iterator;
    }
}
