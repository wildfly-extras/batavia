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

import static org.wildfly.transformer.nodeps.ClassFileUtils.*;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class ClassFileRefs {

    private static final int MINOR_VERSION_REF = 4;
    private static final int MAJOR_VERSION_REF = 6;
    private final ConstantPoolRefs constantPool;
    private final int minorVersion;
    private final int majorVersion;
    private final int accessFlagsRef;
    private final int accessFlags;
    private final int thisClassIndexRef;
    private final int thisClassIndex;
    private final int superClassIndexRef;
    private final int superClassIndex;
    private final int interfacesCountRef;
    private final int interfacesStartRef;
    private final int interfacesEndRef;
    private final int[] interfaces;
    private final int methodsCountRef;
    private final int methodsStartRef;
    private final int methodsEndRef;
    private final MethodInfoRefs[] methods;

    ClassFileRefs(final byte[] clazz, final ConstantPoolRefs constantPool,
                  final int[] interfaces, final MethodInfoRefs[] methods) {
        this.minorVersion = readUnsignedShort(clazz, MINOR_VERSION_REF);
        this.majorVersion = readUnsignedShort(clazz, MAJOR_VERSION_REF);
        this.constantPool = constantPool;
        this.accessFlagsRef = constantPool.getItemsEndRef();
        this.accessFlags = readUnsignedShort(clazz, accessFlagsRef);
        this.thisClassIndexRef = accessFlagsRef + 2;
        this.thisClassIndex = readUnsignedShort(clazz, thisClassIndexRef);
        this.superClassIndexRef = this.thisClassIndexRef + 2;
        this.superClassIndex = readUnsignedShort(clazz, superClassIndexRef);
        this.interfacesCountRef = superClassIndexRef + 2;
        this.interfaces = interfaces;
        this.interfacesStartRef = interfacesCountRef + 2;
        this.interfacesEndRef = interfacesStartRef + interfaces.length * 2;
        this.methodsCountRef = interfacesEndRef;
        this.methods = methods;
        this.methodsStartRef = methodsCountRef + 2;
        this.methodsEndRef = methods.length > 0 ? methods[methods.length - 1].getMethodInfoEndRef() : this.methodsStartRef;
    }

    ConstantPoolRefs getConstantPool() {
        return constantPool;
    }

    int getMinorVersionRef() {
        return MINOR_VERSION_REF;
    }

    int getMinorVersion() {
        return minorVersion;
    }

    int getMajorVersionRef() {
        return MAJOR_VERSION_REF;
    }

    int getMajorVersion() {
        return majorVersion;
    }

    int getAccessFlagsRef() {
        return accessFlagsRef;
    }

    int getAccessFlags() {
        return accessFlags;
    }

    int getThisClassIndexRef() {
        return thisClassIndexRef;
    }

    int getThisClassIndex() {
        return thisClassIndex;
    }

    String getThisClassAsString() {
        final int classNameIndex = constantPool.getClass_NameIndex(getThisClassIndex());
        return constantPool.getUtf8AsString(classNameIndex);
    }

    int getSuperClassIndexRef() {
        return superClassIndexRef;
    }

    int getSuperClassIndex() {
        return superClassIndex;
    }

    String getSuperClassAsString() {
        final int classIndex = getSuperClassIndex();
        if (classIndex == 0) return null;
        final int classNameIndex = constantPool.getClass_NameIndex(classIndex);
        return constantPool.getUtf8AsString(classNameIndex);
    }

    int getInterfacesCountRef() {
        return interfacesCountRef;
    }

    int getInterfacesCount() {
        return interfaces.length;
    }

    int getInterfaceClassIndex(final int index) {
        return interfaces[index];
    }

    int getInterfacesStartRef() {
        return interfacesStartRef;
    }

    int getInterfacesEndRef() {
        return interfacesEndRef;
    }

    int getMethodsCountRef() {
        return methodsCountRef;
    }

    int getMethodsCount() {
        return methods.length;
    }

    int getMethodsStartRef() {
        return methodsStartRef;
    }

    int getMethodsEndRef() {
        return methodsEndRef;
    }

    MethodInfoRefs getMethod(final byte[] clazz, final MethodDescriptor methodDescriptor) {
        for (MethodInfoRefs method : methods) {
            if (constantPool.utf8EqualsTo(method.getNameIndex(), methodDescriptor.methodName)) {
                if (constantPool.utf8EqualsTo(method.getDescriptorIndex(), methodDescriptor.methodDescriptor)) {
                    return method;
                }
            }
        }
        return null;
    }

    MethodInfoRefs getMethod(final int index) {
        return methods[index];
    }

    static ClassFileRefs of(final byte[] clazz) {
        // process constant pool
        final ConstantPoolRefs cpRefs = ConstantPoolRefs.of(clazz);
        // process interfaces
        int position = cpRefs.getItemsEndRef() + 6;
        final int interfacesCount = readUnsignedShort(clazz, position);
        position += 2;
        final int[] interfaces = new int[interfacesCount];
        for (int i = 0; i < interfacesCount; i++) {
            interfaces[i] = readUnsignedShort(clazz, position);
            position += 2;
        }
        // process fields count
        final int fieldsCount = readUnsignedShort(clazz, position);
        position += 2;
        int attributesCount;
        int attributeLength;
        // ignore fields
        for (int i = 0; i < fieldsCount; i++) {
            position += 6; // skip field access_flags, name_index & descriptor_index
            attributesCount = readUnsignedShort(clazz, position);
            position += 2;
            // ignore all attributes on a field
            for (int j = 0; j < attributesCount; j++) {
                position += 2; // skip attribute_name_index
                attributeLength = readUnsignedInt(clazz, position);
                position += 4; // processed attribute length
                position += attributeLength; // skip field attribute
            }
        }
        // process methods count
        final int methodsCount = readUnsignedShort(clazz, position);
        position += 2;
        final MethodInfoRefs[] methods = new MethodInfoRefs[methodsCount];
        // process methods
        for (int i = 0; i < methods.length; i++) {
            methods[i] = MethodInfoRefs.of(clazz, cpRefs, position, i);
            position = methods[i].getMethodInfoEndRef();
        }
        return new ClassFileRefs(clazz, cpRefs, interfaces, methods);
    }

}
