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
package org.wildfly.transformer.nodeps;

import static org.wildfly.transformer.nodeps.ClassFileUtils.readUnsignedInt;
import static org.wildfly.transformer.nodeps.ClassFileUtils.readUnsignedShort;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class MethodInfoRefs {

    private final int methodInfoStartRef;
    private final int accessFlagsRef;
    private final int accessFlags;
    private final int nameIndexRef;
    private final int nameIndex;
    private final int descriptorIndexRef;
    private final int descriptorIndex;
    private final int attributesCountRef;
    private final int attributesCount;
    private final int attributesStartRef;
    private final int attributesEndRef;
    private final CodeAttributeRefs codeAttributeRefs;
    private final int methodInfoEndRef;
    private final int index;

    private MethodInfoRefs(final byte[] clazz, final int methodInfoStartRef, final CodeAttributeRefs codeAttributeRefs, final int methodInfoEndRef, final int index) {
        this.methodInfoStartRef = methodInfoStartRef;
        accessFlagsRef = methodInfoStartRef;
        accessFlags = readUnsignedShort(clazz, accessFlagsRef);
        nameIndexRef = accessFlagsRef + 2;
        nameIndex = readUnsignedShort(clazz, nameIndexRef);
        descriptorIndexRef = nameIndexRef + 2;
        descriptorIndex = readUnsignedShort(clazz, descriptorIndexRef);
        attributesCountRef = descriptorIndexRef + 2;
        attributesCount = readUnsignedShort(clazz, attributesCountRef);
        attributesStartRef = attributesCountRef + 2;
        attributesEndRef = methodInfoEndRef;
        this.codeAttributeRefs = codeAttributeRefs;
        this.methodInfoEndRef = methodInfoEndRef;
        this.index = index;
    }

    int getMethodInfoStartRef() {
        return methodInfoStartRef;
    }

    int getAccessFlagsRef() {
        return accessFlagsRef;
    }

    int getAccessFlags() {
        return accessFlags;
    }

    int getNameIndexRef() {
        return nameIndexRef;
    }

    int getNameIndex() {
        return nameIndex;
    }

    int getDescriptorIndexRef() {
        return descriptorIndexRef;
    }

    int getDescriptorIndex() {
        return descriptorIndex;
    }

    int getAttributesCountRef() {
        return attributesCountRef;
    }

    int getAttributesCount() {
        return attributesCount;
    }

    int getAttributesStartRef() {
        return attributesStartRef;
    }

    int getAttributesEndRef() {
        return attributesEndRef;
    }

    int getIndex() {
        return index;
    }

    CodeAttributeRefs getCodeAttribute() {
        return codeAttributeRefs;
    }

    int getMethodInfoEndRef() {
        return methodInfoEndRef;
    }

    static MethodInfoRefs of(final byte[] clazz, final ConstantPoolRefs cpRefs, final int methodInfoStartRef, final int index) {
        int position = methodInfoStartRef + 6;
        final int attributesCount = readUnsignedShort(clazz, position);
        position += 2;

        int attributeNameIndex;
        int attributeLength;
        int attributeStartRef;
        int attributeEndRef;
        boolean isCodeAttribute;
        CodeAttributeRefs codeAttributeRefs = null;
        for (int j = 0; j < attributesCount; j++) {
            attributeStartRef = position;
            attributeNameIndex = readUnsignedShort(clazz, position);
            position += 2;
            attributeLength = readUnsignedInt(clazz, position);
            position += 4;
            attributeEndRef = position + attributeLength;
            isCodeAttribute = CodeAttributeRefs.ATTRIBUTE_NAME.equals(cpRefs.getUtf8AsString(attributeNameIndex));
            if (isCodeAttribute) {
                codeAttributeRefs = CodeAttributeRefs.of(clazz, attributeStartRef);
            } else {
                // skip non Code method attributes
            }
            position = attributeEndRef;
        }
        return new MethodInfoRefs(clazz, methodInfoStartRef, codeAttributeRefs, position, index);
    }

}
