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

package org.convert2ee9;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

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

/**
 * Transformer
 * <p>
 * Map javax.* classes to their jakarta.* equivalent as outlined on
 * https://github.com/eclipse-ee4j/jakartaee-platform/blob/master/namespace/mappings.adoc
 * 
 * TODO:  introduce META-INF/jakartasignature.prop file with version stamp so we can ignore jars already transformed.
 *
 * @author Scott Marlow
 */
public class Transformer implements ClassFileTransformer {

    private static final boolean useASM7 = getMajorJavaVersion() >= 11;
    private boolean classTransformed;
    private boolean alreadyTransformed;

    public byte[] transform(final ClassReader classReader) {

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

        byte[] result = classWriter.toByteArray();
        return result;
    }

    @Override
    public byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined, final ProtectionDomain protectionDomain, final byte[] classfileBuffer) throws IllegalClassFormatException {
        final ClassReader classReader = new ClassReader(classfileBuffer);
        return transform(classReader);
    }

    private static Map <String, String> replacementMap = new HashMap<>();
    static { 
        replacementMap.put("javax/annotation/security", "jakarta/annotation/security");
        replacementMap.put("javax/annotation/sql", "jakarta/annotation/sql");
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
        replacementMap.put("javax/management/j2ee", "jakarta/management/j2ee");
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
        replacementMap.put("javax.management.j2ee", "jakarta.management.j2ee");
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
/*    
    private static String replaceJavaXwithJakarta(String desc) {
        // TODO: try using a regular expression
        // note that we will ignore JDK javax.transaction.xa classes
        String result = desc.
                replace("javax/annotation/security", "jakarta/annotation/security").
                replace("javax/annotation/sql", "jakarta/annotation/sql").
                replace("javax/batch", "jakarta/batch").
                replace("javax/decorator", "jakarta/decorator").
                replace("javax/ejb", "jakarta/ejb").
                replace("javax/el", "jakarta/el").
                replace("javax/enterprise", "jakarta/enterprise").
                replace("javax/faces", "jakarta/faces").
                replace("javax/inject", "jakarta/inject").
                replace("javax/interceptor", "jakarta/interceptor").
                replace("javax/jms", "jakarta/jms").
                replace("javax/json", "jakarta/json").
                replace("javax/mail", "jakarta/mail").
                replace("javax/management/j2ee", "jakarta/management/j2ee").
                replace("javax/persistence", "jakarta/persistence").
                replace("javax/resource", "jakarta/resource").
                replace("javax/security/auth", "jakarta/security/auth").
                replace("javax/security/enterprise", "jakarta/security/enterprise").
                replace("javax/security/jacc", "jakarta/security/jacc").
                replace("javax/servlet", "jakarta/servlet").
                // only need to match with first letter of javax.transaction level classes
                        replace("javax/transaction/H", "jakarta/transaction/H").
                        replace("javax/transaction/I", "jakarta/transaction/I").
                        replace("javax/transaction/N", "jakarta/transaction/N").
                        replace("javax/transaction/R", "jakarta/transaction/R").
                        replace("javax/transaction/S", "jakarta/transaction/S").
                        replace("javax/transaction/T", "jakarta/transaction/T").
                        replace("javax/transaction/U", "jakarta/transaction/U").
                        replace("javax/validation", "jakarta/validation").
                        replace("javax/websocket", "jakarta/websocket").
                        replace("javax/ws/rs", "jakarta/ws/rs");

        return result;
    }

    private static String replaceDottedJavaXwithJakarta(String desc) {
        // note that we will ignore JDK javax.transaction.xa classes
        String result = desc.
                replace("javax.annotation.security", "jakarta.annotation.security").
                replace("javax.annotation.sql", "jakarta.annotation.sql").
                replace("javax.batch", "jakarta.batch").
                replace("javax.decorator", "jakarta.decorator").
                replace("javax.ejb", "jakarta.ejb").
                replace("javax.el", "jakarta.el").
                replace("javax.enterprise", "jakarta.enterprise").
                replace("javax.faces", "jakarta.faces").
                replace("javax.inject", "jakarta.inject").
                replace("javax.interceptor", "jakarta.interceptor").
                replace("javax.jms", "jakarta.jms").
                replace("javax.json", "jakarta.json").
                replace("javax.mail", "jakarta.mail").
                replace("javax.management.j2ee", "jakarta.management.j2ee").
                replace("javax.persistence", "jakarta.persistence").
                replace("javax.resource", "jakarta.resource").
                replace("javax.security.auth", "jakarta.security.auth").
                replace("javax.security.enterprise", "jakarta.security.enterprise").
                replace("javax.security.jacc", "jakarta.security.jacc").
                replace("javax.servlet", "jakarta.servlet").
                // only need to match with first letter of javax.transaction level classes
                        replace("javax.transaction.H", "jakarta.transaction.H").
                        replace("javax.transaction.I", "jakarta.transaction.I").
                        replace("javax.transaction.N", "jakarta.transaction.N").
                        replace("javax.transaction.R", "jakarta.transaction.R").
                        replace("javax.transaction.S", "jakarta.transaction.S").
                        replace("javax.transaction.T", "jakarta.transaction.T").
                        replace("javax.transaction.U", "jakarta.transaction.U").
                        replace("javax.validation", "jakarta.validation").
                        replace("javax.websocket", "jakarta.websocket").
                        replace("javax.ws.rs", "jakarta.ws.rs");

        return result;
    }
  */
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

    public static void main(final String... args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: java -cp convert2ee9-1.0.0.Alpha1-SNAPSHOT.jar:asm-7.1.jar org.convert2ee9.Transformer" +
                    Transformer.class + " sourceClassFile targetClassFile");
            return;
        }
        // configure transformer
        String to = null;
        Transformer t = new Transformer();
        // get original class content
        final ByteArrayOutputStream targetBAOS = new ByteArrayOutputStream();
        final Path source = Paths.get(args[0]);
        final Path target = Paths.get(args[1]);
        if (source.toString().endsWith(".jar")) {
            jarFile(t, source, target);
        } else {
            classFile(t, source, target);
        }
    }

    private static void jarFile(final Transformer t, final Path source, final Path target) throws IOException, IllegalClassFormatException {

        if (source.toString().endsWith(".jar")) {
            JarFile jarFileSource = new JarFile(source.toFile());
            FileOutputStream fileOutputStream = new FileOutputStream(target.toFile());
            JarOutputStream jarOutputStream = new JarOutputStream(fileOutputStream);
            try {

                for (Enumeration<JarEntry> entries = jarFileSource.entries(); entries.hasMoreElements(); ) {
                    JarEntry jarEntry = entries.nextElement();
                    String name = jarEntry.getName();
                    if (jarEntry.getName().endsWith(".xml")) {
                        ZipEntry zipEntrySource = jarFileSource.getEntry(name);
                        String targetName = jarEntry.getName();
                        xmlFile(targetName, jarFileSource.getInputStream(zipEntrySource), jarOutputStream);
                    } else if (jarEntry.getName().endsWith(".class")) {
                        jarFileEntry(t, jarEntry, jarFileSource, jarOutputStream);
                    } else if (jarEntry.getName().endsWith("/")) {
                    } else if (jarEntry.getName().startsWith("META-INF/services/javax.")) {
                        // rename service files like META-INF/services/javax.persistence.spi.PersistenceProvider
                        // to META-INF/services/jakarta.persistence.spi.PersistenceProvider
                        ZipEntry zipEntrySource = jarFileSource.getEntry(name);
                        String targetName = jarEntry.getName().replace("javax.", "jakarta.");
                        copyFile(targetName, jarFileSource.getInputStream(zipEntrySource), jarOutputStream);
                    } else {
                        ZipEntry zipEntrySource = jarFileSource.getEntry(name);
                        String targetName = jarEntry.getName();
                        copyFile(targetName, jarFileSource.getInputStream(zipEntrySource), jarOutputStream);
                    }
                }
            } finally {
                jarOutputStream.close();
                jarFileSource.close();
                fileOutputStream.close();
            }

        } else {
            System.err.println("unexpected file extension type " + source.toString());
        }
    }

    private static void xmlFile(String targetName, InputStream inputStream, JarOutputStream jarOutputStream) throws IOException {
        try {
            ByteArrayOutputStream xmlOutputStream = new ByteArrayOutputStream();
            byte[] xmlBuffer = new byte[16384];
            int length;
            while ((length = inputStream.read(xmlBuffer)) != -1) {
                xmlOutputStream.write(xmlBuffer, 0, length);
            }
            String xmlValue = xmlOutputStream.toString("UTF-8").replace("javax.", "jakarta.");
            xmlBuffer = xmlValue.getBytes("UTF-8");
            jarOutputStream.putNextEntry(new JarEntry(targetName));
            jarOutputStream.write(xmlBuffer);
        } finally {
            inputStream.close();
        }
    }

    private static void copyFile(String targetName, InputStream inputStream, JarOutputStream jarOutputStream) throws IOException {
        try {
            jarOutputStream.putNextEntry(new JarEntry(targetName));
            final byte[] buffer = new byte[16384];
            int count;
            while ((count = inputStream.read(buffer)) != -1) {
                jarOutputStream.write(buffer, 0, count);
            }
        } finally {
            jarOutputStream.closeEntry();
            inputStream.close();
            
        }
    }
    
    // transform class in an archive file
    private static void jarFileEntry(final Transformer t, final JarEntry jarEntry, final JarFile jarFileSource, final JarOutputStream jarOutputStream) throws IOException, IllegalClassFormatException {
        ZipEntry zipEntrySource = jarFileSource.getEntry(jarEntry.getName());
        InputStream inputStream = jarFileSource.getInputStream(zipEntrySource);
        if (jarEntry.getSize() > Integer.MAX_VALUE) {
            System.out.println("error " + jarEntry.getName() +" class is larger than Integer.MAX_VALUE, getSize() =  " + jarEntry.getSize() );
            System.exit(1);
        }
        byte [] byteBuffer = new byte [(int)jarEntry.getSize()];
        int offset = 0;
        int numRead = 0;
        while (offset < byteBuffer.length && (numRead = inputStream.read(byteBuffer, offset, byteBuffer.length - offset)) >= 0) {
            offset += numRead;
        }
        if (offset != byteBuffer.length) {
            System.out.println("error reading bytes from " + jarEntry.getName() +", expected to read " + byteBuffer.length + " but only read " + offset);
            System.exit(1);
        }
        InputStream sourceBAIS = null;
        try {
            final byte[] targetBytes = t.transform(null, null, null, null, byteBuffer);
            if (targetBytes != null) {
                // will write modified class content
                sourceBAIS = new ByteArrayInputStream(targetBytes);
            } else {
                // copy original class
                sourceBAIS = new ByteArrayInputStream(byteBuffer); 
            }

            jarOutputStream.putNextEntry(new JarEntry(jarEntry.getName()));
            final byte[] buffer = new byte[16384];
            int sourceCounter;
            while ((sourceCounter = sourceBAIS.read(buffer)) != -1) {
                jarOutputStream.write(buffer, 0, sourceCounter);
            }
        } finally {
            jarOutputStream.closeEntry();
            inputStream.close();
            if (sourceBAIS != inputStream && sourceBAIS != null) {
                sourceBAIS.close();
            }
        }
    }

    private static void classFile(final Transformer t, final Path source, final Path target) throws IOException {
        if (source.toString().endsWith(".class")) {
            InputStream sourceBAIS = null;
            InputStream inputStream = Files.newInputStream(source);
            try {
                ClassReader classReader = new ClassReader(inputStream);
                final byte[] targetBytes = t.transform(classReader);
                if (targetBytes != null) {
                    // write modified class content
                    sourceBAIS = new ByteArrayInputStream(targetBytes);
                } else {
                    sourceBAIS = Files.newInputStream(source);
                }
                Files.copy(sourceBAIS, target);
            } finally {
                inputStream.close();
                if (sourceBAIS != null) {
                    sourceBAIS.close();
                }
            }
        } else {
            System.err.println("unexpected file extension type " + source.toString());
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
