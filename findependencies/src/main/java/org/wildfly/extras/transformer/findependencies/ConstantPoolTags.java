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
final class ConstantPoolTags {

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

    private ConstantPoolTags() {
        // forbidden instantiation
    }

}
