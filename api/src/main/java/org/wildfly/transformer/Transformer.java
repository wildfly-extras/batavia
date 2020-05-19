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

/**
 * Resource transformer can be used concurrently by multiple threads as instances of this class are thread safe.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface Transformer {

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
    Resource[] transform(final Resource r);

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
