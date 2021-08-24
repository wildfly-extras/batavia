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
package org.wildfly.extras.transformer;

import static java.lang.Thread.currentThread;

import java.io.File;
import java.util.ConcurrentModificationException;

/**
 * Resource transformer builder instance can be manipulated only by thread that created it.
 * Cannot be used concurrently by multiple threads as instances of this class are not thread safe.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
public abstract class TransformerBuilder {

    private final Thread thread = currentThread();
    protected File configsDir;
    protected Boolean verbose;
    protected Boolean invert;
    private boolean built;

    protected TransformerBuilder() {
        // only subclasses can override it
    }

    /**
     * Sets directory where transformer specific external configuration files are located.
     * Once this method is called it turns off <i>default configuration mapping</i>
     * and <i>user provided configuration</i> will be used instead.
     *
     * @param configsDir configuration files directory
     * @return this builder instance
     * @throws ConcurrentModificationException if this builder instance is used by multiple threads
     * @throws IllegalStateException if either {@link #build()} or this method (with given config type) have been already called
     * @throws IllegalArgumentException if method parameter is <code>null</code>
     * or if method parameter equals to <code>empty string</code>
     * or if provided directory doesn't exist
     * or if provided directory name doesn't point to directory
     */
    public final TransformerBuilder setConfigsDir(final String configsDir) {
        // preconditions
        if (thread != currentThread()) throw new ConcurrentModificationException("Builder instance used by multiple threads");
        if (built) throw new IllegalStateException("Builder instance have been already closed");
        if (configsDir == null) throw new IllegalArgumentException("Parameter cannot be null");
        if ("".equals(configsDir)) throw new IllegalArgumentException("Parameter cannot be empty string");
        final File configsDirFile = new File(configsDir);
        if (!configsDirFile.exists()) throw new IllegalArgumentException("Directory '" + configsDir + "' doesn't exist");
        if (!configsDirFile.isDirectory()) throw new IllegalArgumentException("Provided value '" + configsDir + "' is not directory");
        // implementation
        this.configsDir = configsDirFile;
        return this;
    }

    /**
     * Sets verbosity of underlying transformation engine.
     *
     * @param verbose if output should be verbose or not
     * @return this builder instance
     */
    public final TransformerBuilder setVerbose(final boolean verbose) {
        // preconditions
        if (thread != currentThread()) throw new ConcurrentModificationException("Builder instance used by multiple threads");
        if (built) throw new IllegalStateException("Builder instance have been already closed");
        if (this.verbose != null) throw new IllegalStateException("This method can be called only once");
        // implementation
        this.verbose = verbose;
        return this;
    }

    /**
     * Inverts the transformation.
     *
     * @param invert if transformation should be inverted.
     * @return this builder instance
     */
    public final TransformerBuilder setInvert(final boolean invert) {
        // preconditions
        if (thread != currentThread()) throw new ConcurrentModificationException("Builder instance used by multiple threads");
        if (built) throw new IllegalStateException("Builder instance have been already closed");
        if (this.invert != null) throw new IllegalStateException("This method can be called only once");
        // implementation
        this.invert = invert;
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
     */
    public final ArchiveTransformer build() {
        // preconditions
        if (thread != currentThread()) throw new ConcurrentModificationException("Builder instance used by multiple threads");
        if (built) throw new IllegalStateException("Builder instance have been already closed");
        built = true;
        // implementation
        return buildInternal();
    }

    /**
     * Creates new transformer instance.
     *
     * @return new transformer instance
     */
    protected abstract ArchiveTransformer buildInternal();

}
