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

import static org.wildfly.transformer.nodeps.ConstantPoolTags.STRING;
import static org.wildfly.transformer.nodeps.ConstantPoolTags.UTF8;
import static org.wildfly.transformer.nodeps.ClassFileUtils.writeUnsignedShort;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class AddMappingPatch {

    final int diffInBytes;
    final int currentPoolSize;
    final byte[] poolEndPatch;
    final MethodsPatch methodsPatch;

    private AddMappingPatch(final int currentPoolSize, final byte[] poolEndPatch, final MethodsPatch methodsPatch) {
        this.currentPoolSize = currentPoolSize;
        this.poolEndPatch = poolEndPatch;
        this.methodsPatch = methodsPatch;
        diffInBytes = poolEndPatch.length + methodsPatch.diffInBytes;
    }

    static AddMappingPatch of(final byte[] clazz, final ClassFileRefs cfRefs, final Utf8InfoMapping mapping) {
        final int previousPoolSize = cfRefs.getConstantPool().getSize();
        int currentPoolSize = previousPoolSize;
        final int mappingSize = mapping.from.length;
        int patchSize = 0;
        patchSize += (mappingSize - 1) * 3; // mapping from STRINGs
        patchSize += (mappingSize - 1) * 3; // mapping to STRINGs
        for (int i = 1; i < mappingSize; i++) patchSize += (3 + mapping.from[i].length); // mapping from UTF8s
        for (int i = 1; i < mappingSize; i++) patchSize += (3 + mapping.to[i].length); // mapping to UTF8s
        final byte[] poolEndPatch = new byte[patchSize];
        int index = 0;
        final int[][] mappingStrings = new int[mappingSize - 1][2]; // STRING mapping used later by LDC_W instruction
        for (int i = 1; i < mappingSize; i++) {
            // write STRING_info into constant pool for string we are mapping from
            poolEndPatch[index++] = STRING;
            writeUnsignedShort(poolEndPatch, index, currentPoolSize + 2);
            index += 2;
            mappingStrings[i - 1][0] = currentPoolSize;
            currentPoolSize++;
            // write STRING_info into constant pool for string we are mapping to
            poolEndPatch[index++] = STRING;
            writeUnsignedShort(poolEndPatch, index, currentPoolSize + 2);
            index += 2;
            mappingStrings[i - 1][1] = currentPoolSize;
            currentPoolSize++;
            // write UTF8_info into constant pool for string we are mapping from
            poolEndPatch[index++] = UTF8;
            writeUnsignedShort(poolEndPatch, index, mapping.from[i].length);
            index += 2;
            System.arraycopy(mapping.from[i], 0, poolEndPatch, index, mapping.from[i].length);
            index += mapping.from[i].length;
            currentPoolSize++;
            // write UTF8_info into constant pool for string we are mapping to
            poolEndPatch[index++] = UTF8;
            writeUnsignedShort(poolEndPatch, index, mapping.to[i].length);
            index += 2;
            System.arraycopy(mapping.to[i], 0, poolEndPatch, index, mapping.to[i].length);
            index += mapping.to[i].length;
            currentPoolSize++;
        }
        final MethodsPatch methodsPatch = MethodsPatch.getPatchForAddingMappingToUtilityClass(clazz, cfRefs, mappingStrings);
        return new AddMappingPatch(currentPoolSize, poolEndPatch, methodsPatch);
    }

}
