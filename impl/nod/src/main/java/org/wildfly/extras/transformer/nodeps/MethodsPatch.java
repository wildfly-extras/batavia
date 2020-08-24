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

import static org.wildfly.extras.transformer.nodeps.Opcodes.*;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class MethodsPatch {

    private static final byte[] JAVA_UTIL_MAP_CLASS = ClassFileUtils.stringToUtf8("java/util/Map");
    private static final byte[] PUT_METHOD_NAME = ClassFileUtils.stringToUtf8("put");
    private static final byte[] PUT_METHOD_DESCRIPTOR = ClassFileUtils.stringToUtf8("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    private static final byte[] KEY_CONSTANT = ClassFileUtils.stringToUtf8("KEY");
    private static final byte[] VALUE_CONSTANT = ClassFileUtils.stringToUtf8("VALUE");
    /**
     * Minimal byte code sequence size being looked up.
     */
    private static final int MINIMUM = 3;
    final int diffInBytes;
    final List<int[]> methodPatches;

    /**
     * Continuous bytes except of Java VM code that was part of <code>method_info</code>'s code (previous implementation).
     * Mapping on index <code>zero</code> is undefined. Mappings are defined from index <code>one</code>.
     */
    final byte[][] mappingFrom;

    /**
     * Continuous bytes excerpt of Java VM code that will become part of <code>method_info</code>'s code (new implementation).
     * Mapping on index <code>zero</code> is undefined. Mappings are defined from index <code>one</code>.
     */
    final byte[][] mappingTo;

    private MethodsPatch(final int diffInBytes, final List<int[]> methodPatches, final byte[][] mappingFrom, final byte[][] mappingTo) {
        this.diffInBytes = diffInBytes;
        this.methodPatches = methodPatches;
        this.mappingFrom = mappingFrom;
        this.mappingTo = mappingTo;
    }

    static MethodsPatch getPatchForMethodRedirects(final byte[] clazz, final ClassFileRefs cfRefs, final int[][] methodRefRedirects) {
        int diffInBytes = 0;
        List<int[]> methodPatches = null;
        int[] patch;
        final byte[][] mappingFrom = generateMappingFrom(methodRefRedirects, MethodRedirection.MAPPING);
        final byte[][] mappingTo = generateMappingTo(methodRefRedirects, MethodRedirection.MAPPING);
        MethodInfoRefs methodInfo;
        CodeAttributeRefs codeAttribute;
        for (int i = 0; i < cfRefs.getMethodsCount(); i++) {
            methodInfo = cfRefs.getMethod(i);
            codeAttribute = methodInfo.getCodeAttribute();
            if (codeAttribute == null) continue;
            patch = getMethodPatch(clazz, codeAttribute, i,  mappingFrom, mappingTo);
            if (patch != null) {
                if (methodPatches == null) {
                    methodPatches = new ArrayList<>(cfRefs.getMethodsCount());
                }
                diffInBytes += patch[1];
                methodPatches.add(patch);
            }
        }
        return methodPatches != null ? new MethodsPatch(diffInBytes, methodPatches, mappingFrom, mappingTo) : null;
    }

    static MethodsPatch getPatchForAddingMappingToUtilityClass(final byte[] clazz, final ClassFileRefs cfRefs, final int[][] mappingStrings) {
        int diffInBytes = 0;
        final List<int[]> methodPatches = new ArrayList<>(cfRefs.getMethodsCount());
        final int[] patch;
        final byte[][] mappingFrom = generateMappingFrom(cfRefs.getConstantPool());
        final byte[][] mappingTo = generateMappingTo(cfRefs.getConstantPool(), mappingStrings);
        final MethodInfoRefs classInitMethodInfo = cfRefs.getMethod(clazz, MethodDescriptor.STATIC_INIT);
        CodeAttributeRefs codeAttribute = classInitMethodInfo.getCodeAttribute();
        patch = getMethodPatch(clazz, codeAttribute, classInitMethodInfo.getIndex(),  mappingFrom, mappingTo);
        diffInBytes += patch[1];
        methodPatches.add(patch);
        return new MethodsPatch(diffInBytes, methodPatches, mappingFrom, mappingTo);
    }

    private static byte[][] generateMappingFrom(final ConstantPoolRefs cpRefs) {
        int mapPutInterfaceMethodrefIndex = 0;
        int keyConstantIndex = 0;
        int valueConstantIndex = 0;
        int classIndex = 0;
        int classNameIndex = 0;
        int nameAndTypeIndex = 0;
        int nameAndTypeNameIndex = 0;
        int nameAndTypeDescriptorIndex = 0;
        // lookup our 3 important items (Map.put() interface method call, KEY string & VALUE string) in utility class constant pool
        for (int i = 1; i < cpRefs.getSize(); i++) {
            if (cpRefs.isInterfaceMethodRef(i)) {
                classIndex = cpRefs.getInterfaceMethodRef_ClassIndex(i);
                classNameIndex = cpRefs.getClass_NameIndex(classIndex);
                if (!cpRefs.utf8EqualsTo(classNameIndex, JAVA_UTIL_MAP_CLASS)) continue;
                nameAndTypeIndex = cpRefs.getInterfaceMethodRef_NameAndTypeIndex(i);
                nameAndTypeNameIndex = cpRefs.getNameAndType_NameIndex(nameAndTypeIndex);
                if (!cpRefs.utf8EqualsTo(nameAndTypeNameIndex, PUT_METHOD_NAME)) continue;
                nameAndTypeDescriptorIndex = cpRefs.getNameAndType_DescriptorIndex(nameAndTypeIndex);
                if (cpRefs.utf8EqualsTo(nameAndTypeDescriptorIndex, PUT_METHOD_DESCRIPTOR)) {
                    mapPutInterfaceMethodrefIndex = i;
                }
            }
            if (cpRefs.isString(i)) {
                if (cpRefs.utf8EqualsTo(cpRefs.getString_Index(i), KEY_CONSTANT)) keyConstantIndex = i;
                if (cpRefs.utf8EqualsTo(cpRefs.getString_Index(i), VALUE_CONSTANT)) valueConstantIndex = i;
            }
            if (keyConstantIndex > 0 && valueConstantIndex > 0 && mapPutInterfaceMethodrefIndex > 0) break;
        }
        int index = 0;
        // create mappingFrom table
        int mappingTableSize = 2;
        final byte[][] mappingFrom = new byte[mappingTableSize][];
        mappingFrom[1] = new byte[2 + 2 + 5]; // ldc + ldc + invokeinterface instructions size
        // load KEY from constant pool
        mappingFrom[1][index++] = LDC;
        mappingFrom[1][index++] = (byte) keyConstantIndex;
        // load VALUE from constant pool
        mappingFrom[1][index++] = LDC;
        mappingFrom[1][index++] = (byte) valueConstantIndex;
        // invoke Map.put() interface method
        mappingFrom[1][index++] = (byte) INVOKEINTERFACE;
        ClassFileUtils.writeUnsignedShort(mappingFrom[1], index, mapPutInterfaceMethodrefIndex);
        index += 2;
        mappingFrom[1][index++] = 3; // due to historical reasons
        mappingFrom[1][index++] = 0; // due to historical reasons

        // mappingFrom table completed
        return mappingFrom;
    }

    private static byte[][] generateMappingFrom(final int[][] methodRefRedirects, final MethodDescriptor[][] methodMapping) {
        // detect mappingFrom table size
        int mappingTableSize = 1;
        for (int i = 0; i < methodRefRedirects.length; i++) {
            if (methodRefRedirects[i][0] != 0) mappingTableSize += 1;

        }
        final byte[][] mappingFrom = new byte[mappingTableSize][];
        int mappingIndex = 0;
        // generate mappingFrom table dynamically (depends on discovered methodRef indices in class constant pool)
        for (int i = 0; i < methodMapping.length; i++) {
            int methodRefIndex = methodRefRedirects[i][0];
            if (methodRefIndex != 0) {
                mappingIndex += 1;
                mappingFrom[mappingIndex] = new byte[3];
                mappingFrom[mappingIndex][0] = (byte) (methodMapping[i][0].isStatic ? INVOKESTATIC : INVOKEVIRTUAL);
                ClassFileUtils.writeUnsignedShort(mappingFrom[mappingIndex], 1, methodRefIndex);
            }
        }
        // mappingFrom table completed
        return mappingFrom;
    }

    private static byte[][] generateMappingTo(final ConstantPoolRefs cpRefs, final int[][] stringMappings) {
        int mapPutInterfaceMethodrefIndex = 0;
        int classIndex = 0;
        int classNameIndex = 0;
        int nameAndTypeIndex = 0;
        int nameAndTypeNameIndex = 0;
        int nameAndTypeDescriptorIndex = 0;
        // lookup our 3 important items (Map.put() interface method call, KEY string & VALUE string) in utility class constant pool
        for (int i = 1; i < cpRefs.getSize(); i++) {
            if (cpRefs.isInterfaceMethodRef(i)) {
                classIndex = cpRefs.getInterfaceMethodRef_ClassIndex(i);
                classNameIndex = cpRefs.getClass_NameIndex(classIndex);
                if (!cpRefs.utf8EqualsTo(classNameIndex, JAVA_UTIL_MAP_CLASS)) continue;
                nameAndTypeIndex = cpRefs.getInterfaceMethodRef_NameAndTypeIndex(i);
                nameAndTypeNameIndex = cpRefs.getNameAndType_NameIndex(nameAndTypeIndex);
                if (!cpRefs.utf8EqualsTo(nameAndTypeNameIndex, PUT_METHOD_NAME)) continue;
                nameAndTypeDescriptorIndex = cpRefs.getNameAndType_DescriptorIndex(nameAndTypeIndex);
                if (cpRefs.utf8EqualsTo(nameAndTypeDescriptorIndex, PUT_METHOD_DESCRIPTOR)) {
                    mapPutInterfaceMethodrefIndex = i;
                }
            }
            if (mapPutInterfaceMethodrefIndex > 0) break;
        }
        int index = 0;
        // create mappingTo table
        int mappingTableSize = 2;
        final byte[][] mappingTo = new byte[mappingTableSize][];
        final int countOfMappings = stringMappings.length;
        mappingTo[1] = new byte[(3 + 3 + 5) * countOfMappings]; // ldc_w + ldc_w + invokeinterface instructions size for every remapping
        for (int i = 0; i < stringMappings.length; i++) {
            // generate 'mapping from' string
            mappingTo[1][index++] = LDC_W;
            ClassFileUtils.writeUnsignedShort(mappingTo[1], index, stringMappings[i][0]);
            index += 2;
            // load 'mapping to' string
            mappingTo[1][index++] = LDC_W;
            ClassFileUtils.writeUnsignedShort(mappingTo[1], index, stringMappings[i][1]);
            index += 2;
            // invoke Map.put() interface method to register new mapping
            mappingTo[1][index++] = (byte) INVOKEINTERFACE;
            ClassFileUtils.writeUnsignedShort(mappingTo[1], index, mapPutInterfaceMethodrefIndex);
            index += 2;
            mappingTo[1][index++] = 3; // due to historical reasons
            mappingTo[1][index++] = 0; // due to historical reasons
        }

        // mappingTo table completed
        return mappingTo;
    }

    private static byte[][] generateMappingTo(final int[][] methodRefRedirects, final MethodDescriptor[][] methodMapping) {
        // detect mappingTo table size
        int mappingTableSize = 1;
        for (int i = 0; i < methodRefRedirects.length; i++) {
            if (methodRefRedirects[i][1] != 0) mappingTableSize += 1;

        }
        final byte[][] mappingTo = new byte[mappingTableSize][];
        int mappingIndex = 0;
        // generate mappingTo table dynamically (depends on discovered methodRef indices in class constant pool)
        for (int i = 0; i < methodMapping.length; i++) {
            int methodRefIndex = methodRefRedirects[i][1];
            if (methodRefIndex != 0) {
                mappingIndex += 1;
                mappingTo[mappingIndex] = new byte[3];
                mappingTo[mappingIndex][0] = (byte) (methodMapping[i][1].isStatic ? INVOKESTATIC : INVOKEVIRTUAL);
                ClassFileUtils.writeUnsignedShort(mappingTo[mappingIndex], 1, methodRefIndex);
            }
        }
        // mappingTo table completed
        return mappingTo;
    }

    /**
     * Returns <code>method patch info</code> if patches were detected or <code>null</code> if there is no patch applicable.
     * Every <code>method patch info</code> has the following format:
     * <p>
     *     <pre>
     *        +-------------+ PATCH INFO STRUCTURE HEADER
     *        | integer 0   | holds <code>method_info</code> index inside class file
     *        | integer 1   | holds <code>method_info</code> structure difference in bytes after applied patches
     *        | integer 2   | new <code>max_stack</code> item value of method's <code>Code_attribute</code> structure
     *        | integer 3   | new <code>max_locals</code> item value of method's <code>Code_attribute</code> structure
     *        +-------------+ PATCH INFO STRUCTURE DATA
     *        | integer 2   | holds non-zero mapping index in mapping tables of 1-st applied patch
     *        | integer 3   | holds index of 1-st patch start inside CodeAttribute's code section of original <code>method_info</code> structure
     *        +-------------+
     *        | integer 4   | holds non-zero mapping index in mapping tables of 2-nd applied patch
     *        | integer 5   | holds index of 2-nd patch start inside bytes section of original <code>CONSTANT_Utf8_info</code> structure
     *        +-------------+
     *        |    ...      | etc
     *        |             |
     *        +-------------+
     *        | integer N-1 | mapping index equal to zero indicates premature <code>method patch info</code> structure end
     *        | integer N   |
     *        +-------------+
     *     </pre>
     * </p>
     *
     * @param clazz class byte code
     * @param codeAttribute method code_attribute structure
     * @param methodIndex method info index in class file
     * @param mappingFrom byte code to be replaced in method implementation
     * @param mappingTo byte code to be applied in method implementation
     * @return method patch or <code>null</code> if no patch is applied
     */
    private static int[] getMethodPatch(final byte[] clazz, final CodeAttributeRefs codeAttribute, final int methodIndex,
                                        final byte[][] mappingFrom, final byte[][] mappingTo) {
        final int codeLength = codeAttribute.getCodeLength();
        final int offset = codeAttribute.getCodeStartRef();;
        final int limit = offset + codeLength;
        int[] retVal = null;
        int mappingIndex;
        int patchIndex = 4;
        int opcode;

        for (int i = offset; i <= limit - MINIMUM; i += OpcodeUtils.instructionBytesCount(clazz, i)) {
            opcode = OpcodeUtils.MASK_FF & clazz[i];
            if (opcode != INVOKESTATIC && opcode != INVOKEVIRTUAL && opcode != LDC) continue; // TODO: every mapping should define interested in OPs
            for (int j = 1; j < mappingFrom.length; j++) {
                if (limit - i < mappingFrom[j].length) continue;
                mappingIndex = j;
                for (int k = 0; k < mappingFrom[j].length; k++) {
                    if (clazz[i + k] != mappingFrom[j][k]) {
                        mappingIndex = 0;
                        break;
                    }
                }
                if (mappingIndex != 0) {
                    if (retVal == null) {
                        retVal = new int[(((limit - i) / MINIMUM) + 2) * 2];
                        retVal[0] = methodIndex;
                    }
                    retVal[1] += mappingTo[mappingIndex].length - mappingFrom[mappingIndex].length;
                    retVal[2] = codeAttribute.getMaxStack(); // TODO: detect new max_stack value
                    retVal[3] = codeAttribute.getMaxLocals(); // TODO: detect new max_locals value
                    retVal[patchIndex++] = mappingIndex;
                    retVal[patchIndex++] = i - offset;
                    break;
                }
            }
        }

        return retVal;
    }

}
