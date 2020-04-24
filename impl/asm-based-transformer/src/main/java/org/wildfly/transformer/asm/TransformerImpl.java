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
package org.wildfly.transformer.asm;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import org.wildfly.transformer.Transformer;

/**
 * Transformer
 * <p>
 * Map javax.* classes to their jakarta.* equivalent as outlined on
 * https://github.com/eclipse-ee4j/jakartaee-platform/blob/master/namespace/mappings.adoc
 * 
 * TODO:  introduce META-INF/jakartasignature.prop file with version stamp so we can ignore jars already transformed.
 *
 * @author Scott Marlow
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class TransformerImpl implements Transformer {

    private static final String CLASS_SUFFIX = ".class";
    private static final int CLASS_SUFFIX_LENGTH = CLASS_SUFFIX.length(); 
    private static final String XML_SUFFIX = ".xml";
    private static final String META_INF_SERVICES_PREFIX = "META-INF/services/";
    private static final boolean useASM7 = getMajorJavaVersion() >= 11;
    private boolean classTransformed;
    private boolean alreadyTransformed;
    private String changeClassName;

    /**
     * {@inheritDoc}
     */
    public byte[] transform(final byte[] clazz) {
        ClassReader classReader = new ClassReader(clazz);
        final ClassWriter classWriter = new ClassWriter(classReader, 0);

        classReader.accept(new ClassVisitor(useASM7 ? Opcodes.ASM7 : Opcodes.ASM6, classWriter) {

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                final String descOrig = descriptor;
                descriptor = replaceJavaXwithJakarta(descriptor);
                if (!descOrig.equals(descriptor)) {  // if we are changing
                    // mark the class as transformed
                    setClassTransformed(true);
                }
                AnnotationVisitor av =  super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
                return new MyAnnotationVisitor(av);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                final String descOrig = descriptor;
                descriptor = replaceJavaXwithJakarta(descriptor);
                if (!descOrig.equals(descriptor)) {  // if we are changing
                    // mark the class as transformed
                    setClassTransformed(true);
                }
                AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
                return new MyAnnotationVisitor(av);
            }

            @Override
            public void visitAttribute(Attribute attribute) {
                System.out.println("fieldvisitor:getAttributeCount type = " + attribute);
                super.visitAttribute(attribute);
            }

            // clear transformed state at start of each class visit
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                if (changeClassName != null) {
                    name = changeClassName;
                    changeClassName = null;
                }
                
                if (superName != null) {
                    String superNameOrig = superName;
                    superName = replaceJavaXwithJakarta(superName);
                    if (!superNameOrig.equals(superName)) {
                        // mark the class as transformed
                        setClassTransformed(true);
                    }
                }

                for(int index = 0; index < interfaces.length; index++) {
                    String orig = interfaces[index];
                    interfaces[index] = replaceJavaXwithJakarta(interfaces[index]);
                    if (!orig.equals(interfaces[index])) {
                        // mark the class as transformed
                        setClassTransformed(true);
                    }
                }
                
                super.visit(version, access, name, signature, superName, interfaces);
            }

            // check if class has already been transformed
            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {

                final String descOrig = desc;
                desc = replaceJavaXwithJakarta(desc);
                if (!descOrig.equals(desc)) {  // if we are changing
                    // mark the class as transformed
                    setClassTransformed(true);
                }
                FieldVisitor fv = super.visitField(access, name, desc, signature, value);
                return new FieldVisitor(api, fv) {

                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        final String descOrig = descriptor;
                        descriptor = replaceJavaXwithJakarta(descriptor);
                        if (!descOrig.equals(descriptor)) {  // if we are changing
                            // mark the class as transformed
                            setClassTransformed(true);
                        }
                        AnnotationVisitor av = fv.visitAnnotation(descriptor, visible);
                        return new MyAnnotationVisitor(av);
                        
                    }

                    @Override
                    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                        final String descOrig = descriptor;
                        descriptor = replaceJavaXwithJakarta(descriptor);
                        if (!descOrig.equals(descriptor)) {  // if we are changing
                            // mark the class as transformed
                            setClassTransformed(true);
                        }
                        AnnotationVisitor av = fv.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
                        return new MyAnnotationVisitor(av);
                        
                    }
                };
            }


            // mark class as transformed (only if class transformations were made)
            @Override
            public void visitEnd() {
                super.visitEnd();
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {

                final String descOrig2 = desc;

                desc = replaceJavaXwithJakarta(desc);
                if (!descOrig2.equals(desc)) {  // if we are changing
                    // mark the class as transformed
                    setClassTransformed(true);
                }
                return new MethodVisitor(Opcodes.ASM6,
                        super.visitMethod(access, name, desc, signature, exceptions)) {

                    @Override
                    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                        final String descOrig = descriptor;
                        descriptor = replaceJavaXwithJakarta(descriptor);
                        if (!descOrig.equals(descriptor)) {  // if we are changing
                            // mark the class as transformed
                            setClassTransformed(true);
                        }
                        AnnotationVisitor av = mv.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
                        return new MyAnnotationVisitor(av);
                    }
                    
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        final String descOrig = descriptor;

                        descriptor = replaceJavaXwithJakarta(descriptor);
                        if (!descOrig.equals(descriptor)) {  // if we are changing
                            // mark the class as transformed
                            setClassTransformed(true);
                        }
                        
                        AnnotationVisitor av = mv.visitAnnotation(descriptor, visible);
                        return new MyAnnotationVisitor(av);
                    }
                    
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {

                        final String descOrig = desc;
                        desc = replaceJavaXwithJakarta(desc);
                        final String ownerOrig = owner;
                        owner = replaceJavaXwithJakarta(owner);
                        if (!descOrig.equals(desc) | !ownerOrig.equals(owner)) {  // if we are changing
                            // mark the class as transformed
                            setClassTransformed(true);
                        }
                        mv.visitMethodInsn(opcode, owner, name, desc, itf);
                    }

                    @Override
                    public void visitLdcInsn(final Object value) {
                        if (value instanceof Type) {
                            Type type = (Type) value;
                            String descOrig = type.getDescriptor();
                            String desc = replaceJavaXwithJakarta(descOrig);
                            if (!descOrig.equals(desc)) { // if we are changing
                                // mark the class as transformed
                                setClassTransformed(true);
                                mv.visitLdcInsn(Type.getType(desc));
                                return;
                            }
                        }

                        if (value instanceof String) {
                            final String typeOrig = (String) value;
                            String replacement = replaceJavaXwithJakarta((String) value);
                            if (!typeOrig.equals(replacement)) {  // if we are changing
                                // mark the class as transformed
                                setClassTransformed(true);
                                mv.visitLdcInsn(replacement);
                                return;
                            }
                        }
                        mv.visitLdcInsn(value);
                    }

                    @Override
                    public void visitLocalVariable(
                            final String name,
                            final String descriptor,
                            final String signature,
                            final Label start,
                            final Label end,
                            final int index) {

                        final String descOrig = descriptor;
                        final String replacement = replaceJavaXwithJakarta(descriptor);
                        if (!descOrig.equals(replacement)) {  // if we are changing
                            // mark the class as transformed
                            setClassTransformed(true);
                        }
                        mv.visitLocalVariable(name, replacement, signature, start, end, index);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        final String descOrig = desc;
                        desc = replaceJavaXwithJakarta(desc);
                        final String ownerOrig = owner;
                        owner = replaceJavaXwithJakarta(owner);
                        if (!descOrig.equals(desc) | !ownerOrig.equals(owner)) {  // if we are changing
                            // mark the class as transformed
                            setClassTransformed(true);
                        }
                        mv.visitFieldInsn(opcode, owner, name, desc);
                    }

                    @Override
                    public void visitTypeInsn(final int opcode, final String type) {
                        final String typeOrig = type;

                        final String replacement = replaceJavaXwithJakarta(type);
                        if (!typeOrig.equals(replacement)) {  // if we are changing
                            // mark the class as transformed
                            setClassTransformed(true);
                        }
                        mv.visitTypeInsn(opcode, replacement);
                    }

                };
            }
        }, 0);
        if (!transformationsMade()) {
            // no change was made, indicate so by returning null
            return null;
        }

        return classWriter.toByteArray();
    }

    private static Map <String, String> mappingWithSeps = new HashMap<>();
    private static Map <String, String> mappingWithDots = new HashMap<>();
    static {
        // init map with '/'
        mappingWithSeps.put("javax/annotation/security", "jakarta/annotation/security");
        mappingWithSeps.put("javax/annotation/sql", "jakarta/annotation/sql");
        mappingWithSeps.put("javax/annotation/G", "jakarta/annotation/G");
        mappingWithSeps.put("javax/annotation/M", "jakarta/annotation/M");
        mappingWithSeps.put("javax/annotation/P", "jakarta/annotation/P");
        mappingWithSeps.put("javax/annotation/R", "jakarta/annotation/R");
        mappingWithSeps.put("javax/batch", "jakarta/batch");
        mappingWithSeps.put("javax/decorator", "jakarta/decorator");
        mappingWithSeps.put("javax/ejb", "jakarta/ejb");
        mappingWithSeps.put("javax/el", "jakarta/el");
        mappingWithSeps.put("javax/enterprise", "jakarta/enterprise");
        mappingWithSeps.put("javax/faces", "jakarta/faces");
        mappingWithSeps.put("javax/inject", "jakarta/inject");
        mappingWithSeps.put("javax/interceptor", "jakarta/interceptor");
        mappingWithSeps.put("javax/jms", "jakarta/jms");
        mappingWithSeps.put("javax/json", "jakarta/json");
        mappingWithSeps.put("javax/mail", "jakarta/mail");
        mappingWithSeps.put("javax/persistence", "jakarta/persistence");
        mappingWithSeps.put("javax/resource", "jakarta/resource");
        mappingWithSeps.put("javax/security/auth", "jakarta/security/auth");
        mappingWithSeps.put("javax/security/enterprise", "jakarta/security/enterprise");
        mappingWithSeps.put("javax/security/jacc", "jakarta/security/jacc");
        mappingWithSeps.put("javax/servlet", "jakarta/servlet");
        // only need to match with first letter of javax.transaction level classes
        mappingWithSeps.put("javax/transaction/H", "jakarta/transaction/H");
        mappingWithSeps.put("javax/transaction/I", "jakarta/transaction/I");
        mappingWithSeps.put("javax/transaction/N", "jakarta/transaction/N");
        mappingWithSeps.put("javax/transaction/R", "jakarta/transaction/R");
        mappingWithSeps.put("javax/transaction/S", "jakarta/transaction/S");
        mappingWithSeps.put("javax/transaction/T", "jakarta/transaction/T");
        mappingWithSeps.put("javax/transaction/U", "jakarta/transaction/U");
        mappingWithSeps.put("javax/validation", "jakarta/validation");
        mappingWithSeps.put("javax/websocket", "jakarta/websocket");
        mappingWithSeps.put("javax/ws/rs", "jakarta/ws/rs");
        // init map with '.'
        mappingWithDots.put("javax.annotation.security", "jakarta.annotation.security");
        mappingWithDots.put("javax.annotation.sql", "jakarta.annotation.sql");
        mappingWithDots.put("javax.annotation.G", "jakarta.annotation.G");
        mappingWithDots.put("javax.annotation.M", "jakarta.annotation.M");
        mappingWithDots.put("javax.annotation.P", "jakarta.annotation.P");
        mappingWithDots.put("javax.annotation.R", "jakarta.annotation.R");
        mappingWithDots.put("javax.batch", "jakarta.batch");
        mappingWithDots.put("javax.decorator", "jakarta.decorator");
        mappingWithDots.put("javax.ejb", "jakarta.ejb");
        mappingWithDots.put("javax.el", "jakarta.el");
        mappingWithDots.put("javax.enterprise", "jakarta.enterprise");
        mappingWithDots.put("javax.faces", "jakarta.faces");
        mappingWithDots.put("javax.inject", "jakarta.inject");
        mappingWithDots.put("javax.interceptor", "jakarta.interceptor");
        mappingWithDots.put("javax.jms", "jakarta.jms");
        mappingWithDots.put("javax.json", "jakarta.json");
        mappingWithDots.put("javax.mail", "jakarta.mail");
        mappingWithDots.put("javax.persistence", "jakarta.persistence");
        mappingWithDots.put("javax.resource", "jakarta.resource");
        mappingWithDots.put("javax.security.auth", "jakarta.security.auth");
        mappingWithDots.put("javax.security.enterprise", "jakarta.security.enterprise");
        mappingWithDots.put("javax.security.jacc", "jakarta.security.jacc");
        mappingWithDots.put("javax.servlet", "jakarta.servlet");
        // only need to match with first letter of javax.transaction level classes
        mappingWithDots.put("javax.transaction.H", "jakarta.transaction.H");
        mappingWithDots.put("javax.transaction.I", "jakarta.transaction.I");
        mappingWithDots.put("javax.transaction.N", "jakarta.transaction.N");
        mappingWithDots.put("javax.transaction.R", "jakarta.transaction.R");
        mappingWithDots.put("javax.transaction.S", "jakarta.transaction.S");
        mappingWithDots.put("javax.transaction.T", "jakarta.transaction.T");
        mappingWithDots.put("javax.transaction.U", "jakarta.transaction.U");
        mappingWithDots.put("javax.validation", "jakarta.validation");
        mappingWithDots.put("javax.websocket", "jakarta.websocket");
        mappingWithDots.put("javax.ws.rs", "jakarta.ws.rs");
    };
    
    private static String replaceJavaXwithJakarta(String desc) {
        StringBuilder stringBuilder = new StringBuilder(desc);
        for(Map.Entry<String, String> possibleReplacement: mappingWithSeps.entrySet()) {
            String key = possibleReplacement.getKey();
            String value = possibleReplacement.getValue();
            int pos = stringBuilder.indexOf(key, 0);
            while(pos > -1) {
                int length = pos  + key.length();
                int next = pos + value.length();
                stringBuilder.replace(pos, length, value);
                pos = stringBuilder.indexOf(key, next);
            }
        }
        return stringBuilder.toString();
    }
    
    private static int getMajorJavaVersion() {
        int major = 8;
        String version = System.getProperty("java.specification.version", null);
        if (version != null) {
            Matcher matcher = Pattern.compile("^(?:1\\.)?(\\d+)$").matcher(version);
            if (matcher.find()) {
                major = Integer.valueOf(matcher.group(1));
            }
        }
        return major;
    }

    public void setClassTransformed(boolean classTransformed) {
        this.classTransformed = classTransformed;
    }

    public void setAlreadyTransformed(boolean alreadyTransformed) {
        this.alreadyTransformed = alreadyTransformed;
    }

    public boolean transformationsMade() {
        return !alreadyTransformed && classTransformed;
    }

    public void clearTransformationState() {
        alreadyTransformed = classTransformed = false;
    }

    @Override
    public Resource transform(final Resource r) {
        
        
        String oldResourceName = r.getName();
        String newResourceName = replacePackageName(oldResourceName, false);
        if (oldResourceName.endsWith(CLASS_SUFFIX)) {
            clearTransformationState(); // clear transformation state for each class, prior to class transformation
                    
            if (!newResourceName.equals(oldResourceName)) {  // any file rename counts as a transformation 
                setClassTransformed(true);
                setNewClassName(newResourceName);
            }
                    
            final byte[] newClazz = transform(r.getData());
            if (newClazz != null) return new Resource(newResourceName, newClazz);
        } else if (oldResourceName.endsWith(XML_SUFFIX)) {
            return new Resource(newResourceName, xmlFile(r.getData()));
        } else if (oldResourceName.startsWith(META_INF_SERVICES_PREFIX)) {
            newResourceName = replacePackageName(oldResourceName, true);
            if (!newResourceName.equals(oldResourceName)) {
                return new Resource(newResourceName, r.getData());
            }
        } else if (!newResourceName.equals(oldResourceName)) {
            return new Resource(newResourceName, r.getData());
        }
        return null; // returning null means nothing was transformed (indicates copy original content)
    }

    private void setNewClassName(String newClassName) {
        if (newClassName.endsWith(CLASS_SUFFIX)) {
            newClassName = newClassName.substring(0,newClassName.length() - CLASS_SUFFIX_LENGTH);
        }
        changeClassName = newClassName;
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
            return new String(data, "UTF-8").replace("javax.", "jakarta.").getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return null; // should never happen
        }
    }

    private static class MyAnnotationVisitor extends AnnotationVisitor {
        public MyAnnotationVisitor(AnnotationVisitor av) {
            super(useASM7 ? Opcodes.ASM7 : Opcodes.ASM6, av);
        }

        @Override
        public void visit(String name, Object value) {
            av.visit(name, value);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            if (descriptor != null) {
                descriptor = replaceJavaXwithJakarta(descriptor);
            }
            av.visitEnum(name, descriptor, value);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            if (descriptor != null) {
                descriptor = replaceJavaXwithJakarta(descriptor);
            }
            AnnotationVisitor av2 = av.visitAnnotation(name, descriptor);
            return new MyAnnotationVisitor(av2);
            
            
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            AnnotationVisitor av2 = av.visitArray(name);
            return new MyAnnotationVisitor(av2);
        }

    }
}
