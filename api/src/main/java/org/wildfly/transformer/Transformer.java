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

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Resource transformer can be used concurrently by multiple threads as instances of this class are thread safe.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface Transformer extends ClassFileTransformer {

    /**
     * The implementation of this method potentially transforms the supplied resource.
     * The resource can be a regular class file or configuration text file or some other kind of files.
     * If the implementing method detects no transformation is needed it must return <code>null</code>.
     * Otherwise it must create a new <code>resource</code> containing transformed resource name or content.
     *
     * @param r the resource to be transformed. The buffer returned by {@link Resource#getData()} method must not be modified.
     * @return either new resource with modified name or content, or <code>null</code> if no transformation is performed.
     */
    Resource transform(final Resource r);

    /**
     * {@inheritDoc}
     */
    @Override
    default byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined,
                             final ProtectionDomain protectionDomain, final byte[] classfileBuffer) {
        final Resource r = transform(new Resource(className, classfileBuffer));
        return r != null ? r.getData() : null;
    }

    /**
     * Resource data.
     */
    final class Resource {
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
