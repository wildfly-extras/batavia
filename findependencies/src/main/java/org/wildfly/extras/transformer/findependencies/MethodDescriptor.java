/*
 * Copyright 2023 Red Hat, Inc, and individual contributors.
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
package org.wildfly.extras.transformer.findependencies;

/**
 * <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class MethodDescriptor {

    static final MethodDescriptor STATIC_INIT = new MethodDescriptor(true, null, "<clinit>", "()V");

    final boolean isStatic;
    final byte[] className;
    final byte[] methodName;
    final byte[] methodDescriptor;

    MethodDescriptor(final boolean isStatic, final String className, final String methodName, final String methodDescriptor) {
        this.isStatic = isStatic;
        this.className = className != null ? ClassFileUtils.stringToUtf8(className) : null;
        this.methodName = ClassFileUtils.stringToUtf8(methodName);
        this.methodDescriptor = ClassFileUtils.stringToUtf8(methodDescriptor);
    }

}
