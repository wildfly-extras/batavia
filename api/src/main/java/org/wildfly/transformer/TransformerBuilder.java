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
 * Resource transformer builder. Instances of this class are thread safe.
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
        if (thread != currentThread()) throw new ConcurrentModificationException();
        if (built || mappingFile != null) throw new IllegalStateException();
        if (config == null || "".equals(config)) throw new IllegalArgumentException();
        // implementation
        final File userConfig = new File(config);
        if (userConfig.exists() && userConfig.isFile()) {
            mappingFile = new FileInputStream(userConfig);
        } else {
            mappingFile = TransformerBuilder.class.getResourceAsStream(config);
        }
        if (mappingFile == null) throw new IllegalArgumentException();
        return this;
    }

    /**
     * Creates new resource transformer.
     *
     * @return new resource transformer
     * @throws ConcurrentModificationException if this builder instance is used by multiple threads
     * @throws IllegalStateException if this method have been already called or
     * there was no packages mapping defined in configuration file or
     * if packages mapping count in configuration file surpasses value <code>65535</code>
     * @throws IllegalArgumentException if configuration file has invalid format or it contains identity package mapping
     * or if some package defined in one package mapping is a substring of package in another package mapping
     * @throws IOException if configuration reading process failed with unexpected I/O error
     */
    public final Transformer build() throws IOException {
        // preconditions
        if (thread != currentThread()) throw new ConcurrentModificationException();
        if (built) throw new IllegalStateException();
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
                    throw new IllegalArgumentException();
                }
                addMapping(from, to);
            }
            if (mappingWithSeps.size() == 0) throw new IllegalStateException();
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
        if (from == null || to == null) throw new IllegalArgumentException();
        if (from.length() == 0 || to.length() == 0) throw new IllegalArgumentException();
        if (from.equals(to)) throw new IllegalArgumentException();
        for (String key : mappingWithSeps.keySet()) {
            if (key.contains(from) || from.contains(key)) throw new IllegalArgumentException();
        }
        if (mappingWithSeps.size() > MAX_MAPPINGS) throw new IllegalStateException();
        mappingWithSeps.put(from, to);
        mappingWithDots.put(from.replace(SEP, DOT), to.replace(SEP, DOT));
    }

    private static void safeClose(final Closeable c) {
        try {
            if (c != null) c.close();
        } catch (final Throwable ignored) {}
    }

}
