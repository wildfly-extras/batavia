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
import java.io.IOException;
import java.util.Set;

import org.wildfly.extras.transformer.findependencies.classfileapi.ClassfileAPI;

/**
 * Main entry point for finding application dependencies.
 *
 * Example usage will run the unit tests first which download Jakarta EE TCK files like jpa_jpa22_repeatable_joincolumns_vehicles.ear.
 * The find command will invoke the org.wildfly.extras.transformer.findependencies.Main driver via Maven for each found EAR file.
 *    cd batavia
 *    mvn clean install
 *    cd findependencies
 *    find -name *.ear -exec mvn exec:java -Dexec.args="-include jakarta -file {}" \; > /tmp/out
 *
 * @author Scott Marlow
 */
public final class Main {

    // Class-File API (Preview) support check https://openjdk.org/jeps/8280389
    // https://bugs.openjdk.org/browse/JDK-8280389 by checking for java.lang.ClassFile
    static final boolean classfileAPI;

    static {
        try {
            System.out.println("Try loading the jdk.internal.classfile.Classfile class");
            classfileAPI = Class.forName("jdk.internal.classfile.Classfile") != null;
            if (classfileAPI == false) {
                System.out.println("Class.forName(\"jdk.internal.classfile.Classfile\") returned: " + Class.forName("jdk.internal.classfile.Classfile"));
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public static void main(final String... args) throws IOException {
        if (classfileAPI) {
            System.out.println("find dependencies using https://openjdk.org/jeps/457:" + args);
        } else {
            System.out.println("find dependencies:" + args);
        }


        if (classfileAPI) {
            // delegate to ClassfileAPI support for Java 21+
            ClassfileAPI.main(args);
            return;
        }

        Filter filter = null;
        for (int looper = 0 ; looper < args.length; looper++) {
            String arg = args[looper];
            if("-include".equals(arg) || "-i".equals(arg)) {
                System.out.println("args:" + arg + ":" + args[looper+1]);
                if (filter == null) {
                    filter = new Filter();
                }
                filter.include(args[++looper]);
            } else if("-file".equals(arg) || "-f".equals(arg)) {
                System.out.println("args:" + arg + ":" + args[looper+1]);
                final File inJarFile = new File(args[++looper]);
                ArchiveTransformerImpl jTrans = new ArchiveTransformerImpl(filter!=null?filter:Filter.defaultFilter());
                jTrans.transform(inJarFile);
                Set<String> classnames =  ClassReference.getClassNames();
                for( String classname:classnames) {
                    System.out.printf("Class %s methods : %s are used by %s\n", classname, ClassReference.getMethodsReferenced(classname),inJarFile.getName());
                }
                // clear for the next set of options
                filter = null;
                ClassReference.clear();
            }
        }

    }

}

