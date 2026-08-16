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

import static org.wildfly.extras.transformer.findependencies.ClassFileUtils.readUnsignedInt;
import static org.wildfly.extras.transformer.findependencies.ClassFileUtils.readUnsignedShort;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class CodeAttributeRefs {

    static final String ATTRIBUTE_NAME = "Code";
    private final int attributeStartRef;
    private final int attributeEndRef;
    private final int attributeNameIndexRef;
    private final int attributeNameIndex;
    private final int attributeLengthRef;
    private final int attributeLength;
    private final int maxStackRef;
    private final int maxStack;
    private final int maxLocalsRef;
    private final int maxLocals;
    private final int codeLengthRef;
    private final int codeLength;
    private final int codeStartRef;
    private final int codeEndRef;

    private CodeAttributeRefs(final byte[] clazz, final int attributeStartRef) {
        this.attributeStartRef = attributeStartRef;
        attributeNameIndexRef = attributeStartRef;
        attributeNameIndex = readUnsignedShort(clazz, attributeNameIndexRef);
        attributeLengthRef = attributeNameIndexRef + 2;
        attributeLength = readUnsignedInt(clazz, attributeLengthRef);
        maxStackRef = attributeLengthRef + 4;
        maxStack = readUnsignedShort(clazz, maxStackRef);
        maxLocalsRef = maxStackRef + 2;
        maxLocals = readUnsignedShort(clazz, maxLocalsRef);
        codeLengthRef = maxLocalsRef + 2;
        codeLength = readUnsignedInt(clazz, codeLengthRef);
        codeStartRef = codeLengthRef + 4;
        codeEndRef = codeStartRef + codeLength;
        attributeEndRef = attributeStartRef + attributeLength;
    }

    int getAttributeStartRef() {
        return attributeStartRef;
    }

    int getAttributeEndRef() {
        return attributeEndRef;
    }

    int getAttributeNameIndexRef() {
        return attributeNameIndexRef;
    }

    int getAttributeNameIndex() {
        return attributeNameIndex;
    }

    int getAttributeLengthRef() {
        return attributeLengthRef;
    }

    int getAttributeLength() {
        return attributeLength;
    }

    int getMaxStackRef() {
        return maxStackRef;
    }

    int getMaxStack() {
        return maxStack;
    }

    int getMaxLocalsRef() {
        return maxLocalsRef;
    }

    int getMaxLocals() {
        return maxLocals;
    }

    int getCodeLengthRef() {
        return codeLengthRef;
    }

    int getCodeLength() {
        return codeLength;
    }

    int getCodeStartRef() {
        return codeStartRef;
    }

    int getCodeEndRef() {
        return codeEndRef;
    }

    static CodeAttributeRefs of(final byte[] clazz, final int attributeStartRef) {
        return new CodeAttributeRefs(clazz, attributeStartRef);
    }

}
