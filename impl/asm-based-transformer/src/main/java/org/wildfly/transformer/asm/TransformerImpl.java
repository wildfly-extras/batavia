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

    private static final boolean useASM7 = getMajorJavaVersion() >= 11;
    private boolean classTransformed;
    private boolean alreadyTransformed;

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
                // clear per class state
                clearTransformationState();
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

    private static Map <String, String> replacementMap = new HashMap<>();
    static { 
        replacementMap.put("javax/annotation/security", "jakarta/annotation/security");
        replacementMap.put("javax/annotation/sql", "jakarta/annotation/sql");
        replacementMap.put("javax/annotation/G", "jakarta/annotation/G");
        replacementMap.put("javax/annotation/M", "jakarta/annotation/M");
        replacementMap.put("javax/annotation/P", "jakarta/annotation/P");
        replacementMap.put("javax/annotation/R", "jakarta/annotation/R");
        replacementMap.put("javax/batch", "jakarta/batch");
        replacementMap.put("javax/decorator", "jakarta/decorator");
        replacementMap.put("javax/ejb", "jakarta/ejb");
        replacementMap.put("javax/el", "jakarta/el");
        replacementMap.put("javax/enterprise", "jakarta/enterprise");
        replacementMap.put("javax/faces", "jakarta/faces");
        replacementMap.put("javax/inject", "jakarta/inject");
        replacementMap.put("javax/interceptor", "jakarta/interceptor");
        replacementMap.put("javax/jms", "jakarta/jms");
        replacementMap.put("javax/json", "jakarta/json");
        replacementMap.put("javax/mail", "jakarta/mail");
        replacementMap.put("javax/persistence", "jakarta/persistence");
        replacementMap.put("javax/resource", "jakarta/resource");
        replacementMap.put("javax/security/auth", "jakarta/security/auth");
        replacementMap.put("javax/security/enterprise", "jakarta/security/enterprise");
        replacementMap.put("javax/security/jacc", "jakarta/security/jacc");
        replacementMap.put("javax/servlet", "jakarta/servlet");
        // only need to match with first letter of javax.transaction level classes
        replacementMap.put("javax/transaction/H", "jakarta/transaction/H");
        replacementMap.put("javax/transaction/I", "jakarta/transaction/I");
        replacementMap.put("javax/transaction/N", "jakarta/transaction/N");
        replacementMap.put("javax/transaction/R", "jakarta/transaction/R");
        replacementMap.put("javax/transaction/S", "jakarta/transaction/S");
        replacementMap.put("javax/transaction/T", "jakarta/transaction/T");
        replacementMap.put("javax/transaction/U", "jakarta/transaction/U");
        replacementMap.put("javax/validation", "jakarta/validation");
        replacementMap.put("javax/websocket", "jakarta/websocket");
        replacementMap.put("javax/ws/rs", "jakarta/ws/rs");
        replacementMap.put("javax.annotation.security", "jakarta.annotation.security");
        replacementMap.put("javax.annotation.sql", "jakarta.annotation.sql");
        replacementMap.put("javax.annotation.security", "jakarta.annotation.security");
        replacementMap.put("javax.annotation.sql", "jakarta.annotation.sql");
        replacementMap.put("javax.annotation.G", "jakarta.annotation.G");
        replacementMap.put("javax.annotation.M", "jakarta.annotation.M");
        replacementMap.put("javax.annotation.P", "jakarta.annotation.P");
        replacementMap.put("javax.annotation.R", "jakarta.annotation.R");
        replacementMap.put("javax.batch", "jakarta.batch");
        replacementMap.put("javax.decorator", "jakarta.decorator");
        replacementMap.put("javax.ejb", "jakarta.ejb");
        replacementMap.put("javax.el", "jakarta.el");
        replacementMap.put("javax.enterprise", "jakarta.enterprise");
        replacementMap.put("javax.faces", "jakarta.faces");
        replacementMap.put("javax.inject", "jakarta.inject");
        replacementMap.put("javax.interceptor", "jakarta.interceptor");
        replacementMap.put("javax.jms", "jakarta.jms");
        replacementMap.put("javax.json", "jakarta.json");
        replacementMap.put("javax.mail", "jakarta.mail");
        replacementMap.put("javax.persistence", "jakarta.persistence");
        replacementMap.put("javax.resource", "jakarta.resource");
        replacementMap.put("javax.security.auth", "jakarta.security.auth");
        replacementMap.put("javax.security.enterprise", "jakarta.security.enterprise");
        replacementMap.put("javax.security.jacc", "jakarta.security.jacc");
        replacementMap.put("javax.servlet", "jakarta.servlet");
        // only need to match with first letter of javax.transaction level classes
        replacementMap.put("javax.transaction.H", "jakarta.transaction.H");
        replacementMap.put("javax.transaction.I", "jakarta.transaction.I");
        replacementMap.put("javax.transaction.N", "jakarta.transaction.N");
        replacementMap.put("javax.transaction.R", "jakarta.transaction.R");
        replacementMap.put("javax.transaction.S", "jakarta.transaction.S");
        replacementMap.put("javax.transaction.T", "jakarta.transaction.T");
        replacementMap.put("javax.transaction.U", "jakarta.transaction.U");
        replacementMap.put("javax.validation", "jakarta.validation");
        replacementMap.put("javax.websocket", "jakarta.websocket");
        replacementMap.put("javax.ws.rs", "jakarta.ws.rs");
        
    };
    
    private static String replaceJavaXwithJakarta(String desc) {
        StringBuilder stringBuilder = new StringBuilder(desc);
        for(Map.Entry<String, String> possibleReplacement: replacementMap.entrySet()) {
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
        final String resourceName = r.getName();
        if (resourceName.endsWith(".class")) {
            final byte[] newClazz = transform(r.getData());
            return newClazz != null ? new Resource(resourceName, newClazz) : null;
        } else if (resourceName.endsWith(".xml")) {
            return new Resource(resourceName, xmlFile(r.getData()));
        } else if (resourceName.startsWith("META-INF/services/javax.")) {
            // rename service files like META-INF/services/javax.persistence.spi.PersistenceProvider
            // to META-INF/services/jakarta.persistence.spi.PersistenceProvider
            return new Resource(resourceName.replace("javax.", "jakarta."), r.getData());
        }
        return null; // returning null means nothing was transformed (indicates copy original content)
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
