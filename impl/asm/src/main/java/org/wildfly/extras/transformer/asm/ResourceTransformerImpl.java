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
package org.wildfly.extras.transformer.asm;

import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.POP;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import org.wildfly.extras.transformer.ResourceTransformer;

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
final class ResourceTransformerImpl extends ResourceTransformer {
    private static final Resource[] EMPTY_ARRAY = new Resource[0];
    private static final String CLASS_SUFFIX = ".class";
    private static final int CLASS_SUFFIX_LENGTH = CLASS_SUFFIX.length(); 
    private static final String XML_SUFFIX = ".xml";
    private static final String TLD_SUFFIX = ".tld";
    private static final String JSP_SUFFIX = ".jsp";
    private static final String META_INF_SERVICES_PREFIX = "META-INF/services/";
    private static final String CLASS_FOR_NAME_PRIVATE_METHOD = "org_wildfly_tranformer_asm_classForName_String__boolean_ClassLoader";
    private static final String REFLECTIONMODEL_INTERNAL_NAME = ReflectionModel.class.getName().replace('.','/');
    private static final String CLASS_OBJECT = "java/lang/Class";
    private static final String FORNAME_METHOD = "forName";
    private static final String MAP_OBJECT = "java/util/Map";
    private static final String MAP_PUT_METHOD = "put";
    private static final String MAP_PUT_METHOD_DESC = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String MAP_GET_METHOD = "get";
    
    private boolean classTransformed;
    private String changeClassName;
    final Set<String> generatedReflectionModelHandlingCode = new CopyOnWriteArraySet<>();

    ResourceTransformerImpl(final File configsDir, final boolean verbose, final boolean invert) throws IOException {
        super(configsDir, verbose, invert);
    }

