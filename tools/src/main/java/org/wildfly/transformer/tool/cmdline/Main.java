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
package org.wildfly.transformer.tool.cmdline;

import java.io.File;
import java.io.IOException;

import org.wildfly.transformer.tool.shared.Common;

/**
 * Command line tool for transforming class files or jar files.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 * @author Scott Marlow 
 */
public final class Main extends Common {

    private static final String PACKAGES_MAPPING_OPTION = "--packages-mapping=";

    public static void main(final String... args) throws IOException {
        if (!validParameters(args)) {
            printUsage();
            System.exit(1);
        }

        final String packagesMappingFile = args.length == 3 ? args[0].substring(PACKAGES_MAPPING_OPTION.length()) : null;
        final File sourceFile = new File(args.length == 3 ? args[1] : args[0]);
        final File targetFile = new File(args.length == 3 ? args[2] : args[1]);
        if (sourceFile.getName().endsWith(CLASS_FILE_EXT)) {
            transformClassFile(sourceFile, targetFile, packagesMappingFile);
        } else if (sourceFile.getName().endsWith(JAR_FILE_EXT)) {
            transformJarFile(sourceFile, targetFile, packagesMappingFile);
        }
    }

    private static boolean validParameters(final String... args) {
        if (args == null || args.length < 2 || args.length > 3) {
            System.err.println("At least 2 arguments are required");
            return false;
        }
        for (String arg : args) {
            if (arg == null) {
                System.err.println("Argument cannot be null");
                return false;
            }
            if ("".equals(arg)) {
                System.err.println("Argument cannot be empty string");
                return false;
            }
        }
        if (args.length == 3) {
            if (!args[0].startsWith(PACKAGES_MAPPING_OPTION)) {
                System.err.println("Unknown option: " + args[0]);
                return false;
            }
        }
        final File sourceFile = new File(args.length == 2 ? args[0] : args[1]);
        if (!sourceFile.getName().endsWith(CLASS_FILE_EXT) && !sourceFile.getName().endsWith(JAR_FILE_EXT)) {
            System.err.println("Supported file extensions are " + CLASS_FILE_EXT + " or " + JAR_FILE_EXT + " : " + sourceFile.getAbsolutePath());
            return false;
        }
        if (!sourceFile.exists()) {
            System.err.println("Couldn't find file " + sourceFile.getAbsolutePath());
            return false;
        }
        final File targetFile = new File(args.length == 2 ? args[1] : args[2]);
        if (targetFile.exists()) {
            System.err.println("Delete file or directory " + targetFile.getAbsolutePath());
            return false;
        }
        return true;
    }

    private static void printUsage() {
        System.err.println();
        System.err.println("Usage: " + Main.class.getName() + " [-option] source.class target.class");
        System.err.println("       (to transform a class)");
        System.err.println("   or  " + Main.class.getName() + " [-option] source.jar target.jar");
        System.err.println("       (to transform a jar file)");
        System.err.println("");
        System.err.println("Where options include:");
        System.err.println("   " + PACKAGES_MAPPING_OPTION + "<config>");
        System.err.println("              If this parameter is not specified on the command line");
        System.err.println("              default packages mapping configuration will be used");
        System.err.println("");
        System.err.println("Notes:");
        System.err.println(" * source.class or source.jar must exist");
        System.err.println(" * target.class or target.jar cannot exist");
    }

}
