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
package org.wildfly.extras.transformer.nodeps;

final class Utf8InfoMapping {

    /**
     * Represents strings we are searching for in <code>CONSTANT_Utf8_info</code> structures (encoded in modified UTF-8).
     * Mapping on index <code>zero</code> is undefined. Mappings are defined from index <code>one</code>.
     */
    final byte[][] from;
    /**
     * Represents strings we will replace matches with inside <code>CONSTANT_Utf8_info</code> structures (encoded in modified UTF-8).
     * Mapping on index <code>zero</code> is undefined. Mappings are defined from index <code>one</code>.
     */
    final byte[][] to;

    /**
     * Used for detecting maximum size of internal patch info arrays and for decreasing patch search space.
     */
    final int min;

    Utf8InfoMapping(final byte[][] from, final byte[][] to, final int min) {
        if (from[0] != null || to[0] != null) throw new IllegalArgumentException();
        this.from = from;
        this.to = to;
        this.min = min;
    }

}
