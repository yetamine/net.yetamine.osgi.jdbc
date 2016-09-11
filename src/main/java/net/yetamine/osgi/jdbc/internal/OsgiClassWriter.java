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

/*
 * NOTICE:
 *
 * Taken from org.apache.aries.spifly.dynamic.OSGiFriendlyClassWriter and
 * adjusted to the Yetamine code style.
 */

package net.yetamine.osgi.jdbc.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * An override of ASM's default behaviour.
 *
 * <p>
 * Prevent {@link #getCommonSuperClass(String, String)} from loading classes
 * (which it was doing on the wrong {@link ClassLoader} anyway).
 */
public final class OsgiClassWriter extends ClassWriter {

    /** Internal name of {@code java.lang.Object}. */
    private static final String OBJECT_INTERNAL_NAME = "java/lang/Object";
    /** Class loader to use for class resolution. */
    private final ClassLoader loader;

    /**
     * Creates a new instance.
     *
     * @param cr
     *            the source {@link ClassReader}
     * @param flags
     *            the visiting flags
     * @param cl
     *            the class loader to use. It must not be {@code null}.
     */
    public OsgiClassWriter(ClassReader cr, int flags, ClassLoader cl) {
        super(cr, flags);
        loader = Objects.requireNonNull(cl);
    }

    /**
     * Creates a new instance.
     *
     * @param flags
     *            the visiting flags
     * @param cl
     *            the class loader to use. It must not be {@code null}.
     */
    public OsgiClassWriter(int flags, ClassLoader cl) {
        super(flags);
        loader = Objects.requireNonNull(cl);
    }

    /**
     * We provide an implementation that doesn't cause class loads to occur. It
     * may not be sufficient because it expects to find the common parent using
     * a single class loader, though in fact the common parent may only be
     * loadable by another bundle from which an intermediate class is loaded.
     *
     * <p>
     * The arguments are not equal! (Checked before this method is called.)
     *
     * @see org.objectweb.asm.ClassWriter#getCommonSuperClass(java.lang.String,
     *      java.lang.String)
     */
    @Override
    protected final String getCommonSuperClass(String type1, String type2) {
        // If either is Object, then Object must be the answer
        if (type1.equals(OBJECT_INTERNAL_NAME) || type2.equals(OBJECT_INTERNAL_NAME)) {
            return OBJECT_INTERNAL_NAME;
        }

        final Set<String> names = new HashSet<>();
        names.add(type1);
        names.add(type2);

        try { // Try loading the class (in ASM, not for real)
            boolean runningType1 = true;
            boolean runningType2 = true;

            String currentType1 = type1;
            String currentType2 = type2;

            while (runningType1 || runningType2) {
                if (runningType1) {
                    final InputStream is = loader.getResourceAsStream(currentType1 + ".class");
                    if (is == null) { // The class file isn't visible on this ClassLoader
                        runningType1 = false;
                    } else {
                        final ClassReader cr = new ClassReader(is);
                        currentType1 = cr.getSuperName();

                        if (currentType1 == null) {
                            if (names.size() == 2) {
                                return OBJECT_INTERNAL_NAME; // type1 is an interface
                            }

                            runningType1 = false; // currentType1 was java.lang.Object
                        } else if (!names.add(currentType1)) {
                            return currentType1;
                        }
                    }
                }

                if (runningType2) {
                    final InputStream is = loader.getResourceAsStream(currentType2 + ".class");
                    if (is == null) { // The class file isn't visible on this ClassLoader
                        runningType2 = false;
                    } else {
                        final ClassReader cr = new ClassReader(is);
                        currentType2 = cr.getSuperName();

                        if (currentType2 == null) {
                            if (names.size() == 3) {
                                return OBJECT_INTERNAL_NAME;  // type2 is an interface
                            }

                            runningType2 = false; // currentType2 was java.lang.Object
                        } else if (!names.add(currentType2)) {
                            return currentType2;
                        }
                    }
                }
            }

            return OBJECT_INTERNAL_NAME; // Better than to fail unrecoverably
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
