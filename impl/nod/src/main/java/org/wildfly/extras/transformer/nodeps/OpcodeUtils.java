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

import static java.lang.Integer.toHexString;
import static org.wildfly.extras.transformer.nodeps.Opcodes.*;

/**
 * <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class OpcodeUtils {

    static final int MASK_FF = 0xFF;
    private static final int MASK_F0 = 0xF0;
    private static final int[] OP_CODES_SIZE = {
            1,                            //   0
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, //   1 -  10
            1, 1, 1, 1, 1, 2, 3, 2, 3, 3, //  11 -  20
            2, 2, 2, 2, 2, 1, 1, 1, 1, 1, //  21 -  30
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, //  31 -  40
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, //  41 -  50
            1, 1, 1, 2, 2, 2, 2, 2, 1, 1, //  51 -  60
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, //  61 -  70
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, //  71 -  80
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, //  81 -  90
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, //  91 - 100
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, // 101 - 110
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, // 111 - 120
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, // 121 - 130
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, // 131 - 140
            1, 3, 1, 1, 1, 1, 1, 1, 1, 1, // 141 - 150
            1, 1, 3, 3, 3, 3, 3, 3, 3, 3, // 151 - 160
            1, 1, 3, 3, 3, 3, 3, 3, 2, 0, // 161 - 170 (position 170 - zero value indicates special handling)
            0, 1, 1, 1, 1, 1, 1, 3, 3, 3, // 171 - 180 (position 171 - zero value indicates special handling)
            3, 3, 3, 3, 5, 5, 3, 2, 3, 1, // 181 - 190
            1, 3, 3, 1, 1, 0, 4, 3, 3, 5, // 191 - 200 (position 196 - zero value indicates special handling)
            5,
    };

    private OpcodeUtils() {
        // forbidden instantiation
    }

    static void printMethodByteCode(final byte[] clazz, final MethodInfoRefs method) {
        if (method == null) return;
        final CodeAttributeRefs codeAttr = method.getCodeAttribute();
        if (codeAttr == null) return;
        System.out.print("Method implementation byte code: ");
        int unsignedByte;
        for (int i = codeAttr.getCodeStartRef(); i < codeAttr.getCodeEndRef(); i++) {
            if (i != codeAttr.getCodeStartRef()) System.out.print("_");
            unsignedByte = MASK_FF & clazz[i];
            System.out.print(((unsignedByte & MASK_F0) == 0 ? "0" : "") + toHexString(unsignedByte));
        }
        System.out.println();
    }

    static void printMethodByteCode(final byte[] clazz, final int offset, final int length) {
        int unsignedByte;
        for (int i = offset; i < offset + length; i++) {
            if (i != offset) System.out.print("_");
            unsignedByte = MASK_FF & clazz[i];
            System.out.print(((unsignedByte & MASK_F0) == 0 ? "0" : "") + toHexString(unsignedByte));
        }
    }

    static void printMethodOpcodes(final byte[] clazz, final MethodInfoRefs method) {
        if (method == null) return;
        final CodeAttributeRefs codeAttr = method.getCodeAttribute();
        if (codeAttr == null) return;
        System.out.println("Method implementation opcodes: ");
        int unsignedByte;
        for (int i = codeAttr.getCodeStartRef(); i < codeAttr.getCodeEndRef();) {
            unsignedByte = MASK_FF & clazz[i];
            System.out.println(i + ": " + ((unsignedByte & MASK_F0) == 0 ? "0" : "") + toHexString(unsignedByte));
            i+= instructionBytesCount(clazz, i);
        }
    }

    static int instructionBytesCount(final byte[] clazz, final int position) {
        final int opCode = MASK_FF & clazz[position];
        final int retVal = OP_CODES_SIZE[opCode];
        if (retVal != 0) return retVal;
        // otherwise zero indicates special handling (additional computation) is needed
        if (opCode == TABLESWITCH) {
            final int paddingSize = 3 - (position % 4);
            final int lowIdx = position + paddingSize + 4;
            final int low = ClassFileUtils.readUnsignedInt(clazz, lowIdx);
            final int highIdx = lowIdx + 4;
            final int high = ClassFileUtils.readUnsignedInt(clazz, highIdx);
            return 1 + paddingSize + 4 + 4 + 4 + 4 * (high - low + 1);
        }
        if (opCode == LOOKUPSWITCH) {
            final int paddingSize = 3 - (position % 4);
            final int npairsIdx = position + paddingSize + 4;
            final int npairs = ClassFileUtils.readUnsignedInt(clazz, npairsIdx);
            return 1 + paddingSize + 4 + 4 + 8 * (npairs);
        }
        if (opCode == WIDE) {
            final int nextOpCode = MASK_FF & clazz[position+1];
            return nextOpCode == IINC ? 5 : 3;
        }
        throw new UnsupportedOperationException();
    }

}
