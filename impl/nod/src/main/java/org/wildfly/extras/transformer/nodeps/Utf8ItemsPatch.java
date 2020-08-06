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

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class Utf8ItemsPatch {

    final int diffInBytes;
    final List<int[]> utf8ItemPatches;

    private Utf8ItemsPatch(final int diffInBytes, final List<int[]> utf8ItemPatches) {
        this.diffInBytes = diffInBytes;
        this.utf8ItemPatches = utf8ItemPatches;
    }

    static Utf8ItemsPatch of(final byte[] clazz, final ConstantPoolRefs cpRefs, final Utf8InfoMapping utf8Mapping) {
        int diffInBytes = 0;
        List<int[]> utf8ItemPatches = null;
        int[] patch;
        for (int i = 1; i < cpRefs.getSize(); i++) {
            if (cpRefs.isUtf8(i)) {
                // processing Utf8 constant pool item
                patch = getUtf8Patch(clazz, cpRefs, i, utf8Mapping);
                if (patch != null) {
                    if (utf8ItemPatches == null) {
                        utf8ItemPatches = new ArrayList<>(ClassFileUtils.countUtf8Items(cpRefs));
                    }
                    diffInBytes += patch[1];
                    utf8ItemPatches.add(patch);
                }
            }
        }
        return utf8ItemPatches != null ? new Utf8ItemsPatch(diffInBytes, utf8ItemPatches) : null;
    }

    /**
     * Returns <code>patch info</code> if patches were detected or <code>null</code> if there is no patch applicable.
     * Every <code>patch info</code> has the following format:
     * <p>
     *     <pre>
     *        +-------------+ PATCH INFO STRUCTURE HEADER
     *        | integer 0   | holds <code>Constant_Utf8_info</code> index inside class's <code>constant pool</code> table
     *        | integer 1   | holds <code>CONSTANT_Utf8_info</code> structure difference in bytes after applied patches
     *        +-------------+ PATCH INFO STRUCTURE DATA
     *        | integer 2   | holds non-zero mapping index in mapping tables of 1-st applied patch
     *        | integer 3   | holds index of 1-st patch start inside bytes section of original <code>CONSTANT_Utf8_info</code> structure
     *        +-------------+
     *        | integer 4   | holds non-zero mapping index in mapping tables of 2-nd applied patch
     *        | integer 5   | holds index of 2-nd patch start inside bytes section of original <code>CONSTANT_Utf8_info</code> structure
     *        +-------------+
     *        |    ...      | etc
     *        |             |
     *        +-------------+
     *        | integer N-1 | mapping index equal to zero indicates premature <code>patch info</code> structure end
     *        | integer N   |
     *        +-------------+
     *     </pre>
     * </p>
     *
     * @param clazz class byte code
     * @param cpRefs constant pool pointers
     * @param cpIndex UTF-8 constant pool item index
     * @return
     */
    private static int[] getUtf8Patch(final byte[] clazz, final ConstantPoolRefs cpRefs, final int cpIndex, final Utf8InfoMapping mapping) {
        final int utf8Length = cpRefs.getUtf8_Length(cpIndex);
        final int offset = cpRefs.getUtf8_BytesRef(cpIndex);
        final int limit = offset + utf8Length;
        int[] retVal = null;
        int mappingIndex;
        int patchIndex = 2;

        for (int i = offset; i <= limit - mapping.min; i++) {
            for (int j = 1; j < mapping.from.length; j++) {
                if (limit - i < mapping.from[j].length) continue;
                mappingIndex = j;
                for (int k = 0; k < mapping.from[j].length; k++) {
                    if (clazz[i + k] != mapping.from[j][k]) {
                        mappingIndex = 0;
                        break;
                    }
                }
                if (mappingIndex != 0) {
                    if (retVal == null) {
                        retVal = new int[(((limit - i) / mapping.min) + 1) * 2];
                        retVal[0] = cpIndex;
                    }
                    retVal[patchIndex++] = mappingIndex;
                    retVal[patchIndex++] = i - offset;
                    retVal[1] += mapping.to[mappingIndex].length - mapping.from[mappingIndex].length;
                    i += mapping.from[j].length;
                }
            }
        }

        return retVal;
    }

}
