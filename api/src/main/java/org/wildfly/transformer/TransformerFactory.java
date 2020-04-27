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
package org.wildfly.transformer;

import static java.util.ServiceLoader.load;

import java.io.IOException;
import java.util.Iterator;

/**
 * Resources transformer factory.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class TransformerFactory {

    private static final TransformerFactory INSTANCE;

    static {
         final Iterator<TransformerFactory> i = load(TransformerFactory.class, TransformerFactory.class.getClassLoader()).iterator();
         TransformerFactory factoryImpl = null;
         boolean factoryImplAvailable = false;
         while (i.hasNext()) {
             factoryImpl = i.next();
             if (factoryImpl != null) {
                 factoryImplAvailable = true;
                 break;
             }
         }
         if (factoryImplAvailable) {
             INSTANCE = factoryImpl;
         } else {
             throw new IllegalStateException("Service provider for " + TransformerFactory.class.getName() + " not found");
         }
    }

    /**
     * Gets factory instance.
     *
     * @return factory instance
     */
    public static TransformerFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Creates new transformer builder instance.
     *
     * @return new transformer builder instance
     */
    public abstract TransformerBuilder newTransformer();

}
