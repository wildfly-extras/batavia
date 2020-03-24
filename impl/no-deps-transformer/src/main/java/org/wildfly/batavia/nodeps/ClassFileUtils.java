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
package org.wildfly.javax2jakarta;

/**
 * Utility class for working with class file content.
 * Compatible with Java VM specification version 13 and below.
 *
 * <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class ClassFileUtils {

    /**
     * <code>CONSTANT_Utf8_info</code> structure tag.
     */
    static final byte UTF8 = 1;
    /**
     * <code>CONSTANT_Integer_info</code> structure tag.
     */
    static final byte INTEGER = 3;
    /**
     * <code>CONSTANT_Float_info</code> structure tag.
     */
    static final byte FLOAT = 4;
    /**
     * <code>CONSTANT_Long_info</code> structure tag.
     */
    static final byte LONG = 5;
    /**
     * <code>CONSTANT_Double_info</code> structure tag.
     */
    static final byte DOUBLE = 6;
    /**
     * <code>CONSTANT_Class_info</code> structure tag.
     */
    static final byte CLASS = 7;
    /**
     * <code>CONSTANT_String_info</code> structure tag.
     */
    static final byte STRING = 8;
    /**
     * <code>CONSTANT_Fieldref_info</code> structure tag.
     */
    static final byte FIELD_REF = 9;
    /**
     * <code>CONSTANT_Methodref_info</code> structure tag.
     */
    static final byte METHOD_REF = 10;
    /**
     * <code>CONSTANT_InterfaceMethodref_info</code> structure tag.
     */
    static final byte INTERFACE_METHOD_REF = 11;
    /**
     * <code>CONSTANT_NameAndType_info</code> structure tag.
     */
    static final byte NAME_AND_TYPE = 12;
    /**
     * <code>CONSTANT_MethodHandle_info</code> structure tag.
     */
    static final byte METHOD_HANDLE = 15;
    /**
     * <code>CONSTANT_MethodType_info</code> structure tag.
     */
    static final byte METHOD_TYPE = 16;
    /**
     * <code>CONSTANT_Dynamic_info</code> structure tag.
     */
    static final byte DYNAMIC = 17;
    /**
     * <code>CONSTANT_InvokeDynamic_info</code> structure tag.
     */
    static final byte INVOKE_DYNAMIC = 18;
    /**
     * <code>CONSTANT_Module_info</code> structure tag.
     */
    static final byte MODULE = 19;
    /**
     * <code>CONSTANT_Package_info</code> structure tag.
     */
    static final byte PACKAGE = 20;
    /**
     * Constant pool size index inside class file.
     */
    static final int POOL_SIZE_INDEX = 8;
    /**
     * Constant pool content beginning index inside class file.
     */
    static final int POOL_CONTENT_INDEX = POOL_SIZE_INDEX + 2;

    /**
     * Constructor.
     */
    private ClassFileUtils() {
        // forbidden instantiation
    }

    /**
     * Reads unsigned short value from given class file position.
     *
     * @param clazz class data
     * @param offset the index to start reading from
     * @return read value
     */
    static int readUnsignedShort(final byte[] clazz, final int offset) {
        return ((clazz[offset] & 0xFF) << 8) | (clazz[offset + 1] & 0xFF);
    }

    /**
     * Writes unsigned short value to given class file position.
     *
     * @param clazz class data
     * @param offset the index to start writing from
     * @param value the value to write
     */
    static void writeUnsignedShort(final byte[] clazz, final int offset, final int value) {
        clazz[offset] = (byte) (value >>> 8);
        clazz[offset + 1] = (byte) value;
    }

    /**
     * Returns pointers to the <code>class constant pool items</code> indexed from 1 till end of array.
     * Every <code>zero</code> inside it represent <code>undefined</code> value.
     * One special field in this array is value at position <code>zero</code>.
     * This value holds pointer to the end of the class constant pool.
     *
     * @param clazz class to create array of constant pool item pointers for
     * @return array of constant pool item pointers
     */
    static int[] getConstantPool(final byte[] clazz) {
        final int constantPoolSize = readUnsignedShort(clazz, POOL_SIZE_INDEX);
        final int[] retVal = new int[constantPoolSize];
        int position = POOL_CONTENT_INDEX;
        byte tag;
        int utf8Length;

        for (int i = 1; i < constantPoolSize; i++) {
            retVal[i] = position;
            tag = clazz[position++];
            if (tag == UTF8) {
                utf8Length = readUnsignedShort(clazz, position);
                position += 2 + utf8Length;
            } else if (tag == CLASS || tag == STRING || tag == METHOD_TYPE || tag == MODULE || tag == PACKAGE) {
                position += 2;
            } else if (tag == LONG || tag == DOUBLE) {
                position += 8;
                i++;
            } else if (tag == INTEGER || tag == FLOAT || tag == FIELD_REF || tag == METHOD_REF ||
                       tag == INTERFACE_METHOD_REF || tag == NAME_AND_TYPE || tag == DYNAMIC || tag == INVOKE_DYNAMIC) {
                position += 4;
            } else if (tag == METHOD_HANDLE) {
                position += 3;
            } else {
                throw new UnsupportedClassVersionError();
            }
        }
        retVal[0] = position;
        return retVal;
    }

    /**
     * Decodes modified UTF-8 to string.
     *
     * @param clazz class bytes
     * @param offset the index of the first byte of modified UTF-8
     * @param limit the limit of modified UTF-8
     * @return decoded string
     */
    static String utf8ToString(final byte[] clazz, final int offset, final int limit) {
        final char[] charBuffer = new char[limit - offset];
        int charsCount = 0;
        int index = offset;
        int currentByte;
        while (index < limit) {
            currentByte = clazz[index++];
            if ((currentByte & 0x80) == 0) {
                charBuffer[charsCount++] = (char) (currentByte & 0x7F);
            } else if ((currentByte & 0xE0) == 0xC0) {
                charBuffer[charsCount++] = (char) (((currentByte & 0x1F) << 6) + (clazz[index++] & 0x3F));
            } else {
                charBuffer[charsCount++] = (char) (((currentByte & 0xF) << 12) + ((clazz[index++] & 0x3F) << 6) + (clazz[index++] & 0x3F));
            }
        }
        return new String(charBuffer, 0, charsCount);
    }

    /**
     * Encodes string to modified UTF-8.
     *
     * @param data string
     * @return encoded modified UTF-8
     */
    static byte[] stringToUtf8(final String data) {
        final byte[] retVal = new byte[getUtf8Size(data)];
        int bytesCount = 0;
        int currentChar;

        for (int i = 0; i < data.length(); i++) {
            currentChar = data.charAt(i);
            if (currentChar < 0x80 && currentChar != 0) {
                retVal[bytesCount++] = (byte) currentChar;
            } else if (currentChar >= 0x800) {
                retVal[bytesCount++] = (byte) (0xE0 | ((currentChar >> 12) & 0x0F));
                retVal[bytesCount++] = (byte) (0x80 | ((currentChar >> 6) & 0x3F));
                retVal[bytesCount++] = (byte) (0x80 | ((currentChar >> 0) & 0x3F));
            } else {
                retVal[bytesCount++] = (byte) (0xC0 | ((currentChar >> 6) & 0x1F));
                retVal[bytesCount++] = (byte) (0x80 | ((currentChar >> 0) & 0x3F));
            }
        }

        return retVal;
    }

    /**
     * Counts how many <code>CONSTANT_Utf8_info</code> structures are present in the class constant pool.
     *
     * @param clazz class bytes
     * @param constantPool pointers to class constant pool
     * @return <code>CONSTANT_Utf8_info</code> structures count in class constant pool
     */
    static int countUtf8Items(final byte[] clazz, final int[] constantPool) {
        int retVal = 0;

        for (int i = 1; i < constantPool.length; i++) {
            if (constantPool[i] == 0) continue;
            if (UTF8 == clazz[constantPool[i]]) retVal++;
        }

        return retVal;
    }

    /**
     * Counts size for modified UTF-8 encoding.
     *
     * @param data to be converted
     * @return byte array size of modified UTF-8
     */
    private static int getUtf8Size(final String data) {
        int retVal = data.length();
        int currentChar;

        for (int i = 0; i < data.length(); i++) {
            currentChar = data.charAt(i);
            if (currentChar >= 0x80 || currentChar == 0) {
                retVal += (currentChar >= 0x800) ? 2 : 1;
            }
        }

        return retVal;
    }

}
