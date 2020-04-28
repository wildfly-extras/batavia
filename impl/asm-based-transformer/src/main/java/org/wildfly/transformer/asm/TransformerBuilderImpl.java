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
package org.wildfly.transformer.asm;

import static java.lang.Thread.currentThread;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.wildfly.transformer.Transformer;
import org.wildfly.transformer.TransformerBuilder;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class TransformerBuilderImpl implements TransformerBuilder {
    private static final String DEFAULT_CONFIG = "default.mapping";
    private static final int MAX_MAPPINGS = 0xFFFF;
    static final char DOT = '.';
    static final char SEP = '/';
    private final Thread thread;
    private final Map<String, String> mapping;
    private InputStream mappingFile;
    private boolean built;

    TransformerBuilderImpl() {
        thread = currentThread();
        mapping = new HashMap<>();
    }

    @Override
    public TransformerBuilder setPackagesMapping(final String config) throws IOException {
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

    public Transformer build() throws IOException {
        // preconditions
        if (thread != currentThread()) throw new ConcurrentModificationException();
        if (built) throw new IllegalStateException();
        // implementation
        built = true;
        try {
            if (mappingFile == null) {
                mappingFile = TransformerImpl.class.getResourceAsStream(SEP + DEFAULT_CONFIG);
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
            if (mapping.size() == 0) throw new IllegalStateException();
        } finally {
            safeClose(mappingFile);
        }

        return new TransformerImpl(mapping);
    }

    private void addMapping(final String from, final String to) {
        if (from == null || to == null) throw new IllegalArgumentException();
        if (from.length() == 0 || to.length() == 0) throw new IllegalArgumentException();
        if (from.equals(to)) throw new IllegalArgumentException();
        for (String key : mapping.keySet()) {
            if (key.contains(from) || from.contains(key)) throw new IllegalArgumentException();
        }
        if (mapping.size() > MAX_MAPPINGS) throw new IllegalStateException();
        mapping.put(from, to);
    }

    private static void safeClose(final Closeable c) {
        try {
            if (c != null) c.close();
        } catch (final Throwable ignored) {}
    }

}
