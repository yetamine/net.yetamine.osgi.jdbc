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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents a sequence of items ordered by given criteria.
 *
 * <p>
 * The represented sequence can be adjusted. The implementation is thread-safe,
 * hence it can be used conveniently with various trackers and listeners to get
 * reasonably accurate snapshots of recent state, e.g., which services are
 * available.
 *
 * @param <K>
 *            the type of the item keys
 * @param <V>
 *            the type of the item values
 */
final class OrderedSequence<K, V> {

    /**
     * Represents an item of the sequence consisting of the value and its key.
     *
     * @param <K>
     *            the type of the item keys
     * @param <V>
     *            the type of the item values
     */
    public static final class Item<K, V> {

        /** Value of the item. */
        private final V value;
        /** Key of the item. */
        private final K key;

        /**
         * Creates a new instance.
         *
         * @param itemKey
         *            the key of the item. It must not be {@code null}.
         * @param itemValue
         *            the value of the item. It must not be {@code null}.
         */
        public Item(K itemKey, V itemValue) {
            value = Objects.requireNonNull(itemValue);
            key = Objects.requireNonNull(itemKey);
        }

        /**
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return String.format("[%s=%s]", key, value);
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj instanceof Item<?, ?>) {
                final Item<?, ?> o = (Item<?, ?>) obj;
                return key.equals(o.key) && value.equals(o.value);
            }

            return false;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            return key.hashCode() ^ value.hashCode();
        }

        /**
         * Returns the key of the item.
         *
         * @return the key of the item
         */
        public K key() {
            return key;
        }

        /**
         * Returns the value of the item.
         *
         * @return the value of the item
         */
        public V value() {
            return value;
        }
    }

    /* Implementation notes:
     *
     * A map to keep the values unique by some key is maintained, which allows
     * to distinguish even significant duplicates of a value. The map acts as
     * the primary source of the data for making the ordered list which is made
     * on demand and cached only until a change of the primary data. The map is
     * used as the lock for all non-atomic operations and for its own safety.
     */

    /** Comparator to establish the order of the items. */
    private final Comparator<? super Item<? extends K, ? extends V>> comparator;
    /** Map for looking up the values and keeping them unique. */
    private final Map<K, Item<K, V>> mapping = new HashMap<>();
    /** Currently valid snapshot of the items in the desired order. */
    private volatile List<Item<K, V>> items = Collections.emptyList();
    /** Currently valid snapshot of the values in the desired order. */
    private volatile List<V> values = Collections.emptyList();

    /**
     * Creates a new instance.
     *
     * @param ordering
     *            the comparator defining the item ordering. It must not be
     *            {@code null}.
     */
    public OrderedSequence(Comparator<? super Item<? extends K, ? extends V>> ordering) {
        comparator = Objects.requireNonNull(ordering);
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return items().toString();
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        return (obj instanceof OrderedSequence<?, ?>) && items().equals(((OrderedSequence<?, ?>) obj).items());
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return items().hashCode();
    }

    /**
     * Adds an item.
     *
     * @param key
     *            the key of the item. It must not be {@code null}.
     * @param value
     *            the value of the item. It must not be {@code null}.
     *
     * @return {@code true} if the item was added by this call, {@code false} if
     *         an item with an equal key already exists
     */
    public boolean add(K key, V value) {
        final Item<K, V> item = new Item<>(key, value);

        synchronized (mapping) {
            if (mapping.putIfAbsent(key, item) == null) {
                invalidate();
                return true;
            }
        }

        return false;
    }

    /**
     * Adds an item or updates an existing one.
     *
     * @param key
     *            the key of the item. It must not be {@code null}.
     * @param value
     *            the value of the item. It must not be {@code null}.
     *
     * @return the previous item if any
     */
    public Optional<Item<K, V>> set(K key, V value) {
        final Item<K, V> item = new Item<>(key, value);

        final Item<K, V> result;
        synchronized (mapping) {
            result = mapping.put(key, item);
            invalidate();
        }

        return Optional.ofNullable(result);
    }

    /**
     * Removes an item.
     *
     * @param key
     *            the key of the item
     *
     * @return the removed value if any
     */
    public Optional<Item<K, V>> remove(Object key) {
        final Item<K, V> result;
        synchronized (mapping) {
            result = mapping.remove(key);
        }

        return Optional.ofNullable(result);
    }

    /**
     * Removes an item if matching the given predicate.
     *
     * <p>
     * This method may be used to remove an existing item atomically with this
     * pattern:
     *
     * <pre>
     * sequence.get(key).ifPresent(item -&gt; sequence.remove(item.key(), item::equals))
     * </pre>
     *
     * @param key
     *            the key of the item
     * @param condition
     *            the condition for removal. It must not be {@code null}.
     *
     * @return the removed value if any
     */
    public Optional<Item<K, V>> remove(Object key, Predicate<? super Item<K, V>> condition) {
        final Item<K, V> result;
        synchronized (mapping) {
            result = mapping.get(key);
            if ((result == null) || !condition.test(result)) {
                return Optional.empty();
            }

            mapping.remove(key);
        }

        return Optional.of(result);
    }

    /**
     * Gets an item.
     *
     * @param key
     *            the key of the item
     *
     * @return the item if any
     */
    public Optional<Item<K, V>> item(Object key) {
        final Item<K, V> result;
        synchronized (mapping) {
            result = mapping.get(key);
        }

        return Optional.ofNullable(result);
    }

    /**
     * Returns an immutable snapshot of the current state in the desired
     * ordering.
     *
     * @return an immutable snapshot of the current state
     */
    public List<Item<K, V>> items() {
        // Using a double-checked locking here
        final List<Item<K, V>> result = items;
        return (result != null) ? result : refresh();
    }

    /**
     * Gets a value of an item.
     *
     * @param key
     *            the key of the item
     *
     * @return the value if any
     */
    public Optional<V> value(Object key) {
        return item(key).map(Item::value);
    }

    /**
     * Returns an immutable snapshot of the current state in the desired
     * ordering.
     *
     * @return an immutable snapshot of the current state
     */
    public List<V> values() {
        List<V> result = values;
        if (result != null) {
            return result;
        }

        synchronized (mapping) {
            result = Collections.unmodifiableList(refresh().stream().map(Item::value).collect(Collectors.toList()));
            values = result;
        }

        return result;
    }

    /**
     * Updates the current snapshot if missing (which indicates that the primary
     * data changed and the snapshot may need an update).
     *
     * @return the updated snapshot
     */
    private List<Item<K, V>> refresh() {
        List<Item<K, V>> result;
        synchronized (mapping) {
            result = items;

            if (result == null) {
                result = mapping.values().stream()  // @formatter:break
                        .sorted(comparator)         // Make it ordered as expected
                        .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));

                items = result;
            }
        }

        return result;
    }

    /**
     * Invalidates the current snapshot, e.g., when the primary data changes.
     */
    private void invalidate() {
        values = null;
        items = null;
    }
}
