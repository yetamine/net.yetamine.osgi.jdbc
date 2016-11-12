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

import java.lang.reflect.Method;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ASM visitor to perform the weaving operation.
 */
final class WeavingClassVisitor extends ClassVisitor implements Opcodes {

    static final Logger LOGGER = LoggerFactory.getLogger(WeavingClassVisitor.class);

    /** Caller identifier. */
    final long caller;
    /** Indicates a woven modification. */
    boolean woven = false;

    /**
     * Creates a new instance.
     *
     * @param visitor
     *            the delegated visitor. It must not be {@code null}.
     * @param callerIdentifier
     *            the caller identifier to weave
     */
    public WeavingClassVisitor(ClassVisitor visitor, long callerIdentifier) {
        super(Opcodes.ASM5, visitor);
        caller = callerIdentifier;
    }

    /**
     * Indicates a modification woven in the class.
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
        // Compose three visitors together: the inherited spills the code to the writer, the JSR inliner
        // adapter deals with deprecated JSR instructions that the writer can't cope with (but those
        // instructions appear in some drivers), and finally the actual weaving visitor that employs
        // the thunk instead of original calls to DriverManager

        final MethodVisitor inherited = super.visitMethod(access, name, desc, signature, exceptions);
        final MethodVisitor adapting = new JSRInlinerAdapter(inherited, access, name, desc, signature, exceptions);
        return new WeavingMethodVisitor(adapting);
    }

    /**
     * Represents a class, providing its internal name as well.
     */
    static final class ClassReference {

        /** Actual {@link Class}. */
        private final Class<?> classType;
        /** Internal name of the class. */
        private final String internalName;

        /**
         * Creates a new instance.
         *
         * @param clazz
         *            the class. It must not be {@code null}.
         */
        public ClassReference(Class<?> clazz) {
            internalName = Type.getInternalName(clazz);
            classType = clazz;
        }

        /**
         * Creates a new instance.
         *
         * @param name
         *            the name of the class. It must not be {@code null}.
         *
         * @throws ClassNotFoundException
         *             if the class can't be loaded
         */
        public ClassReference(String name) throws ClassNotFoundException {
            this(Class.forName(name));
        }

        /**
         * Resolves the class by the name.
         *
         * @param name
         *            the name of the class. It must be valid and linkable class
         *            name.
         *
         * @return a new instance referring to the given class
         */
        public static ClassReference resolve(String name) {
            try {
                return new ClassReference(name);
            } catch (ClassNotFoundException e) {
                final NoClassDefFoundError t = new NoClassDefFoundError(name);
                t.initCause(e);
                throw t;
            }
        }

        /**
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return classType.getTypeName();
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj instanceof ClassReference) {
                final ClassReference o = (ClassReference) obj;
                return classType.equals(o.classType) && internalName.equals(o.internalName);
            }

            return false;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            return classType.hashCode() ^ internalName.hashCode();
        }

        /**
         * Returns the internal name of the class.
         *
         * @return the internal name of the class
         */
        public String internalName() {
            return internalName;
        }

        /**
         * Returns the actual class type.
         *
         * @return the actual class type
         */
        public Class<?> classType() {
            return classType;
        }
    }

    /**
     * Represents a method by the method name and descriptor, which should be
     * unique within the class scope.
     */
    static final class MethodReference {

        /** Name of the method. */
        private final String name;
        /** Descriptor of the method. */
        private final String descriptor;

        /**
         * Creates a new instance.
         *
         * @param methodName
         *            the name of the method. It must not be {@code null}.
         * @param methodDescriptor
         *            the descriptor of the method. It must not be {@code null}.
         */
        public MethodReference(String methodName, String methodDescriptor) {
            descriptor = Objects.requireNonNull(methodDescriptor);
            name = Objects.requireNonNull(methodName);
        }

        /**
         * Creates a new instance.
         *
         * @param method
         *            the method to refer to. It must not be {@code null}.
         */
        public MethodReference(Method method) {
            this(method.getName(), Type.getMethodDescriptor(method));
        }

        /**
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return String.format("%s/%s", name, descriptor);
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj instanceof MethodReference) {
                final MethodReference o = (MethodReference) obj;
                return name.equals(o.name) && descriptor.equals(o.descriptor);
            }

            return false;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            return name.hashCode() ^ name.hashCode();
        }

        /**
         * Returns the descriptor of the method.
         *
         * @return the descriptor of the method
         */
        public String descriptor() {
            return descriptor;
        }

