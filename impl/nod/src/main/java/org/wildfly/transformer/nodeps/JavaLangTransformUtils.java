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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class template that will be used for creation of package private utility methods.
 * It covers some public (static) calls of methods from classes located in <code>java.lang</code> package.
 * 
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class JavaLangTransformUtils {

    private static final Map<String, String> MAPPINGS;

    static {
        MAPPINGS = new HashMap<>();
        MAPPINGS.put("KEY", "VALUE");
    }

    private JavaLangTransformUtils() {
        // forbidden instantiation
    }

    private static String transform(final String old) {
        if (old != null) {
            for (Map.Entry<String, String> mapping : MAPPINGS.entrySet()) {
                if (old.startsWith(mapping.getKey())) {
                    return mapping.getValue() + old.substring(mapping.getKey().length());
                }
            }
        }
        return old;
    }

    /////
    // java.lang.Class redirected static methods
    /////

    static Class<?> Class_forName(final String oldClassName) throws ClassNotFoundException {
        return Class.forName(transform(oldClassName));
    }

    static Class<?> Class_forName(final String oldClassName, final boolean initialize, final ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(transform(oldClassName), initialize, loader);
    }

    /////
    // java.lang.ClassLoader redirected instance methods
    /////

    static URL ClassLoader_getResource(final ClassLoader classLoader, final String oldName) {
        return classLoader.getResource(transform(oldName));
    }

    static InputStream ClassLoader_getResourceAsStream(final ClassLoader classLoader, final String oldName) {
        return classLoader.getResourceAsStream(transform(oldName));
    }

    static Enumeration<URL> ClassLoader_getResources(final ClassLoader classLoader, final String oldName) throws IOException {
        return classLoader.getResources(transform(oldName));
    }

    static Class<?> ClassLoader_loadClass(final ClassLoader classLoader, final String oldName) throws ClassNotFoundException {
        return classLoader.loadClass(transform(oldName));
    }

    /////
    // java.lang.ClassLoader redirected static methods
    /////

    static URL ClassLoader_getSystemResource(final String oldName) {
        return ClassLoader.getSystemResource(transform(oldName));
    }

    static InputStream ClassLoader_getSystemResourceAsStream(final String oldName) {
        return ClassLoader.getSystemResourceAsStream(transform(oldName));
    }

    static Enumeration<URL> ClassLoader_getSystemResources(final String oldName) throws IOException {
        return ClassLoader.getSystemResources(transform(oldName));
    }

}
