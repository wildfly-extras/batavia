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

import static jdk.internal.classfile.Classfile.*;

import java.io.File;
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Set;
import java.util.jar.JarFile;

import jdk.internal.classfile.*;
import org.wildfly.extras.transformer.findependencies.ClassReference;
import org.wildfly.extras.transformer.findependencies.Filter;
import org.wildfly.extras.transformer.findependencies.archivefile.Reader;

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

    private static String internalToBinary(String name) {
        return name.replace('/', '.');
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
            className = className.replace('/', '.'); // correct java package name
            System.out.println("Classname = " + className);
            ClassReference.findClassName(className);

            for (ClassElement ce : cm) {
//                className = ce.thisClass().name().stringValue();
//                className = className.replace('/','.'); // correct java package name
//                ClassReference.findClassName(className);
                switch (ce) {
                    case MethodModel mm -> {
                        MethodTypeDesc methodTypeDesc = mm.methodTypeSymbol();

                        if (mm.parent().isPresent()) { // if we know the class model that called method is part of
                            ClassModel methodIsInClass = mm.parent().get();
                            String methodName = mm.methodName().stringValue();
                            String methodDescriptor = mm.methodType().stringValue();
                            methodDescriptor = dropFirstAndLastChar(methodDescriptor);
                            String parentClassName = methodIsInClass.thisClass().name().stringValue();
                            parentClassName = internalToBinary(parentClassName);
                            ClassReference targetMethodIsInClass = ClassReference.findClassName(dropFirstAndLastChar(parentClassName));
                            targetMethodIsInClass.addMethod(methodName, methodDescriptor);
                        }

                        ClassDesc returnTypeClassDesc = methodTypeDesc.returnType();
                        String returnTypeDescriptor = returnTypeClassDesc.descriptorString();
                        returnTypeDescriptor = internalToBinary(returnTypeDescriptor);
                        // record first reference to return type class
                        ClassReference.findClassName(dropFirstAndLastChar(returnTypeDescriptor));

                        for (int parameterCount = methodTypeDesc.parameterCount(); parameterCount > 0; parameterCount--) {
                            ClassDesc parameterClassDesc = methodTypeDesc.parameterType(parameterCount - 1);
                            String parameterDescriptor = internalToBinary(parameterClassDesc.descriptorString());
                            // record first reference to each parameter class type
                            ClassReference.findClassName(dropFirstAndLastChar(parameterDescriptor));
                        }
                    }
                    case FieldModel fm -> {
                        String fieldName = fm.fieldName().stringValue();
                        String fieldDescriptor = fm.fieldType().stringValue();
                        fieldDescriptor = dropFirstAndLastChar(fieldDescriptor);
                        ClassReference classReference = ClassReference.findClassName(fieldDescriptor);
                        classReference.addField(fieldName, fieldDescriptor);
                        System.out.println("fieldName " + fieldName + " of type " + fieldDescriptor);
                        // if (fm.parent().isPresent()) { // not sure this is needed so ignoring for now
                        // ClassModel fieldIsInClass = fm.parent().get();
                        // String referencedClassName = fieldIsInClass.thisClass().name().stringValue();
                        // }
                    }
                    default -> {

                    }
                }
            }
        }
    }
}

