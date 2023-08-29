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

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Main
 *
 * @author Scott Marlow
 */
public final class Main {


    public static void main(final String... args) throws IOException {
        System.out.println("find dependencies:" + args);
        Filter filter = null;
        for (int looper = 0 ; looper < args.length; looper++) {
            String arg = args[looper];
            if("-include".equals(arg) || "-i".equals(arg)) {
                System.out.println("args:" + arg + ":" + args[looper+1]);
                if (filter == null) {
                    filter = new Filter();
                }
                filter.include(args[++looper]);
            } else if("-file".equals(arg) || "-f".equals(arg)) {
                System.out.println("args:" + arg + ":" + args[looper+1]);
                final File inJarFile = new File(args[++looper]);
                ArchiveTransformerImpl jTrans = new ArchiveTransformerImpl(filter!=null?filter:Filter.defaultFilter());
                jTrans.transform(inJarFile);
                Set<String> classnames =  ClassReference.getClassNames();
                System.out.println("matches for input " + inJarFile.getName() + " = " + classnames + ": filter= " + filter);
                // clear for the next set of options
                filter = null;
            }
        }

    }

}

