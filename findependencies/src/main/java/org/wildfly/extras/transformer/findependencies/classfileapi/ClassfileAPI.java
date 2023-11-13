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
                System.out.println("args:" + arg + ":" + args[looper + 1]);
                final File inJarFile = new File(args[++looper]);
                System.out.println("input file = " + inJarFile + " file exist check = " + inJarFile.exists());
                Classfile cf = Classfile.of();
                System.out.println("Classfile = " + cf);
                Path path = inJarFile.toPath();
                ClassModel cm = null;
                try {
                    cm = cf.parse(path);
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                    throw throwable;
                }
                System.out.println("ClassModel = " + cm);
                for (ClassElement ce : cm) {
                    System.out.println("ClassElement = " + cm);
                    switch (ce) {
                        case MethodModel mm -> System.out.printf("Method %s%n", mm.methodName().stringValue());
                        case FieldModel fm -> System.out.printf("Field %s%n", fm.fieldName().stringValue());
                        default -> {
                        }
                    }
                }
                Set<String> classnames = ClassReference.getClassNames();
                for (String classname : classnames) {
                    System.out.printf("Class %s methods : %s are used by %s\n", classname, ClassReference.getMethodsReferenced(classname), inJarFile.getName());
                }
                System.out.printf("finished " + inJarFile + " testing...");
                // clear for the next set of options
                filter = null;
                ClassReference.clear();
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
}