        /**
         * Returns the name of the method.
         *
         * @return the name of the method
         */
        public String name() {
            return name;
        }
    }

    /**
     * Describes the thunk implementation details.
     */
    static final class Thunk {

        /** Reference to {@link DriverManager}, which is the weaving target. */
        public static final ClassReference DRIVER_MANAGER = new ClassReference(DriverManager.class);

        /** Reference to the thunking class which shall intercept the calls. */
        public static final ClassReference THUNKING_CLASS = ClassReference.resolve( // @formatter:break
                "net.yetamine.osgi.jdbc.thunk.DriverSupportThunk"                   // Use the by-name resolution to break the dependency cycle
        );

        /**
         * Redirections from {@link #DRIVER_MANAGER} to {@link #THUNKING_CLASS}.
         *
         * <p>
         * All target methods must have the same descriptor, but one more
         * parameter: the last parameter must be a {@code long} identifying the
         * caller, which is passed to the visitor and woven in the resulting
         * code.
         */
        private static final Map<MethodReference, MethodReference> REDIRECTIONS;
        static { // Rather compute the all the constants from the actual classes
            final Set<MethodReference> thunkingMethods = Stream             // Make a set from the stream
                    .of(THUNKING_CLASS.classType().getMethods())            // Get the public methods
                    .map(MethodReference::new)                              // Make references to the methods
                    .collect(Collectors.toSet());

            // Compute the mapping to all methods with the same name and descriptor, except
            // for the the caller identification, which shall be woven in the code

            REDIRECTIONS = new HashMap<>();
            for (Method method : DRIVER_MANAGER.classType().getMethods()) {
                final List<Type> parameters = new ArrayList<>(Arrays.asList(Type.getArgumentTypes(method)));
                parameters.add(Type.LONG_TYPE);
                final Type returnType = Type.getReturnType(method);
                final Type signature = Type.getMethodType(returnType, parameters.toArray(new Type[parameters.size()]));
                // This describes the same method with an additional long parameter (as the last parameter)
                final MethodReference linkage = new MethodReference(method.getName(), signature.getDescriptor());
                if (thunkingMethods.contains(linkage)) { // If there is such a method in the thunk, redirect to it
                    REDIRECTIONS.put(new MethodReference(method), linkage);
                }
            }
        }

        /**
         * Returns the redirection target for the given method.
         *
         * @param method
         *            the method to redirect from
         *
         * @return the redirection target, or {@code null} if none
         */
        public static MethodReference method(MethodReference method) {
            return REDIRECTIONS.get(method);
        }

        private Thunk() {
            throw new AssertionError();
        }
    }

    /**
     * ASM visitor to perform method weaving.
     */
    private final class WeavingMethodVisitor extends MethodVisitor {

        /**
         * Creates a new instance.
         *
         * @param visitor
         *            the delegated visitor. It must not be {@code null}.
         */
        public WeavingMethodVisitor(MethodVisitor visitor) {
            super(Opcodes.ASM5, visitor);
        }

        /**
         * @see org.objectweb.asm.MethodVisitor#visitMethodInsn(int,
         *      java.lang.String, java.lang.String, java.lang.String, boolean)
         */
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if ((opcode != Opcodes.INVOKESTATIC) || !Thunk.DRIVER_MANAGER.internalName().equals(owner)) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }

            final MethodReference invoking = new MethodReference(name, desc);
            final MethodReference thunking = Thunk.method(invoking);
            if (thunking == null) { // If no redirection, keep the original code
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }

            // Redirection exists: weave in the caller identification as the last parameter on the
            // stack just before the invoke instruction. This causes the caller identification to
            // be passed as the parameter to the support core.

            super.visitLdcInsn(caller);
            final String thunkMethod = thunking.name();
            final String thunkDescriptor = thunking.descriptor();
            final String thunkClass = Thunk.THUNKING_CLASS.internalName();
            super.visitMethodInsn(opcode, thunkClass, thunkMethod, thunkDescriptor, false);
            woven = true;

            if (LOGGER.isTraceEnabled()) { // Due to the number of parameters, rather make a check
                final String f = "Woven redirection of {}/{} to of thunk {}/{} with caller {}.";
                LOGGER.trace(f, owner, invoking, thunkClass, thunkMethod, caller);
            }
        }
    }
}
