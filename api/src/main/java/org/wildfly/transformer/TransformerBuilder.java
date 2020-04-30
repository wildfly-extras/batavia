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
package org.wildfly.transformer;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.lang.Thread.currentThread;

/**
 * Resource transformer builder instance can be manipulated only by thread that created it.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
public abstract class TransformerBuilder {
    private static final String DEFAULT_CONFIG = "default.mapping";
    private static final int MAX_MAPPINGS = 0xFFFF;
    private static final char DOT = '.';
    private static final char SEP = '/';
    private final Thread thread;
    private final Map<String, String> mappingWithSeps;
    private final Map<String, String> mappingWithDots;
    private InputStream mappingFile;
    private boolean built;

    protected TransformerBuilder() {
        thread = currentThread();
        mappingWithSeps = new HashMap<>();
        mappingWithDots = new HashMap<>();
    }

    /**
     * Adds custom packages mapping defined in a configuration file.
     * First this method tries to read the specified file from the file system.
     * If file doesn't exist on the file system then class loader resources are inspected.
     * The specified file <code>config</code> must exist either on the  file system or
     * in class loader resources otherwise exception is thrown.
     * Once this method is called it turns off <i>default packages mapping configuration</i>
     * and <i>user provided configuration</i> is used instead.
     *
     * @param config packages mapping configuration file
     * @return this builder instance
     * @throws ConcurrentModificationException if this builder instance is used by multiple threads
     * @throws IllegalStateException if either this or {@link #build()} method have been already called
     * @throws IllegalArgumentException if method parameter is <code>null</code>
     * or if method parameter equals to <code>empty string</code>
     * or if file doesn't exist neither on the file system nor in class loader resources
     * @throws IOException if some I/O error occurs while reading the configuration file <code>config</code>
     */
    public final TransformerBuilder setPackagesMapping(final String config) throws IOException {
        // preconditions
        if (thread != currentThread()) throw new ConcurrentModificationException("Builder instance used by multiple threads");
        if (built) throw new IllegalStateException("Builder instance have been already closed");
        if (mappingFile != null) throw new IllegalStateException("This method can be called only once");
        if (config == null || "".equals(config)) throw new IllegalArgumentException("Parameter cannot be neither null nor empty string");
        // implementation
        final File userConfig = new File(config);
        if (userConfig.exists() && userConfig.isFile()) {
            mappingFile = new FileInputStream(userConfig);
        } else {
            mappingFile = TransformerBuilder.class.getResourceAsStream(config);
        }
        if (mappingFile == null) throw new IllegalArgumentException("Couldn't find specified config file neither on file system nor on class path");
        return this;
    }

    /**
     * Creates new resource transformer and closes this builder instance.
     *
     * @return new resource transformer
     * @throws ConcurrentModificationException if this builder instance is used by multiple threads
     * @throws IllegalStateException if this method have been already called or
     * there was no packages mapping defined in configuration file or
     * if packages mapping count in configuration file surpasses value <code>65535</code>
     * @throws IllegalArgumentException if configuration file has invalid format or it contains identical package mapping
     * or if some package defined in one package mapping is a substring of package in another package mapping
     * @throws IOException if configuration reading process failed with unexpected I/O error
     */
    public final Transformer build() throws IOException {
        // preconditions
        if (thread != currentThread()) throw new ConcurrentModificationException("Builder instance used by multiple threads");
        if (built) throw new IllegalStateException("Builder instance have been already closed");
        // implementation
        built = true;
        try {
            if (mappingFile == null) {
                mappingFile = TransformerBuilder.class.getResourceAsStream(SEP + DEFAULT_CONFIG);
            }
            final Properties packagesMapping = new Properties();
            packagesMapping.load(mappingFile);
            String to;
            for (String from : packagesMapping.stringPropertyNames()) {
                to = packagesMapping.getProperty(from);
                if (to.indexOf(DOT) != -1 || from.indexOf(DOT) != -1) {
                    throw new IllegalArgumentException("Packages mapping config file must be property file in path separator format only");
                }
                addMapping(from, to);
            }
            if (mappingWithSeps.size() == 0) throw new IllegalStateException("No mapping was defined in packages mapping config file");
        } finally {
            safeClose(mappingFile);
        }

        return newInstance(mappingWithSeps, mappingWithDots);
    }

    /**
     * Creates new transformer instance.
     *
     * @param mappingWithSeps packages mapping in path separator form
     * @param mappingWithDots packages mapping in dot form
     * @return new transformer instance
     */
    public abstract Transformer newInstance(final Map<String, String> mappingWithSeps, final Map<String, String> mappingWithDots);

    private void addMapping(final String from, final String to) {
        if (from == null || to == null) throw new IllegalArgumentException("Package definition cannot be null");
        if (from.length() == 0 || to.length() == 0) throw new IllegalArgumentException("Package definition cannot be empty string");
        if (from.equals(to)) throw new IllegalArgumentException("Identical package mapping detected: " + from + " -> " + to);
        for (String key : mappingWithSeps.keySet()) {
            if (key.contains(from)) throw new IllegalArgumentException("Package " + from + " is substring of package " + key);
            if (from.contains(key)) throw new IllegalArgumentException("Package " + key + " is substring of package " + from);
        }
        if (mappingWithSeps.size() > MAX_MAPPINGS) throw new IllegalStateException("Packages mapping count exceeded value " + MAX_MAPPINGS);
        mappingWithSeps.put(from, to);
        mappingWithDots.put(from.replace(SEP, DOT), to.replace(SEP, DOT));
    }

    private static void safeClose(final Closeable c) {
        try {
            if (c != null) c.close();
        } catch (final Throwable ignored) {}
    }

}
