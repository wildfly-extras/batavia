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

package org.wildfly.extras.transformer.findependencies.classfileapi;

import jdk.internal.classfile.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarFile;

import static jdk.internal.classfile.Classfile.*;

import org.wildfly.extras.transformer.findependencies.archivefile.Reader;
import org.wildfly.extras.transformer.findependencies.ClassReference;
import org.wildfly.extras.transformer.findependencies.Filter;

/**
 * ClassfileAPI
 *
 * @author Scott Marlow
 */
public class ClassfileAPI {

    public static void main(final String... args) throws IOException {
        Filter filter = null;
        ClassFileReader classFileReader = new ClassFileReader();
        System.out.println("xxx using ClassfileAPI + https://openjdk.org/jeps/457");
        for (int looper = 0; looper < args.length; looper++) {
            String arg = args[looper];
            if ("-include".equals(arg) || "-i".equals(arg)) {
                System.out.println("args:" + arg + ":" + args[looper + 1]);
                if (filter == null) {
                    filter = new Filter();
                }
                filter.include(args[++looper]);
            } else if ("-file".equals(arg) || "-f".equals(arg)) {
                ClassReference.clear();
                System.out.println("args:" + arg + ":" + args[looper + 1]);
                final File inJarFile = new File(args[++looper]);
                System.out.println("input file = " + inJarFile + " file exist check = " + inJarFile.exists());
                classFileReader.scan(inJarFile);

                Set<String> classnames = ClassReference.getClassNames();
                for (String classname : classnames) {
                    System.out.printf("Class %s methods : %s are used by %s\n", classname, ClassReference.getMethodsReferenced(classname), inJarFile.getName());
                }
                System.out.printf("finished " + inJarFile + " testing...");
                // clear for the next set of options
                filter = null;
            }
        }
    }

    /**
     * @param inJarFile may be a jar/war/ear
     * @param filter    identifies the class package references to include
     */
    private static void processDeployment(File inJarFile, Filter filter) throws IOException {
        JarFile jar = new JarFile(inJarFile);

    }

    private static String dropFirstAndLastChar(String value) {
        if (value.length() <= 1)
            return value;
        return value.substring(1, value.length() - 1);
    }

    static class ClassFileReader extends Reader {

        @Override
        public void collect(byte[] clazz, String newResourceName) {
            Classfile cf = Classfile.of();

            ClassModel cm = null;
            try {
                cm = cf.parse(clazz);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                throw throwable;
            }
            String className = cm.thisClass().name().stringValue();
            className = className.replace('/','.'); // correct java package name
            System.out.println("Classname = " + className);
            ClassReference.findClassName(className);

            for (ClassElement ce : cm) {
//                className = ce.thisClass().name().stringValue();
//                className = className.replace('/','.'); // correct java package name
//                ClassReference.findClassName(className);
                switch (ce) {
                    case MethodModel mm -> {
                        String methodName = mm.methodName().stringValue();
                        String methodDescriptor = mm.methodType().stringValue();
                        methodDescriptor = dropFirstAndLastChar(methodDescriptor);
                        ClassReference classReference = ClassReference.findClassName(methodDescriptor);
                        classReference.addMethod(methodName, methodDescriptor);

                        // MethodTypeDesc methodTypeDesc = mm.methodTypeSymbol();
                        // ClassDesc classDesc = methodTypeDesc.returnType();

                        if (mm.parent().isPresent()) {
                            ClassModel methodIsInClass = mm.parent().get();
                            String referencedClassName = methodIsInClass.thisClass().name().stringValue().replace('/','.');
                            // TODO: extract classname out of descriptor
                            System.out.printf("\nreferenced class %s method %s descriptor %s", referencedClassName, methodName, methodDescriptor);
                        } else {
                            System.out.printf("\nneed to understand why methodName %s descriptor: has no parent", methodName, methodDescriptor);
                        }

                    }
                    case FieldModel fm -> {
                        String fieldName = fm.fieldName().stringValue();
                        String fieldDescriptor = fm.fieldType().stringValue();
                        fieldDescriptor = dropFirstAndLastChar(fieldDescriptor);
                        ClassReference classReference = ClassReference.findClassName(fieldDescriptor);
                        classReference.addField(fieldName, fieldDescriptor);

                        if (fm.parent().isPresent()) {
                            ClassModel fieldIsInClass = fm.parent().get();
                            String referencedClassName = fieldIsInClass.thisClass().name().stringValue().replace('/','.');
                            // TODO: extract classname out of descriptor
                            System.out.printf("\nreferenced class %s field %s descriptor %s", referencedClassName, fieldName, fieldDescriptor);
                        } else {
                            System.out.println("\nfigure out this else case cause.");
                        }

                    }
                    default -> {

                    }
                }
            }
        }
    }
}

