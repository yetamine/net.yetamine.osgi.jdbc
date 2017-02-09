/*
 * Copyright 2017 Yetamine
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

import net.yetamine.osgi.jdbc.internal.Activator;

/**
 * A utility class that provides support for weaving-related classes.
 */
public final class WeavingSupport {

    /**
     * Prevents creating instances of this class.
     */
    private WeavingSupport() {
        throw new AssertionError();
    }

    /**
     * Executes the given {@link Runnable}.
     *
     * <p>
     * This method uses the executor provided by the extender bundle to execute
     * the given {@link Runnable} instance. When the extender bundle is active,
     * it provides a single-threaded executor that runs the tasks in background,
     * otherwise the task is executed by the calling thread.
     *
     * <p>
     * The purpose of this method is to provide support for weaving operations,
     * e.g., weaving filters, that might need to trigger an operation which may
     * execute asynchronously and may trigger loading additional classes, which
     * is usually unacceptable for any code running in a weaving hook scope like
     * the filters do.
     *
     * <p>
     * The typical use of this method looks like:
     *
     * <pre>
     * WeavingSupport.execute(() -&gt; LOGGER.debug("Failed to do foo.", e));
     * </pre>
     *
     * The tasks are executed in the serial order as they are enqueued to the
     * executor, if not executed directly, which allows to keep logging (i.e.,
     * the most often reason for using the executor) quite consistent. Using a
     * single-threaded executor with an unbounded queue, however, needs some
     * care and it should be used only for the very necessary tasks.
     *
     * @param r
     *            the task to run. It must not be {@code null}.
     */
    public static void execute(Runnable r) {
        Activator.executor().execute(r);
    }
}