    /**
     * Transform passed classes and possibly generated one extra class containing bytecode generated from ReflectionModel.
     * 
     * @return EMPTY_ARRAY if no modification made, otherwise, Resource[1] for only modified class,
     * Resource[2] for modified class + extra class containing bytecode generated from ReflectionModel 
     */
    private Resource[] transform(String newResourceName, final byte[] clazz) {
        // generatedExtraClass[0] can hold byte[] generatedReflectionModelHandlingByteCode
        // generatedExtraClass[1] can hold String generatedReflectionModelHandlingClassName
        final Object[] generatedExtraClass = new Object [2];

        ClassReader classReader = new ClassReader(clazz);
        final ClassWriter classWriter = new ClassWriter(classReader, 0);

        classReader.accept(new ClassVisitor(Opcodes.ASM7, classWriter) {
            @Override
            public void visitOuterClass(String owner, String name, String descriptor) {
                super.visitOuterClass(owner, name, replaceJavaXwithJakarta(descriptor));
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                AnnotationVisitor av =  super.visitTypeAnnotation(typeRef, typePath, replaceJavaXwithJakarta(descriptor), visible);
                return new MyAnnotationVisitor(av);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                AnnotationVisitor av = super.visitAnnotation(replaceJavaXwithJakarta(descriptor), visible);
                return new MyAnnotationVisitor(av);
            }

            @Override
            public void visitAttribute(Attribute attribute) {
                super.visitAttribute(attribute);
            }

            // clear transformed state at start of each class visit
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {

                signature = replaceJavaXwithJakarta(signature);

                if (changeClassName != null) {
                    name = changeClassName;
                    changeClassName = null;
                }

                superName = replaceJavaXwithJakarta(superName);

                for(int index = 0; index < interfaces.length; index++) {
                    interfaces[index] = replaceJavaXwithJakarta(interfaces[index]);
                }

                super.visit(version, access, name, signature, superName, interfaces);
            }

            // check if class has already been transformed
            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {

                signature = replaceJavaXwithJakarta(signature);

                desc = replaceJavaXwithJakarta(desc);
                FieldVisitor fv = super.visitField(access, name, desc, signature, value);
                return new FieldVisitor(api, fv) {

                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        descriptor = replaceJavaXwithJakarta(descriptor);
                        AnnotationVisitor av = fv.visitAnnotation(descriptor, visible);
                        return new MyAnnotationVisitor(av);
                    }

                    @Override
                    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                        descriptor = replaceJavaXwithJakarta(descriptor);
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

                if (signature != null) {
                    signature = replaceJavaXwithJakarta(signature);
                }
                desc = replaceJavaXwithJakarta(desc);
                if (exceptions != null) {
                    for(int looper = 0; looper < exceptions.length; looper++) {
                        exceptions[looper] = replaceJavaXwithJakarta(exceptions[looper]);
                    }
                }
                return new MethodVisitor(Opcodes.ASM6,
                        super.visitMethod(access, name, desc, signature, exceptions)) {
                    
                    // Transform javax types in stack frames 
                    @Override
                    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
                        Object[] modifiedLocal = null;
                        Object[] modifiedStack = null;
                        for (int looper = 0; looper < numLocal;looper ++) {
                            if(local[looper] instanceof String) {
                                String value = replaceJavaXwithJakarta((String)local[looper]);
                                if( local[looper] != null && (false == local[looper].equals(value))) {
                                    // if transformation to jakarta type was made, copy original stack frame and use new value
                                    if (modifiedLocal == null) {
                                        modifiedLocal = new Object[numLocal];
                                        for (int innerLooper = 0; innerLooper < numLocal;innerLooper ++) {
                                            modifiedLocal[innerLooper] = local[innerLooper];
                                        }
                                    }
                                    modifiedLocal[looper] = value;
                                }
                            }
                        }
                        for (int looper = 0; looper < numStack;looper ++) {
                            if(stack[looper] instanceof String) {
                                String value = replaceJavaXwithJakarta((String)stack[looper]);
                                // if transformation to jakarta type was made, copy original stack frame and use new value
                                if( stack[looper] != null && (false == stack[looper].equals(value))) {
                                    if (modifiedStack == null) {
                                        modifiedStack = new Object[numStack];
                                        for (int innerLooper = 0; innerLooper < numStack;innerLooper ++) {
                                            modifiedStack[innerLooper] = stack[innerLooper];
                                        }
                                    }
                                    modifiedStack[looper] = value;
                                }
                            }
                        }
                        
                        super.visitFrame(type, 
                                numLocal, modifiedLocal != null ? modifiedLocal:local, 
                                numStack, modifiedStack != null ? modifiedStack: stack);
                    }

                    @Override
                    public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                        return super.visitParameterAnnotation(parameter, replaceJavaXwithJakarta(descriptor), visible);
                    }

                    @Override
                    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                        super.visitTryCatchBlock(start, end, handler, replaceJavaXwithJakarta(type));
                    }

                    @Override
                    public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                        descriptor = replaceJavaXwithJakarta(descriptor);

                        return new MyAnnotationVisitor(mv.visitInsnAnnotation(typeRef, typePath, descriptor, visible));
                    }

                    @Override
                    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
                        descriptor = replaceJavaXwithJakarta(descriptor);
                        return new MyAnnotationVisitor(mv.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible));
                    }

                    @Override
                    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                        descriptor = replaceJavaXwithJakarta(descriptor);
                        super.visitMultiANewArrayInsn(descriptor, numDimensions);
                    }

                    @Override
                    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                        descriptor = replaceJavaXwithJakarta(descriptor);
                        return new MyAnnotationVisitor(mv.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible));
                    }

                    @Override
                    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {

                        descriptor = replaceJavaXwithJakarta(descriptor);
                        AnnotationVisitor av = mv.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
                        return new MyAnnotationVisitor(av);
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        descriptor = replaceJavaXwithJakarta(descriptor);
                        AnnotationVisitor av = mv.visitAnnotation(descriptor, visible);
                        return new MyAnnotationVisitor(av);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {


                        desc = replaceJavaXwithJakarta(desc);
                        owner = replaceJavaXwithJakarta(owner);

                        // handle Class.forName(String name, boolean initialize,ClassLoader loader)
                        // invokestatic  #17 Method java/lang/Class.forName:(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;
                        if (opcode == Opcodes.INVOKESTATIC &&
                            owner.equals(CLASS_OBJECT) && name.equals(FORNAME_METHOD)) {
                            // handle both current forms ("(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;") 
                            // generate package name for generating copy of ReflectionModel class in application 
                            owner = transformerClassPackageName();
                            if (owner != null ) {
                                owner = owner + "/" + CLASS_FOR_NAME_PRIVATE_METHOD;
                            } else {
                                owner = CLASS_FOR_NAME_PRIVATE_METHOD;
                            }

                            generateReflectionHandlingModelCode(owner);
                            System.out.println("changing call to Class#" + name + " to instead call " + owner + "/" + CLASS_FOR_NAME_PRIVATE_METHOD + "#" + name);
                            setClassTransformed(true);
                        }

                        mv.visitMethodInsn(opcode, owner, name, desc, itf);
                    }

                    private void generateReflectionHandlingModelCode(String handlingClassName) {
                        // check if we generated reflection handling code yet, if not, generate it

                        if (!generatedReflectionModelHandlingCode.contains(handlingClassName)) {

                            if (!generatedReflectionModelHandlingCode.add(handlingClassName)) {
                                // another thread will (or did) generate extra code 
                                return;
                            }

                            System.out.println("Generating reflection handling code " + handlingClassName);
                            try {
                                // read BataviaReflectionModel bytecode as byte array, then modify it for handling javax => Jakarta transformation rules
                                
                                InputStream inputStream = ReflectionModel.class.getClassLoader().getResourceAsStream(ReflectionModel.class.getName().replace('.','/')+".class");
                                ByteArrayOutputStream out = new ByteArrayOutputStream();
                                int read;
                                byte[] byteArray = new byte[3000];
                                while ( (read = inputStream.read(byteArray, 0, byteArray.length) ) != -1) {
                                    out.write( byteArray, 0, read );
                                }
                                out.flush();
                                byte[] bataviaReflectionModel = out.toByteArray();        
                                ClassReader bataviaReflectionModelClassReader = new ClassReader(bataviaReflectionModel);
                                final ClassWriter bataviaReflectionModelClassWriter = new ClassWriter(bataviaReflectionModelClassReader, ClassWriter.COMPUTE_FRAMES);
                                bataviaReflectionModelClassReader.accept(new ClassVisitor(Opcodes.ASM7, bataviaReflectionModelClassWriter) {
                                    @Override
                                    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                                        System.out.println("change ReflectionModel class name from " + name + " to " + handlingClassName + 
                                                " keep superName = " + superName);
                                        // change ReflectionModel class reference to new generated class name
                                        name = handlingClassName; 
                                        super.visit(version, access, name, signature, superName, interfaces);                                        
                                    }

                                    @Override
                                    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                                        return new MethodVisitor(Opcodes.ASM6,
                                                super.visitMethod(access, name, desc, signature, exceptions)) {

                                            // trigger on mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
                                            @Override
                                            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                                // first generate the call to invoke mapping.get("rules_are_here") call
                                                if (owner.equals(REFLECTIONMODEL_INTERNAL_NAME)) {
                                                    owner = handlingClassName;
                                                }
                                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);   
                                                // then if its actually the call to mapping.get, add additional code for adding all transformation rules to mapping
                                                if (MAP_OBJECT.equals(owner) && MAP_GET_METHOD.equals(name)) {  
                                                    System.out.println("Injecting transformation rules");
                                                    super.visitInsn(POP);  // generate extra pop for the call to mapping.get("rules_are_here")
                                                    
                                                    for (Map.Entry<String, String> possibleReplacement : mappingWithSeps.entrySet()) {
                                                        super.visitFieldInsn(GETSTATIC, handlingClassName, "mapping", "Ljava/util/Map;");
                                                        super.visitLdcInsn(possibleReplacement.getKey());
                                                        super.visitLdcInsn(possibleReplacement.getValue());
                                                        super.visitMethodInsn(INVOKEINTERFACE, MAP_OBJECT, MAP_PUT_METHOD, 
                                                                MAP_PUT_METHOD_DESC, true);
                                                        super.visitInsn(POP);
                                                    }
                                                    int setIterationCount = 0;
                                                    for (Map.Entry<String, String> possibleReplacement : mappingWithDots.entrySet()) {
                                                        super.visitFieldInsn(GETSTATIC, handlingClassName, "mapping", "Ljava/util/Map;");
                                                        super.visitLdcInsn(possibleReplacement.getKey());
                                                        super.visitLdcInsn(possibleReplacement.getValue());
                                                        super.visitMethodInsn(INVOKEINTERFACE, MAP_OBJECT, MAP_PUT_METHOD, 
                                                                MAP_PUT_METHOD_DESC, true);
                                                        setIterationCount++;
                                                        if (setIterationCount == mappingWithDots.entrySet().size()) {
                                                            // don't generate the last call to pop, as the first call to mapping.get("rules_are_here") will generate an extra pop here
                                                        }
                                                        else { 
                                                            super.visitInsn(POP);
                                                        }
                                                    }
                                                }
                                            }

                                            @Override
                                            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                                                if (owner.equals(REFLECTIONMODEL_INTERNAL_NAME)) {
                                                    owner = handlingClassName;
                                                }
                                                super.visitFieldInsn(opcode, owner, name, descriptor);
                                            }
                                        };
                                    }
                                }, 0);
                                
                                byte[] result = bataviaReflectionModelClassWriter.toByteArray();
                                
                                // generatedExtraClass[0] can hold byte[] generatedReflectionModelHandlingByteCode
                                // generatedExtraClass[1] can hold String generatedReflectionModelHandlingClassName
                                generatedExtraClass[0] = result;  
                                generatedExtraClass[1] = handlingClassName +  CLASS_SUFFIX;
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    private String transformerClassPackageName() {
                        String transformedPackage = classReader.getClassName();
                        // determine the package name
                        int index = transformedPackage.lastIndexOf('/');
                        if (index > -1) {
                            transformedPackage = transformedPackage.substring(0,index);
                        }
                        else {
                            return null;  // no package used
                        }
                        return transformedPackage; 
                    }
                    
                    @Override
                    public void visitInvokeDynamicInsn(String name, String desc, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                        final String descOrig = desc;
                        desc = replaceJavaXwithJakarta(desc);
                        final String ownerOrig = bootstrapMethodHandle.getOwner();
                        String bootstrapMethodHandleOwner = replaceJavaXwithJakarta(ownerOrig);
                        final String bootstrapMethodHandleDescOrig = bootstrapMethodHandle.getDesc(); 
                        String bootstrapMethodHandleDesc = replaceJavaXwithJakarta(bootstrapMethodHandleDescOrig);
                        if (!descOrig.equals(desc)) {  // if we are changing
                            // mark the class as transformed
                            setClassTransformed(true);
                        }
                        if (!ownerOrig.equals(bootstrapMethodHandleOwner) ||
                        !bootstrapMethodHandleDescOrig.equals(bootstrapMethodHandleDesc)) {  // if we are changing
                            bootstrapMethodHandle = new Handle(
                                    bootstrapMethodHandle.getTag(),
                                    bootstrapMethodHandleOwner,
                                    bootstrapMethodHandle.getName(),                                     
                                    bootstrapMethodHandleDesc,
                                    bootstrapMethodHandle.isInterface());
                        }
                        /** bootstrapMethodArguments can be Integer, Float,Long, Double,String, Type, Handle, ConstantDynamic value. 
                         * ConstantDynamic can modify content of array at runtime.
                         * 
                         * TODO: https://github.com/wildfly-extras/batavia/issues/28 support for handling bootstrapMethodArguments that are ARRAY, OBJECT, METHOD  
                         */
                        Object[] copyBootstrapMethodArguments = null;
                        for(int looper = 0; looper < bootstrapMethodArguments.length; looper++) {
                            Object argument = bootstrapMethodArguments[looper];
                            if (argument instanceof Type && ((
                                    ((Type)argument)).getSort() == Type.ARRAY ||
                                    ((Type)argument).getSort() == Type.OBJECT ||
                                    ((Type)argument).getSort() == Type.METHOD)) {
                                Type type = (Type) argument;
                                String oldDesc = type.getDescriptor();
                                String updatedDesc = replaceJavaXwithJakarta(type.getDescriptor());
                                if (type.getSort() == Type.ARRAY) {
                                    //    replace descriptor (if necessary)
                                    //    inspect and potentially replace all elements of this array type (see Type.getElementType())
                                    //    inspect and potentially replace its internal name // see Type.getInternalName()
                                    Type elementType = type.getElementType();
                                    String internalName = type.getInternalName();
                                    System.out.println("TODO: https://github.com/wildfly-extras/batavia/issues/28 for Type.ARRAY " + internalName + " elementType = " + elementType);
                                } else if(type.getSort() == Type.METHOD) {
                                    // replace descriptor (if necessary)
                                    // inspect and potentially replace all arguments of this method type (see Type.getArgumentTypes())
                                    // inspect and potentially replace its return type (see Type.getReturnType())
                                    // ElytronDefinition.class - type.getSort() == Type.METHOD type.getArgumentTypes() = [Lorg.objectweb.asm.Type;@72ea2f77
                                    System.out.println("TODO: https://github.com/wildfly-extras/batavia/issues/28 for Type.METHOD " + type.getDescriptor()  + " updatedDesc=" + updatedDesc);
                                    for (Type argTypes : type.getArgumentTypes()) {
                                        System.out.println("argumentTypes: " +
                                                " argTypes.getInternalName() = " + argTypes.getInternalName() +
                                                " argTypes.getDescriptor() = " + argTypes.getDescriptor());
                                        System.out.println("argTypes to string = " + argTypes.toString());
                                    }
                                } else { // (type.getSort() == Type.OBJECT)
                                    // replace descriptor (if necessary)
                                    // inspect and potentially replace its internal name // see Type.getInternalName()
                                    // inspect and potentially replace its name // see Type.getClassName()
                                    System.out.println("TODO: https://github.com/wildfly-extras/batavia/issues/28 for Type.OBJECT " + type.getInternalName() 
                                            + " type.getClassName() = " + type.getClassName());
                                }
                                
                                if (!oldDesc.equals(updatedDesc)) {
                                    setClassTransformed(true);
                                    if (copyBootstrapMethodArguments == null) {
                                        copyBootstrapMethodArguments = cloneBootstrapMethodArguments(bootstrapMethodArguments);
                                    }
                                    copyBootstrapMethodArguments[looper] = Type.getMethodType(updatedDesc);
                                    System.out.println("Updated type descriptor for BootstrapMethodArguments[" + looper + "] = " 
                                            + ((Type)copyBootstrapMethodArguments[looper]).getDescriptor());
                                }
                                
                            } else if (argument instanceof Handle) {  // reference to a field or method
                                Handle handle = (Handle)argument;
                                String origDesc = handle.getDesc();
                                String updatedDesc = replaceJavaXwithJakarta(handle.getDesc());
                                String origOwner = handle.getOwner();
                                String updatedOwner = replaceJavaXwithJakarta(handle.getOwner()); 
                                if (!origDesc.equals(updatedDesc) || !origOwner.equals(updatedOwner)) {  // if we are changing
                                    handle = new Handle(
                                            handle.getTag(),
                                            updatedOwner,
                                            handle.getName(),
                                            updatedDesc,
                                            handle.isInterface());
                                    if (copyBootstrapMethodArguments == null) {
                                        copyBootstrapMethodArguments = cloneBootstrapMethodArguments(bootstrapMethodArguments);
                                    }
                                    copyBootstrapMethodArguments[looper] = handle;
                                }
                                
                            } else if( argument instanceof ConstantDynamic) {
                                // TODO: runtime handling of ConstantDynamic)
                                ConstantDynamic constantDynamic = (ConstantDynamic)argument;
                                Thread.dumpStack();  // TODO: remove this dumpStack as soon as we identify that its happening
                                throw new IllegalStateException("ConstantDynamic is not handled " +constantDynamic.toString());
                            }
                        }
                        if (copyBootstrapMethodArguments != null) {
                            bootstrapMethodArguments = copyBootstrapMethodArguments;
                        }
                        super.visitInvokeDynamicInsn(name, desc, bootstrapMethodHandle, bootstrapMethodArguments);
                    }

                    private Object[] cloneBootstrapMethodArguments(Object[] bootstrapMethodArguments) {
                        // copy all current values after allocation
                        Object [] copyBootstrapMethodArguments = new Object[bootstrapMethodArguments.length];
                        for (int copyIdx = 0; copyIdx < bootstrapMethodArguments.length;copyIdx++) {
                            copyBootstrapMethodArguments[copyIdx] = bootstrapMethodArguments[copyIdx];
                        }
                        return copyBootstrapMethodArguments;
                    }

                    @Override
                    public void visitLdcInsn(final Object value) {
                        if (value instanceof Type) {
                            Type type = (Type) value;
                            String descOrig = type.getDescriptor();
                            String desc = replaceJavaXwithJakarta(descOrig);
                            if (!descOrig.equals(desc)) { // if we are changing
                                mv.visitLdcInsn(Type.getType(desc));
                                return;
                            }
                        }

                        if (value instanceof String) {
                            final String typeOrig = (String) value;
                            String replacement = replaceJavaXwithJakarta((String) value);
                            if (!typeOrig.equals(replacement)) {  // if we are changing
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

                        mv.visitLocalVariable(name, replaceJavaXwithJakarta(descriptor), replaceJavaXwithJakarta(signature), start, end, index);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        mv.visitFieldInsn(opcode, replaceJavaXwithJakarta(owner), name, replaceJavaXwithJakarta(desc));
                    }

                    @Override
                    public void visitTypeInsn(final int opcode, final String type) {
                        mv.visitTypeInsn(opcode, replaceJavaXwithJakarta(type));
                    }

                };
            }
        }, 0);
        if (!transformationsMade()) {
            // no change was made, indicate so by returning EMPTY_ARRAY 
            return EMPTY_ARRAY;
        }
        return generatedExtraClass[0] == null ?
                new Resource[]{new Resource(newResourceName, classWriter.toByteArray())} :
                new Resource[]{new Resource(newResourceName, classWriter.toByteArray()),
                        new Resource((String) generatedExtraClass[1], (byte[]) generatedExtraClass[0])};
        
    }

    private String replaceJavaXwithJakarta(String desc) {
        if ( desc == null) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder(desc);
        for (Map.Entry<String, String> possibleReplacement: mappingWithSeps.entrySet()) {
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
        for (Map.Entry<String, String> possibleReplacement: mappingWithDots.entrySet()) {
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
        String result = stringBuilder.toString();
        if (!result.equals(desc)) {  // if we are changing
            // mark the class as transformed
            setClassTransformed(true);
        }
        return result;
    }

    public void setClassTransformed(boolean classTransformed) {
        this.classTransformed = classTransformed;
    }

    public boolean transformationsMade() {
        return classTransformed;
    }

    public void clearTransformationState() {
        classTransformed = false;
    }

    @Override
    public Resource[] transform(final Resource r) {
        Resource retVal = null;
        String oldResourceName = r.getName();
        String newResourceName = replacePackageName(oldResourceName, false);
        if (oldResourceName.endsWith(CLASS_SUFFIX)) {
            clearTransformationState(); // clear transformation state for each class, prior to class transformation
                    
            if (!newResourceName.equals(oldResourceName)) {  // any file rename counts as a transformation 
                setClassTransformed(true);
                setNewClassName(newResourceName);
            }
                    
            return transform(newResourceName, r.getData());
        } else if (oldResourceName.endsWith(XML_SUFFIX)) {
            retVal = new Resource(newResourceName, textFile(r.getData()));
        } else if (oldResourceName.endsWith(TLD_SUFFIX)) {
            retVal = new Resource(newResourceName, textFile(r.getData()));
        } else if (oldResourceName.endsWith(JSP_SUFFIX)) {
            retVal = new Resource(newResourceName, textFile(r.getData()));
        } else if (oldResourceName.startsWith(META_INF_SERVICES_PREFIX)) {
            newResourceName = replacePackageName(oldResourceName, true);
            if (!newResourceName.equals(oldResourceName)) {
                retVal = new Resource(newResourceName, r.getData());
            }
        } else if (!newResourceName.equals(oldResourceName)) {
            retVal = new Resource(newResourceName, r.getData());
        }
        return retVal == null ? EMPTY_ARRAY : new Resource[] {retVal};
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

    private static byte[] textFile(final byte[] data) {
        try {
            return new String(data, "UTF-8").replace("javax.", "jakarta.").getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return null; // should never happen
        }
    }

    private class MyAnnotationVisitor extends AnnotationVisitor {
        public MyAnnotationVisitor(AnnotationVisitor av) {
            super(Opcodes.ASM7, av);
        }

        @Override
        public void visit(String name, Object value) {
            av.visit(name, value);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            av.visitEnum(name, replaceJavaXwithJakarta(descriptor), value);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            AnnotationVisitor av2 = av.visitAnnotation(name, replaceJavaXwithJakarta(descriptor));
            return new MyAnnotationVisitor(av2);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            AnnotationVisitor av2 = av.visitArray(name);
            return new MyAnnotationVisitor(av2);
        }

    }
}
