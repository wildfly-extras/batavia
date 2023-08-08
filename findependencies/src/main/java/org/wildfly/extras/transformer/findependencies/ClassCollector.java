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
 * Collector
 *
 * @author Scott Marlow
 */
public class ClassCollector {

    private Filter match;
    public ClassCollector(Filter match) {
        this.match = match;
    }

    public ClassReference addClassName(String className) {
        if(match.matchClassPackage(className)) {
            ClassReference classReference = ClassReference.findClassName(className);
        }
        return null;
    }

    public ClassReference addMethod(String className, String method, String methodDescriptor) {
        if(match.matchClassPackage(className)) {
            ClassReference classReference = ClassReference.findClassName(className);
            classReference.addMethod(method, methodDescriptor);
            return classReference;
        }
        return null;
    }

    public ClassReference addField(String className, String field, String fieldDescriptor) {
        if(match.matchClassPackage(className)) {
            ClassReference classReference = ClassReference.findClassName(className);
            classReference.addField(field, fieldDescriptor);
            return classReference;
        }
        return null;
    }

}
