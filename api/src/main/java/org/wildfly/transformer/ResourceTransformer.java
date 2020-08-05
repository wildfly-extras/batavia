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
import java.io.InputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Resource transformer tries to convert given resource to another resource(s) by applying configured transformation rules.
 * Cannot be used concurrently by multiple threads as instances of this class are not thread safe.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class ResourceTransformer {
    private static final String DEFAULT_CONFIG = "default.mapping";
    private static final int MAX_MAPPINGS = 0xFFFF;
    private static final char DOT = '.';
    private static final char SEP = '/';
    protected final Map<String, String> mappingWithSeps = new HashMap<>();
    protected final Map<String, String> mappingWithDots = new HashMap<>();
    protected final boolean verbose;

    protected ResourceTransformer(final Map<Config, String> configs, final boolean verbose) throws IOException {
        this.verbose = verbose;
        String config = configs.get(Config.PACKAGES_MAPPING);
        if (config == null) {
            config = SEP + DEFAULT_CONFIG;
        }
        final InputStream mappingFile;
        final File userConfig = new File(config);
        if (userConfig.exists() && userConfig.isFile()) {
            mappingFile = new FileInputStream(userConfig);
        } else {
            mappingFile = ResourceTransformer.class.getResourceAsStream(config);
        }
        if (mappingFile == null) throw new IllegalArgumentException("Couldn't find specified config file neither on file system nor on class path");
        try {
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
    }

    private static void safeClose(final Closeable c) {
        try {
            if (c != null) c.close();
        } catch (final Throwable ignored) {}
    }

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

    /**
     * The implementation of this method potentially transforms the supplied resource.
     * The resource can be a regular class file or configuration text file or some other kind of files.
     * If the implementing method detects no transformation is needed it must return <code>empty array</code>.
     * Otherwise it must create a new <code>resource</code> (or new resources) containing transformed resource
     * (potentially additional created resources). If return value contains more than one resource then
     * resource at array index <code>zero</code> is always considered to be replacement of original resource
     * and other resources (since array index of <code>1</code> including) represent additional resources
     * created dynamically that must be added to target environment (e.g. jar archive or defining class loader).
     *
     * @param r the resource to be transformed. The buffer returned by {@link Resource#getData()} method must not be modified.
     * @return either <code>empty array</code> if no transformation is performed or
     * a new resource (or multiple resources) representing transformed resource (or potentially additional created resources)
     */
    protected abstract Resource[] transform(final Resource r);

    /**
     * Resource data.
     */
    public static final class Resource {
        private final String name;
        private final byte[] data;

        /**
         * Constructor
         * @param name resource name
         * @param data resource data
         */
        public Resource(final String name, final byte[] data) {
            if (name == null || data == null) throw new NullPointerException();
            this.name = name;
            this.data = data;
        }

        /**
         * Gets resource name.
         * @return resource name
         */
        public String getName() {
            return this.name;
        }

        /**
         * Gets resource data. The byte buffer returned by this method must not be modified.
         * @return resource data
         */
        public byte[] getData() {
            return data;
        }
    }

}
