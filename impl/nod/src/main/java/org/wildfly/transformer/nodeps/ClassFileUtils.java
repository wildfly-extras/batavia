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

/**
 * Utility class for working with class file content.
 * Compatible with Java VM specification version 14 and below.
 *
 * <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class ClassFileUtils {

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
     * Reads unsigned int value from given class file position.
     *
     * @param clazz class data
     * @param offset the index to start reading from
     * @return read value
     */
    static int readUnsignedInt(final byte[] clazz, final int offset) {
        return ((clazz[offset] & 0xFF) << 24) | ((clazz[offset + 1] & 0xFF) << 16) | ((clazz[offset + 2] & 0xFF) << 8) | (clazz[offset + 3] & 0xFF);
    }

    /**
     * Writes unsigned short value to given byte buffer.
     *
     * @param buffer byte buffer
     * @param offset the index to start writing from
     * @param value the value to write
     */
    static void writeUnsignedShort(final byte[] buffer, final int offset, final int value) {
        buffer[offset] = (byte) (value >>> 8);
        buffer[offset + 1] = (byte) value;
    }

    /**
     * Writes unsigned int value to given byte buffer.
     *
     * @param buffer byte buffer
     * @param offset the index to start writing from
     * @param value the value to write
     */
    static void writeUnsignedInt(final byte[] buffer, final int offset, final int value) {
        buffer[offset] = (byte) (value >>> 24);
        buffer[offset + 1] = (byte) (value >>> 16);
        buffer[offset + 2] = (byte) (value >>> 8);
        buffer[offset + 3] = (byte) value;
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
     * @param cpRefs pointers to class constant pool
     * @return <code>CONSTANT_Utf8_info</code> structures count in class constant pool
     */
    static int countUtf8Items(final ConstantPoolRefs cpRefs) {
        int retVal = 0;

        for (int i = 1; i < cpRefs.getSize(); i++) {
            if (cpRefs.isUtf8(i)) retVal++;
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
