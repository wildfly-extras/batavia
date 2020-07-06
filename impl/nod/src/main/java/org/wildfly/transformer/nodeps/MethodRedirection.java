/*
 * Copyright 2020 Red Hat, Inc, and individual contributors.
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
package org.wildfly.transformer.nodeps;

/**
 * <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class MethodRedirection {

    /**
     * First dimension is number of defined bijections.
     * Second dimension is <code>2</code>>, where:
     * <ul>
     *     <li>Index <code>0</code> represents old method call we are going to eliminate</li>
     *     <li>Index <code>1</code> represents new method call we are going to introduce</li>
     * </ul>
     */
    static final MethodDescriptor[][] MAPPING;

    static {
        MAPPING = new MethodDescriptor[9][2];
        int row = 0;
        /////
        // Class static methods bijection
        /////
        MAPPING[row][0] = new MethodDescriptor(true, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
        MAPPING[row][1] = new MethodDescriptor(true, "org/wildfly/transformer/nodeps/JavaLangTransformUtils", "Class_forName", "(Ljava/lang/String;)Ljava/lang/Class;");
        row++;
        MAPPING[row][0] = new MethodDescriptor(true, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
        MAPPING[row][1] = new MethodDescriptor(true, "org/wildfly/transformer/nodeps/JavaLangTransformUtils", "Class_forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
        row++;
        /////
        // ClassLoader instance methods bijection
        /////
        MAPPING[row][0] = new MethodDescriptor(false, "java/lang/ClassLoader", "getResource", "(Ljava/lang/String;)Ljava/net/URL;");
        MAPPING[row][1] = new MethodDescriptor(true, "org/wildfly/transformer/nodeps/JavaLangTransformUtils", "ClassLoader_getResource", "(Ljava/lang/ClassLoader;Ljava/lang/String;)Ljava/net/URL;");
        row++;
        MAPPING[row][0] = new MethodDescriptor(false, "java/lang/ClassLoader", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;");
        MAPPING[row][1] = new MethodDescriptor(true, "org/wildfly/transformer/nodeps/JavaLangTransformUtils", "ClassLoader_getResourceAsStream", "(Ljava/lang/ClassLoader;Ljava/lang/String;)Ljava/io/InputStream;");
        row++;
        MAPPING[row][0] = new MethodDescriptor(false, "java/lang/ClassLoader", "getResources", "(Ljava/lang/String;)Ljava/util/Enumeration;");
        MAPPING[row][1] = new MethodDescriptor(true, "org/wildfly/transformer/nodeps/JavaLangTransformUtils", "ClassLoader_getResources", "(Ljava/lang/ClassLoader;Ljava/lang/String;)Ljava/util/Enumeration;");
        row++;
        MAPPING[row][0] = new MethodDescriptor(false, "java/lang/ClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
        MAPPING[row][1] = new MethodDescriptor(true, "org/wildfly/transformer/nodeps/JavaLangTransformUtils", "ClassLoader_loadClass", "(Ljava/lang/ClassLoader;Ljava/lang/String;)Ljava/lang/Class;");
        row++;
        /////
        // ClassLoader static methods bijection
        /////
        MAPPING[row][0] = new MethodDescriptor(true, "java/lang/ClassLoader", "getSystemResource", "(Ljava/lang/String;)Ljava/net/URL;");
        MAPPING[row][1] = new MethodDescriptor(true, "org/wildfly/transformer/nodeps/JavaLangTransformUtils", "ClassLoader_getSystemResource", "(Ljava/lang/String;)Ljava/net/URL;");
        row++;
        MAPPING[row][0] = new MethodDescriptor(true, "java/lang/ClassLoader", "getSystemResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;");
        MAPPING[row][1] = new MethodDescriptor(true, "org/wildfly/transformer/nodeps/JavaLangTransformUtils", "ClassLoader_getSystemResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;");
        row++;
        MAPPING[row][0] = new MethodDescriptor(true, "java/lang/ClassLoader", "getSystemResources", "(Ljava/lang/String;)Ljava/util/Enumeration;");
        MAPPING[row][1] = new MethodDescriptor(true, "org/wildfly/transformer/nodeps/JavaLangTransformUtils", "ClassLoader_getSystemResources", "(Ljava/lang/String;)Ljava/util/Enumeration;");
        row++;
    }

}
