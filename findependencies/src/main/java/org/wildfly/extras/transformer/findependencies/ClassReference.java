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

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

/**
 * ClassReference instance refers to a Java Class definition.
 * Each
 *
 * @author Scott Marlow
 */
public class ClassReference {
    private static final HashMap<String, ClassReference> classes = new HashMap();

    private final String className;

    /**
     *  Contains all Methods referenced by Class.
     *  methods map lookup is via method name.  The contained HashMap is referenced by descriptor.
    **/
    private final HashMap<String,HashMap<String,MethodReference>> methods = new HashMap<>();

    /**
     *  Contains all Fields referenced by Class.
     *  methods map lookup is via method name.  The contained HashMap is referenced by descriptor.
    **/
    private final HashMap<String,HashMap<String,FieldReference>> fields = new HashMap<>();

    private ClassReference(String className) {
        this.className=className;
    }

    /**
     * Clear all Class references
     */

    public static ClassReference findClassName(String className) {
        ClassReference classReference = classes.get(className);
        if (classReference == null) {
            classReference = new ClassReference(className);
            classes.put(className, classReference);
        }
        return classReference;
    }

    public static Set<String> getClassNames() {
        return Collections.unmodifiableSet(classes.keySet()) ;
    }

    /**
     *
     * @return class name referenced
     */
    public String toString() {
        return className;
    }

    /**
     * @return list of method names referenced
     */
    public static Set<String> getMethodsReferenced(String className) {
        ClassReference classReference = classes.get(className);
        return classReference.methods.keySet();
    }

    /**
     * Add method
     *
     * @param method name of method referenced
     * @param descriptor the method descriptor as used in deployment bytecode
     */
    public void addMethod(String method, String descriptor) {
        MethodReference methodReference;
        HashMap<String,MethodReference> map = methods.get(method);
        if(map == null) {
            map = new HashMap<String,MethodReference>();
            methodReference = new MethodReference(method, descriptor);
            map.put(method, methodReference);
            methods.put(method, map);
        } else if((methodReference = map.get(descriptor)) == null) {
            methodReference = new MethodReference(method, descriptor);
            map.put(method, methodReference);
        }
        methodReference.addReference();
    }

    /**
     * Add field
     *
     * @param field name of field referenced
     * @param descriptor the field descriptor as used in deployment bytecode
     */
    public void addField(String field, String descriptor) {
        FieldReference  fieldReference;
        HashMap<String,FieldReference> map = fields.get(field);
        if(map == null) {
            map = new HashMap<String,FieldReference>();
            fieldReference = new FieldReference(field, descriptor);
            map.put(field, fieldReference);
            fields.put(field, map);
        } else if((fieldReference = map.get(descriptor)) == null) {
            fieldReference = new FieldReference(field, descriptor);
            map.put(field, fieldReference);
        }
        fieldReference.addReference();

    }

    public static void clear() {
            classes.clear();
        }

    static protected class MethodReference {

        private String name;
        private String descriptor;
        private int count=0;

        protected MethodReference(String name, String descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }

        public void addReference() {
            count++;
        }
    }

    static protected class FieldReference {
        private String name;
        private String descriptor;
        private int count=0;

        protected FieldReference(String name, String descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }

        public void addReference() {
            count++;
        }
    }
}
