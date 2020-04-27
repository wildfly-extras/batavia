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

import java.io.IOException;
import java.util.ConcurrentModificationException;

/**
 * Resource transformer builder. Instances of this class are thread safe.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
public interface TransformerBuilder {

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
     * @throws IllegalStateException if either {@link #setPackagesMapping(String)} or {@link #build()}
     * have been already called
     * @throws IllegalArgumentException if method parameter is <code>null</code>
     * or if method parameter equals to <code>empty string</code>
     * or if file doesn't exist neither on the file system nor in class loader resources
     * @throws IOException if some I/O error occurs while reading the configuration file <code>config</code>
     */
    TransformerBuilder setPackagesMapping(final String config) throws IOException;

    /**
     * Creates new resource transformer.
     *
     * @return new resource transformer
     * @throws ConcurrentModificationException if this builder instance is used by multiple threads
     * @throws IllegalStateException if {@link #build()} have been already called or
     * there was no packages mapping defined in configuration file or
     * if packages mapping count in configuration file surpasses value <code>65535</code>
     * @throws IllegalArgumentException if configuration file has invalid format or it contains identity package mapping
     * or if some package defined in one package mapping is a substring of package in another package mapping
     * @throws IOException if configuration reading process failed with unexpected I/O error
     */
    Transformer build() throws IOException;

}
