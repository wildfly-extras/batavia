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

import static java.lang.System.arraycopy;
import static java.lang.Thread.currentThread;
import static org.wildfly.transformer.nodeps.ClassFileUtils.*;
import static org.wildfly.transformer.nodeps.OpcodeUtils.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.wildfly.transformer.Transformer;

/**
 * Class file transformer.
 * Instances of this class are thread safe.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class TransformerImpl implements Transformer {

    private static final Resource[] EMPTY_ARRAY = new Resource[0];
    private static final String CLASS_SUFFIX = ".class";
    private static final String XML_SUFFIX = ".xml";
    private static final String META_INF_SERVICES_PREFIX = "META-INF/services/";
    private static final String OUR_PACKAGE;

    static {
        final String ourClass = TransformerImpl.class.getName().replace(".", "/");
        OUR_PACKAGE = ourClass.substring(0, ourClass.lastIndexOf('/') + 1);
    }

    /**
     * Debugging support.
     */
    private static final boolean DEBUG = Boolean.getBoolean(TransformerImpl.class.getName() + ".debug");

    /**
     * Packages mapping with '/' char.
     */
    final Map<String, String> mappingWithSeps;

    /**
     * Packages mapping with '.' char.
     */
    final Map<String, String> mappingWithDots;

    final Utf8InfoMapping utf8Mapping;

    /**
     * Keeps track of already generated utility classes. The mapping here is: <code>targetPackage->generatedClassName</code>.
     */
    private final ConcurrentMap<String, String> generatedUtilityClasses = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param mappingWithSeps packages mapping in path separator form
     * @param mappingWithDots packages mapping in dot form
     */
    TransformerImpl(final Map<String, String> mappingWithSeps, final Map<String, String> mappingWithDots) {
        this.mappingWithSeps = mappingWithSeps;
        this.mappingWithDots =  mappingWithDots;
        final int arraySize = mappingWithSeps.size() + mappingWithDots.size() + 1;
        final byte[][] mappingFrom = new byte[arraySize][];
        final byte[][] mappingTo = new byte[arraySize][];
        int minimum = Integer.MAX_VALUE;
        int i = 1;
        for (Map.Entry<String, String> mappingEntry : mappingWithSeps.entrySet()) {
            mappingFrom[i] = stringToUtf8(mappingEntry.getKey());
            mappingTo[i] = stringToUtf8(mappingEntry.getValue());
            if (minimum > mappingFrom[i].length) {
                minimum = mappingFrom[i].length;
            }
            i++;
        }
        for (Map.Entry<String, String> mappingEntry : mappingWithDots.entrySet()) {
            mappingFrom[i] = stringToUtf8(mappingEntry.getKey());
            mappingTo[i] = stringToUtf8(mappingEntry.getValue());
            if (minimum > mappingFrom[i].length) {
                minimum = mappingFrom[i].length;
            }
            i++;
        }
        this.utf8Mapping = new Utf8InfoMapping(mappingFrom, mappingTo, minimum);
    }

    @Override
    public Resource[] transform(final Resource r) {
        Resource[] retVal = null;
        String oldResourceName = r.getName();
        String newResourceName = replacePackageName(oldResourceName, false);
        if (oldResourceName.endsWith(CLASS_SUFFIX)) {
            retVal = transform(r.getData(), utf8Mapping, newResourceName);
        } else if (oldResourceName.endsWith(XML_SUFFIX)) {
            retVal = new Resource[]{new Resource(newResourceName, xmlFile(r.getData()))};
        } else if (oldResourceName.startsWith(META_INF_SERVICES_PREFIX)) {
            newResourceName = replacePackageName(oldResourceName, true);
            if (!newResourceName.equals(oldResourceName)) {
                retVal = new Resource[]{new Resource(newResourceName, r.getData())};
            }
        } else if (!newResourceName.equals(oldResourceName)) {
            retVal = new Resource[] {new Resource(newResourceName, r.getData())};
        }
        return retVal == null ? EMPTY_ARRAY : retVal;
    }

    private String replacePackageName(final String resourceName, final boolean dotFormat) {
        int startIndex;
        for (final Map.Entry<String, String> mapping : (dotFormat ? mappingWithDots : mappingWithSeps).entrySet()) {
            startIndex = resourceName.indexOf(mapping.getKey());
            if (startIndex != -1) {
                return resourceName.substring(0, startIndex) + mapping.getValue() + resourceName.substring(startIndex + mapping.getKey().length());
            }
        }
        return resourceName;
    }

    private static byte[] xmlFile(final byte[] data) {
        try {
            // TODO: use mapping provided in constructor!!!
            return new String(data, "UTF-8").replace("javax.", "jakarta.").getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return null; // should never happen
        }
    }

    private Resource[] transform(final byte[] clazz, final Utf8InfoMapping utf8Mapping, final String newResourceName) {
        final ClassFileRefs cfRefs = ClassFileRefs.of(clazz);
        final ConstantPoolRefs cpRefs = cfRefs.getConstantPool();
        final String transformedClassName = cfRefs.getThisClassAsString();
        int diffInBytes = 0;

        final Utf8ItemsPatch utf8ItemsPatch = Utf8ItemsPatch.of(clazz, cpRefs, utf8Mapping);
        if (utf8ItemsPatch != null) diffInBytes += utf8ItemsPatch.diffInBytes;
        MethodsRedirectPatch methodsRedirectPatch = null;
        if (!transformedClassName.startsWith(OUR_PACKAGE)) {
            methodsRedirectPatch = MethodsRedirectPatch.of(clazz, cfRefs);
            if (methodsRedirectPatch != null) diffInBytes += methodsRedirectPatch.diffInBytes;
        }

        if (diffInBytes > 0 && Integer.MAX_VALUE - diffInBytes < clazz.length) {
            throw new UnsupportedOperationException("Couldn't patch class file. The transformed class file would exceed max allowed size " + Integer.MAX_VALUE + " bytes");
        }

        final boolean patchesNotAvailable = utf8ItemsPatch == null && methodsRedirectPatch == null;
        if (patchesNotAvailable) return null;
        // patches are available, patch the class
        final byte[] patchedClass = applyPatches(clazz, utf8Mapping,clazz.length + diffInBytes, cfRefs, utf8ItemsPatch, methodsRedirectPatch, null);
        final Resource patchedClassResource = new Resource(newResourceName, patchedClass);
        final MethodsRedirectPatch.UtilityClasses utilClasses = methodsRedirectPatch != null ? methodsRedirectPatch.utilClasses : null;
        final Resource[] retVal = new Resource[(utilClasses != null ? utilClasses.utilClassesRefactoring.to.length - 1 : 0) + 1];
        retVal[0] = patchedClassResource;
        if (utilClasses != null) {
            final byte[][] oldClassNames = utilClasses.utilClassesRefactoring.from;
            final byte[][] newClassNames = utilClasses.utilClassesRefactoring.to;
            String oldClassName, newClassName;
            byte[] oldUtilClassBytes;
            byte[] newUtilClassBytes;
            for (int i = 1; i < oldClassNames.length; i++) {
                oldClassName = utf8ToString(oldClassNames[i], 0, oldClassNames[i].length) + ".class";
                newClassName = utf8ToString(newClassNames[i], 0, newClassNames[i].length) + ".class";
                oldUtilClassBytes = getResourceBytes(oldClassName);
                newUtilClassBytes = transformUtilityClass(oldUtilClassBytes, utilClasses.utilClassesRefactoring, utf8Mapping);
                retVal[i] = new Resource(newClassName, newUtilClassBytes);
            }
        }
        return retVal;
    }

    private byte[] transformUtilityClass(final byte[] clazz, final Utf8InfoMapping renameMapping, final Utf8InfoMapping mappingRules) {
        final ClassFileRefs cfRefs = ClassFileRefs.of(clazz);
        final ConstantPoolRefs cpRefs = cfRefs.getConstantPool();
        int diffInBytes = 0;

        // rename utility class
        final Utf8ItemsPatch renamePatch = Utf8ItemsPatch.of(clazz, cpRefs, renameMapping);
        diffInBytes += renamePatch.diffInBytes;
        // add mapping rules to constant pool and modify its static initializer to apply these rules
        final AddMappingPatch applyMappingsPatch = AddMappingPatch.of(clazz, cfRefs, mappingRules); // TODO: rename AddMappingPatch to ApplyMappingsPatch
        diffInBytes += applyMappingsPatch.diffInBytes;
        return applyPatches(clazz, renameMapping,clazz.length + diffInBytes, cfRefs, renamePatch, null, applyMappingsPatch);
    }

    private static byte[] toByteArray(final InputStream is) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int c = -1;
            while ((c = is.read()) != -1) baos.write(c);
            return baos.toByteArray();
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            return null;
        }
    }

    private static byte[] getResourceBytes(final String resourceName) {
        return toByteArray(TransformerImpl.class.getClassLoader().getResourceAsStream(resourceName));
    }

    /**
     * Returns modified class byte code with patches applied.
     *
     * @param oldClass original class byte code
     * @param newClassSize count of bytes of new class byte code
     * @param oldClassRefs pointers to old class items
     * @param utf8ItemsPatch utf8 items patches to apply
     * @param methodsRedirectPatch add items patch to apply
     * @return modified class byte code with patches applied
     */
    private byte[] applyPatches(final byte[] oldClass, final Utf8InfoMapping utf8Mapping, final int newClassSize, final ClassFileRefs oldClassRefs,
                                final Utf8ItemsPatch utf8ItemsPatch, final MethodsRedirectPatch methodsRedirectPatch, final AddMappingPatch applyMappingsPatch) {
        if (DEBUG) {
            synchronized (System.out) {
                System.out.println("[" + currentThread() + "] Patching class " + oldClassRefs.getThisClassAsString() + " - START");
            }
        }
        // TODO: revisit this method is it possible to merge via inheritance somehow MethodsRedirectPatch & AddMappingPatch ???
        final byte[] newClass = new byte[newClassSize];
        int oldClassOffset = 0, newClassOffset = 0;
        int length, mappingIndex, oldUtf8ItemBytesSectionOffset, oldUtf8ItemLength, patchOffset;
        int debugOldUtf8ItemOffset = -1, debugNewUtf8ItemOffset = -1;
        int debugOldUtf8ItemLength = -1, debugNewUtf8ItemLength = -1;

        // First copy magic, version and constant pool size
        arraycopy(oldClass, oldClassOffset, newClass, newClassOffset, oldClassRefs.getConstantPool().getItemsStartRef());
        oldClassOffset = newClassOffset = oldClassRefs.getConstantPool().getItemsStartRef();
        if (methodsRedirectPatch != null) {
            // patching constant pool size
            writeUnsignedShort(newClass, oldClassRefs.getConstantPool().getSizeStartRef(), methodsRedirectPatch.currentPoolSize);
        } else if (applyMappingsPatch != null) {
            // patching constant pool size
            writeUnsignedShort(newClass, oldClassRefs.getConstantPool().getSizeStartRef(), applyMappingsPatch.currentPoolSize);
        }

        if (utf8ItemsPatch != null) for (int[] utf8ItemPatch : utf8ItemsPatch.utf8ItemPatches) {
            if (utf8ItemPatch == null) break;
            oldUtf8ItemBytesSectionOffset = oldClassRefs.getConstantPool().getItemRefs()[utf8ItemPatch[0]] + 3;
            // copy till start of next utf8 item bytes section
            length = oldUtf8ItemBytesSectionOffset - oldClassOffset;
            arraycopy(oldClass, oldClassOffset, newClass, newClassOffset, length);
            oldClassOffset += length;
            newClassOffset += length;
            if (DEBUG) {
                debugOldUtf8ItemOffset = oldClassOffset;
                debugNewUtf8ItemOffset = newClassOffset;
            }
            // patch utf8 item length
            oldUtf8ItemLength = readUnsignedShort(oldClass, oldClassOffset - 2);
            writeUnsignedShort(newClass, newClassOffset - 2, oldUtf8ItemLength + utf8ItemPatch[1]);
            // apply utf8 info bytes section patches
            for (int i = 2; i < utf8ItemPatch.length;) {
                mappingIndex = utf8ItemPatch[i++];
                if (mappingIndex == 0) break;
                patchOffset = utf8ItemPatch[i++];
                // copy till begin of patch
                length = patchOffset - (oldClassOffset - oldUtf8ItemBytesSectionOffset);
                arraycopy(oldClass, oldClassOffset, newClass, newClassOffset, length);
                oldClassOffset += length;
                newClassOffset += length;
                // apply patch
                arraycopy(utf8Mapping.to[mappingIndex], 0, newClass, newClassOffset, utf8Mapping.to[mappingIndex].length);
                oldClassOffset += utf8Mapping.from[mappingIndex].length;
                newClassOffset += utf8Mapping.to[mappingIndex].length;
            }
            // copy remaining class byte code till utf8 item end
            length = oldUtf8ItemBytesSectionOffset + oldUtf8ItemLength - oldClassOffset;
            arraycopy(oldClass, oldClassOffset, newClass, newClassOffset, length);
            oldClassOffset += length;
            newClassOffset += length;
            if (DEBUG) {
                synchronized (System.out) {
                    System.out.println("[" + currentThread() + "] Patching UTF-8 constant pool item on position: " + utf8ItemPatch[0]);
                    debugOldUtf8ItemLength = readUnsignedShort(oldClass, debugOldUtf8ItemOffset - 2);
                    System.out.println("[" + currentThread() + "] old value: " + utf8ToString(oldClass, debugOldUtf8ItemOffset, debugOldUtf8ItemOffset + debugOldUtf8ItemLength));
                    debugNewUtf8ItemLength = readUnsignedShort(newClass, debugNewUtf8ItemOffset - 2);
                    System.out.println("[" + currentThread() + "] new value: " + utf8ToString(newClass, debugNewUtf8ItemOffset, debugNewUtf8ItemOffset + debugNewUtf8ItemLength));
                }
            }
        }
        // copy remaining pool items
        length = oldClassRefs.getConstantPool().getItemsEndRef() - oldClassOffset;
        arraycopy(oldClass, oldClassOffset, newClass, newClassOffset, length);
        oldClassOffset += length;
        newClassOffset += length;

        // add new pool items if available
        if (methodsRedirectPatch != null) {
            arraycopy(methodsRedirectPatch.poolEndPatch, 0, newClass, newClassOffset, methodsRedirectPatch.poolEndPatch.length);
            newClassOffset += methodsRedirectPatch.poolEndPatch.length;
        } else if (applyMappingsPatch != null) {
            arraycopy(applyMappingsPatch.poolEndPatch, 0, newClass, newClassOffset, applyMappingsPatch.poolEndPatch.length);
            newClassOffset += applyMappingsPatch.poolEndPatch.length;
        }

        // patching methods
        int oldMethodInfoCodeAttributeCodeOffset, oldCodeAttributeLength, oldCodeAttributeCodeLength;
        MethodInfoRefs methodInfo;
        CodeAttributeRefs codeAttribute;
        int debugOldCodeAttributeCodeOffset = -1, debugNewCodeAttributeCodeOffset = -1;
        int debugOldCodeAttributeCodeLength = -1, debugNewCodeAttributeCodeLength = -1;
        MethodsPatch methodsPatch = methodsRedirectPatch != null ? methodsRedirectPatch.methodsPatch : null; // either first patch
        methodsPatch = methodsPatch == null ? (applyMappingsPatch != null ? applyMappingsPatch.methodsPatch : null) : null; // or second patch
        if (methodsPatch != null) for (int[] methodPatch : methodsPatch.methodPatches) {
            if (methodPatch == null) break;
            methodInfo = oldClassRefs.getMethod(methodPatch[0]);
            codeAttribute = methodInfo.getCodeAttribute();
            oldMethodInfoCodeAttributeCodeOffset = codeAttribute.getCodeStartRef();
            // copy till start of next code_attribute code section
            length = oldMethodInfoCodeAttributeCodeOffset - oldClassOffset;
            arraycopy(oldClass, oldClassOffset, newClass, newClassOffset, length);
            oldClassOffset += length;
            newClassOffset += length;
            if (DEBUG) {
                debugOldCodeAttributeCodeOffset = oldClassOffset;
                debugNewCodeAttributeCodeOffset = newClassOffset;
            }
            // patch code attribute length
            oldCodeAttributeLength = readUnsignedInt(oldClass, oldClassOffset - 12);
            writeUnsignedInt(newClass, newClassOffset - 12, oldCodeAttributeLength + methodPatch[1]);
            // patch code attribute code length
            oldCodeAttributeCodeLength = readUnsignedInt(oldClass, oldClassOffset - 4);
            writeUnsignedInt(newClass, newClassOffset - 4, oldCodeAttributeCodeLength + methodPatch[1]);
            // TODO: patch here max_stack & max_locals
            // apply code attribute code section patches
            for (int i = 4; i < methodPatch.length;) {
                mappingIndex = methodPatch[i++];
                if (mappingIndex == 0) break;
                patchOffset = methodPatch[i++];
                // copy till begin of patch
                length = patchOffset - (oldClassOffset - oldMethodInfoCodeAttributeCodeOffset);
                arraycopy(oldClass, oldClassOffset, newClass, newClassOffset, length);
                oldClassOffset += length;
                newClassOffset += length;
                // apply patch
                arraycopy(methodsPatch.mappingTo[mappingIndex], 0, newClass, newClassOffset, methodsPatch.mappingTo[mappingIndex].length);
                oldClassOffset += methodsPatch.mappingFrom[mappingIndex].length;
                newClassOffset += methodsPatch.mappingTo[mappingIndex].length;
            }
            // copy remaining class byte code till code attribute end
            length = oldMethodInfoCodeAttributeCodeOffset + oldCodeAttributeLength - oldClassOffset;
            arraycopy(oldClass, oldClassOffset, newClass, newClassOffset, length);
            oldClassOffset += length;
            newClassOffset += length;
            if (DEBUG) {
                synchronized (System.out) {
                    System.out.println("[" + currentThread() + "] Patching method implementation '" + oldClassRefs.getConstantPool().getUtf8AsString(methodInfo.getNameIndex()) + "' on position: " + methodPatch[0]);
                    debugOldCodeAttributeCodeLength = readUnsignedInt(oldClass, debugOldCodeAttributeCodeOffset - 4);
                    System.out.print("[" + currentThread() + "] Old implementation bytecode: ");
                    printMethodByteCode(oldClass, debugOldCodeAttributeCodeOffset, debugOldCodeAttributeCodeLength);
                    System.out.println();
                    debugNewCodeAttributeCodeLength = readUnsignedInt(newClass, debugNewCodeAttributeCodeOffset - 4);
                    System.out.print("[" + currentThread() + "] New implementation bytecode: ");
                    printMethodByteCode(newClass, debugNewCodeAttributeCodeOffset, debugNewCodeAttributeCodeLength);
                    System.out.println();
                }
            }
        }

        // copy remaining class byte code
        arraycopy(oldClass, oldClassOffset, newClass, newClassOffset, oldClass.length - oldClassOffset);

        if (DEBUG) {
            synchronized (System.out) {
                System.out.println("[" + currentThread() + "] Patching class " + oldClassRefs.getThisClassAsString() + " - END");
            }
        }
        return newClass;
    }

}
