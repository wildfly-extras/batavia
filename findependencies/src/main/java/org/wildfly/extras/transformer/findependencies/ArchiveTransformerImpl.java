/*
 * Copyright 2023 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extras.transformer.findependencies;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.wildfly.extras.transformer.ResourceTransformer;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 * Scott Marlow
 */
final class ArchiveTransformerImpl {

    private static final String CLASS_SUFFIX = ".class";
    private static final String XML_SUFFIX = ".xml";
    private static final String TLD_SUFFIX = ".tld";
    private static final String JSP_SUFFIX = ".jsp";
    private static final String META_INF_SERVICES_PREFIX = "META-INF/services/";

    protected final ClassCollector classCollector;


    ArchiveTransformerImpl(final Filter filter) {
        classCollector = new ClassCollector(filter);
    }

    private static void readBytes(final InputStream is, final byte[] clazz) throws IOException {
        int offset = 0;
        while (offset < clazz.length) {
            offset += is.read(clazz, offset, clazz.length - offset);
        }
    }


    /**
     * Attempts to scan through given <code>source</code> archive.
     *
     * @param inJarFile archive file to be consumed (can be exploded)
     * @throws IOException if some I/O error occurs
     */
    public void transform(final File inJarFile) throws IOException {
        JarEntry inJarEntry;
        byte[] buffer;


        JarFile jar = new JarFile(inJarFile);
        for (final Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements(); ) {
            // jar file entry preconditions
            inJarEntry = e.nextElement();
            if (inJarEntry.getSize() == 0) {
                continue; // directories
            }
            if (inJarEntry.getSize() < 0) {
                throw new UnsupportedOperationException("File size " + inJarEntry.getName() + " unknown! File size must be positive number");
            }
            if (inJarEntry.getSize() > Integer.MAX_VALUE) {
                throw new UnsupportedOperationException("File " + inJarEntry.getName() + " too big! Maximum allowed file size is " + Integer.MAX_VALUE + " bytes");
            }

            // reading original jar file entry
            buffer = new byte[(int) inJarEntry.getSize()];
            try (InputStream in = jar.getInputStream(inJarEntry)) {
                readBytes(in, buffer);
            }

            if (inJarEntry.getName().endsWith(".jar") || inJarEntry.getName().endsWith(".war")) {
                File baseDir = new File(inJarFile.getParentFile().getAbsolutePath());
                String jarName = inJarEntry.getName();
                File libFile = new File(baseDir, jarName);
                libFile.getParentFile().mkdirs();
                if (!libFile.exists()) {
                    try (FileOutputStream libFileOS = new FileOutputStream(libFile)) {
                        libFileOS.write(buffer);
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                }
                transform(libFile);
                } else {
                Resource r = new Resource(inJarEntry.getName(), buffer);
                // transform resource
                String oldResourceName = r.getName();
                String newResourceName = oldResourceName;
                if (oldResourceName.endsWith(CLASS_SUFFIX)) {
                    collect(r.getData(), newResourceName);
                }
            }
        }
    }

    private void collect(final byte[] clazz, final String newResourceName) {
        final ClassFileRefs cfRefs = ClassFileRefs.of(clazz);
        final ConstantPoolRefs cpRefs = cfRefs.getConstantPool();
        final String transformedClassName = cfRefs.getThisClassAsString();
        // System.out.println("class name = " + transformedClassName);
        int[] constantPoolIndexStartAtOne = cpRefs.getItemRefs();
        for (int index = 1 ; index < constantPoolIndexStartAtOne.length; index++) {
            String info="";
            int classIndex;
            int classNameIndex;
            int methodNameAndTypeIndex;
            int methodNameIndex;
            int methodDescriptorIndex;
            int nameAndTypeIndex;
            int nameAndTypeNameIndex;
            int nameAndTypeDescriptorIndex;
            String methodName;
            String methodDescriptor;

            if (cpRefs.isMethodRef(index)) {
                info += "method: ";

                // method reference found
                classIndex = cpRefs.getMethodRef_ClassIndex(index);
                classNameIndex = cpRefs.getClass_NameIndex(classIndex);

                String className = cpRefs.getUtf8AsString(classNameIndex);

                methodNameAndTypeIndex = cpRefs.getMethodRef_NameAndTypeIndex(index);
                methodNameIndex = cpRefs.getNameAndType_NameIndex(methodNameAndTypeIndex);
                methodName = cpRefs.getUtf8AsString(methodNameIndex);
                methodDescriptorIndex = cpRefs.getNameAndType_DescriptorIndex(methodNameAndTypeIndex);
                methodDescriptor = cpRefs.getUtf8AsString(methodDescriptorIndex);
                System.out.printf("%s: %s => %s %s\n", info, className, methodName, methodDescriptor);
                classCollector.addMethod(className, methodName, methodDescriptor);
            }

            if (cpRefs.isClassInfo(index)) {
                info += "classInfo: ";
                classNameIndex = cpRefs.getClass_NameIndex(index);
                String className = cpRefs.getUtf8AsString(classNameIndex);
                // TODO: should we log the className variable value?
                System.out.printf("%s: %s\n", info, className);
            }
            if (cpRefs.isInterfaceMethodRef(index)) {
                info += "InterfaceMethodref: ";
                classIndex = cpRefs.getInterfaceMethodRef_ClassIndex(index);
                classNameIndex = cpRefs.getClass_NameIndex(classIndex);
                String className = cpRefs.getUtf8AsString(classNameIndex);

                nameAndTypeIndex = cpRefs.getInterfaceMethodRef_NameAndTypeIndex(index);
                nameAndTypeNameIndex = cpRefs.getNameAndType_NameIndex(nameAndTypeIndex);
                methodName = cpRefs.getUtf8AsString(nameAndTypeNameIndex);

                nameAndTypeDescriptorIndex = cpRefs.getNameAndType_DescriptorIndex(nameAndTypeIndex);
                methodDescriptor = cpRefs.getUtf8AsString(nameAndTypeDescriptorIndex);

                System.out.printf("%s: %s => %s %s\n", info, className, methodName, methodDescriptor);
                classCollector.addMethod(className, methodName, methodDescriptor);

                // A symbolic reference to a method of an interface is derived from a
                //CONSTANT_InterfaceMethodref_info structure (§4.4.2). Such a reference
                //gives the name and descriptor of the interface method, as well as a symbolic
                //reference to the interface in which the method is to be found.
                // CONSTANT_Methodref_info {
                //u1 tag;
                //u2 class_index;
                //u2 name_and_type_index;
                //}
            }
            if (cpRefs.isNameAndType(index)) {
                // TODO: CONSTANT_NameAndType_info
            }

            // TODO: reference_index (REF_getField, REF_getStatic, REF_putField, REF_putStatic)
            // TODO: REF_invokeVirtual, REF_newInvokeSpecial
            // TODO: REF_invokeStatic, REF_invokeSpecial
            // TODO: REF_invokeInterface
            // TODO: Check that we have covered all application class references, method/field references
        }

    }

    public static final class Resource {

        private final String name;
        private final byte[] data;

        /**
         * Constructor
         *
         * @param name resource name
         * @param data resource data
         */
        public Resource(final String name, final byte[] data) {
            if (name == null || data == null) {
                throw new NullPointerException();
            }
            this.name = name;
            this.data = data;
        }

        /**
         * Gets resource name.
         *
         * @return resource name
         */
        public String getName() {
            return this.name;
        }

        /**
         * Gets resource data. The byte buffer returned by this method must not be modified.
         *
         * @return resource data
         */
        public byte[] getData() {
            return data;
        }
    }
}
