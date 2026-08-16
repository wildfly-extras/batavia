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
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class ConstantPoolRefs {

    private static final int CONSTANT_POOL_SIZE_START_INDEX = 8;
    private static final int CONSTANT_POOL_ITEMS_START_INDEX = 10;
    private final byte[] clazz;
    private final int[] constantPool;
    private final int sizeStartRef;
    private final int itemsStartRef;
    private final int itemsEndRef;

    ConstantPoolRefs(final byte[] clazz, final int[] constantPool, final int sizeStartRef, final int itemsStartRef, final int itemsEndRef) {
        this.clazz = clazz;
        this.constantPool = constantPool;
        this.sizeStartRef = sizeStartRef;
        this.itemsStartRef = itemsStartRef;
        this.itemsEndRef = itemsEndRef;
    }

    /**
     * Returns pointers to the <code>class constant pool items</code> indexed from 1 till end of array.
     * Every <code>zero</code> value inside it represents <code>undefined</code> value.
     * @return array of constant pool item pointers
     */
    int[] getItemRefs() {
        return constantPool;
    }

    int getSizeStartRef() {
        return sizeStartRef;
    }

    int getSize() {
        return constantPool.length;
    }

    int getItemsStartRef() {
        return itemsStartRef;
    }

    int getItemsEndRef() {
        return itemsEndRef;
    }

    boolean isUtf8(final int index) {
        return ConstantPoolTags.UTF8 == clazz[constantPool[index]];
    }

    boolean isString(final int index) {
        return ConstantPoolTags.STRING == clazz[constantPool[index]];
    }

    int getString_IndexRef(final int index) {
        if (!isString(index)) throw new IllegalArgumentException();
        return constantPool[index] + 1;
    }

    int getString_Index(final int index) {
        return ClassFileUtils.readUnsignedShort(clazz, getString_IndexRef(index));
    }

    String getUtf8AsString(final int index) {
        final int utf8Length = getUtf8_Length(index);
        final int utf8BytesStart = getUtf8_BytesRef(index);
        return ClassFileUtils.utf8ToString(clazz, utf8BytesStart, utf8BytesStart + utf8Length);
    }

    boolean utf8EqualsTo(final int index, final byte[] otherUtf8) {
        if (getUtf8_Length(index) != otherUtf8.length) return false;
        final int utf8BytesRef = getUtf8_BytesRef(index);
        for (int i = 0; i < otherUtf8.length; i++) {
            if (clazz[utf8BytesRef + i] != otherUtf8[i]) return false;
        }
        return true;
    }

    int getUtf8_LengthRef(final int index) {
        if (!isUtf8(index)) throw new IllegalArgumentException();
        return constantPool[index] + 1;
    }

    int getUtf8_BytesRef(final int index) {
        if (!isUtf8(index)) throw new IllegalArgumentException();
        return constantPool[index] + 3;
    }

    int getUtf8_Length(final int index) {
        return ClassFileUtils.readUnsignedShort(clazz, getUtf8_LengthRef(index));
    }

    boolean isNameAndType(final int index) {
        return ConstantPoolTags.NAME_AND_TYPE == clazz[constantPool[index]];
    }

    int getNameAndType_NameIndexRef(final int index) {
        if (!isNameAndType(index)) throw new IllegalArgumentException();
        return constantPool[index] + 1;
    }

    int getNameAndType_DescriptorIndexRef(final int index) {
        if (!isNameAndType(index)) throw new IllegalArgumentException();
        return constantPool[index] + 3;
    }

    int getNameAndType_NameIndex(final int index) {
        return ClassFileUtils.readUnsignedShort(clazz, getNameAndType_NameIndexRef(index));
    }

    int getNameAndType_DescriptorIndex(final int index) {
        return ClassFileUtils.readUnsignedShort(clazz, getNameAndType_DescriptorIndexRef(index));
    }

    boolean isInterfaceMethodRef(final int index) {
        return ConstantPoolTags.INTERFACE_METHOD_REF == clazz[constantPool[index]];
    }

    int getInterfaceMethodRef_ClassIndexRef(final int index) {
        if (!isInterfaceMethodRef(index)) throw new IllegalArgumentException();
        return constantPool[index] + 1;
    }

    int getInterfaceMethodRef_NameAndTypeIndexRef(final int index) {
        if (!isInterfaceMethodRef(index)) throw new IllegalArgumentException();
        return constantPool[index] + 3;
    }

    int getInterfaceMethodRef_ClassIndex(final int index) {
        return ClassFileUtils.readUnsignedShort(clazz, getInterfaceMethodRef_ClassIndexRef(index));
    }

    int getInterfaceMethodRef_NameAndTypeIndex(final int index) {
        return ClassFileUtils.readUnsignedShort(clazz, getInterfaceMethodRef_NameAndTypeIndexRef(index));
    }

    boolean isMethodRef(final int index) {
        return ConstantPoolTags.METHOD_REF == clazz[constantPool[index]];
    }

    int getMethodRef_ClassIndexRef(final int index) {
        if (!isMethodRef(index)) throw new IllegalArgumentException();
        return constantPool[index] + 1;
    }

    int getMethodRef_NameAndTypeIndexRef(final int index) {
        if (!isMethodRef(index)) throw new IllegalArgumentException();
        return constantPool[index] + 3;
    }

    int getMethodRef_ClassIndex(final int index) {
        return ClassFileUtils.readUnsignedShort(clazz, getMethodRef_ClassIndexRef(index));
    }

    int getMethodRef_NameAndTypeIndex(final int index) {
        return ClassFileUtils.readUnsignedShort(clazz, getMethodRef_NameAndTypeIndexRef(index));
    }

    boolean isClassInfo(final int index) {
        return ConstantPoolTags.CLASS == clazz[constantPool[index]];
    }

    int getClass_NameIndexRef(final int index) {
        if (!isClassInfo(index)) throw new IllegalArgumentException();
        return constantPool[index] + 1;
    }

    int getClass_NameIndex(final int index) {
        return ClassFileUtils.readUnsignedShort(clazz, getClass_NameIndexRef(index));
    }

    static ConstantPoolRefs of(final byte[] clazz) {
        final int constantPoolSize = ClassFileUtils.readUnsignedShort(clazz, CONSTANT_POOL_SIZE_START_INDEX);
        final int cpItemsStartIndex = CONSTANT_POOL_ITEMS_START_INDEX;
        final int[] constantPool = new int[constantPoolSize];
        int position = cpItemsStartIndex;
        byte tag;
        int utf8Length;

        for (int i = 1; i < constantPoolSize; i++) {
            constantPool[i] = position;
            tag = clazz[position++];
            if (tag == ConstantPoolTags.UTF8) {
                utf8Length = ClassFileUtils.readUnsignedShort(clazz, position);
                position += 2 + utf8Length;
            } else if (tag == ConstantPoolTags.CLASS || tag == ConstantPoolTags.STRING || tag == ConstantPoolTags.METHOD_TYPE || tag == ConstantPoolTags.MODULE || tag == ConstantPoolTags.PACKAGE) {
                position += 2;
            } else if (tag == ConstantPoolTags.LONG || tag == ConstantPoolTags.DOUBLE) {
                position += 8;
                i++;
            } else if (tag == ConstantPoolTags.INTEGER || tag == ConstantPoolTags.FLOAT || tag == ConstantPoolTags.FIELD_REF || tag == ConstantPoolTags.METHOD_REF ||
                    tag == ConstantPoolTags.INTERFACE_METHOD_REF || tag == ConstantPoolTags.NAME_AND_TYPE || tag == ConstantPoolTags.DYNAMIC || tag == ConstantPoolTags.INVOKE_DYNAMIC) {
                position += 4;
            } else if (tag == ConstantPoolTags.METHOD_HANDLE) {
                position += 3;
            } else {
                throw new UnsupportedClassVersionError();
            }
        }
        final int cpItemsEndIndex = position;
        return new ConstantPoolRefs(clazz, constantPool, CONSTANT_POOL_SIZE_START_INDEX, cpItemsStartIndex, cpItemsEndIndex);
    }

}
