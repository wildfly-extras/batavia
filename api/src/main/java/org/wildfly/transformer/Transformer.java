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
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * Class file transformer.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface Transformer extends ClassFileTransformer {

    /**
     * The implementation of this method potentially transforms the supplied class file.
     * If the implementing method detects no transformation is needed it must return <code>null</code>.
     * Otherwise it must create a new <code>byte[]</code> array containing transformed class byte code.
     *
     * @param clazz the bytes of class file (buffer must not be modified)
     * @return either bytes of modified class file or <code>null</code> if no transformation is performed.
     */
    byte[] transform(final byte[] clazz);

    /**
     * {@inheritDoc}
     */
    @Override
    default byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined,
                             final ProtectionDomain protectionDomain, final byte[] classfileBuffer)
            throws IllegalClassFormatException {
        return transform(classfileBuffer);
    }

}
