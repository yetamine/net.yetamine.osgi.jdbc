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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Weaving hook for intercepting calls to {@link java.sql.DriverManager} and
 * redirecting them to the surrogate implementation.
 */
final class JdbcWeavingHook implements WeavingHook {

    static final Logger LOGGER = LoggerFactory.getLogger(JdbcWeavingHook.class);

    /** Import entry to be added for a woven class. */
    private final String requiredImport;
    /** Bundle with the hook. */
    private final Bundle parentBundle;

    /**
     * Creates a new instance.
     *
     * @param context
     *            the paretn bundle context. It must not be {@code null}.
     */
    JdbcWeavingHook(BundleContext context) {
        parentBundle = context.getBundle(); // Store this bundle
        final String bundleName = parentBundle.getSymbolicName();
        final String bundleVersion = parentBundle.getVersion().toString();
        final String packageName = net.yetamine.osgi.jdbc.DriverManager.class.getPackage().getName();
        final String importEntry = "%s;bundle-symbolic-name=%s;bundle-version=%s";
        requiredImport = String.format(importEntry, packageName, bundleName, bundleVersion);
    }

    /**
     * Returns the tag for registering or unregisterig a driver from the given
     * bundle.
     *
     * @param bundle
     *            the bundle to use. It must not be {@code null}.
     *
     * @return the tag for registering or unregistering a driver
     */
    public static Object driverGroup(Bundle bundle) {
        return weavingDriverTag(bundle);
    }

    /**
     * @see org.osgi.framework.hooks.weaving.WeavingHook#weave(org.osgi.framework.hooks.weaving.WovenClass)
     */
    @Override
    public void weave(WovenClass wovenClass) {
        final BundleWiring wiring = wovenClass.getBundleWiring();
        final Bundle bundle = wiring.getBundle();
        if (parentBundle.equals(bundle)) {
            return;
        }

        final String className = wovenClass.getClassName();

        try { // May weave classes from other bundles
            LOGGER.trace("Weaving class {}.", className);
            final ClassReader cr = new ClassReader(wovenClass.getBytes());
            final ClassLoader cl = wiring.getClassLoader();
            final ClassWriter cw = new OsgiClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, cl);
            final JdbcWeavingClassVisitor visitor = new JdbcWeavingClassVisitor(cw, weavingDriverTag(bundle));
            cr.accept(visitor, ClassReader.SKIP_FRAMES);

            if (visitor.woven()) {
                wovenClass.setBytes(cw.toByteArray());
                wovenClass.getDynamicImports().add(requiredImport);
                LOGGER.debug("Woven class {}.", className);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to weave class {}.", className, e);
        }
    }

    /**
     * Returns the tag for registering a driver from the given bundle that shall
     * be woven in the code.
     *
     * @param bundle
     *            the bundle to use. It must not be {@code null}.
     *
     * @return the tag for a driver registration to be woven in the code
     */
    private static long weavingDriverTag(Bundle bundle) {
        return bundle.getBundleId();

    }
}

//TODO: TRACE logging of woven methods and transformations done

/**
 * ASM visitor to perform the weaving operation.
 */
final class JdbcWeavingClassVisitor extends ClassVisitor implements Opcodes {

    /** Name of the class to redirect the calls to. */
    static final String SURROGATE_CLASS = net.yetamine.osgi.jdbc.DriverManager.class.getName().replace('.', '/');
    /** Name of the class to redirect the calls from. */
    static final String DRIVER_MANAGER_INTERNAL_NAME = "java/sql/DriverManager";

    /** Indicates a modification. */
    boolean woven = false;
    /** Registration tag. */
    private final long tag;

    /**
     * Creates a new instance.
     *
     * @param visitor
     *            the delegated visitor. It must not be {@code null}.
     * @param registrationTag
     *            the tag to weave for registrations
     */
    public JdbcWeavingClassVisitor(ClassVisitor visitor, long registrationTag) {
        super(Opcodes.ASM5, visitor);
        tag = registrationTag;
    }

    /**
     * Indicates a modification.
     *
     * @return {@code true} if the class has been modified by the weaving
     *         process
     */
    public boolean woven() {
        return woven;
    }

    /**
     *
     * @see org.objectweb.asm.ClassVisitor#visitMethod(int, java.lang.String,
     *      java.lang.String, java.lang.String, java.lang.String[])
     */
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        return new JdbcWeavingMethodVisitor(super.visitMethod(access, name, desc, signature, exceptions));
    }

    /**
     * ASM visitor to perform method weaving.
     */
    private class JdbcWeavingMethodVisitor extends MethodVisitor {

        /**
         * Creates a new instance.
         *
         * @param visitor
         *            the delegated visitor. It must not be {@code null}.
         */
        public JdbcWeavingMethodVisitor(MethodVisitor visitor) {
            super(Opcodes.ASM5, visitor);
        }

        /**
         * @see org.objectweb.asm.MethodVisitor#visitMethodInsn(int,
         *      java.lang.String, java.lang.String, java.lang.String, boolean)
         */
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if ((opcode != Opcodes.INVOKESTATIC) || !DRIVER_MANAGER_INTERNAL_NAME.equals(owner)) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }

            // TODO: Extend to use the tag
            //
            // Following transformation should happen:
            //  - All registerDriver overloads should resolve to the extended
            //    that uses a tag for registering the driver
            // - The tag must be woven in the code (as a constant)
            super.visitMethodInsn(opcode, SURROGATE_CLASS, name, desc, itf);
            woven = true;
        }
    }
}
