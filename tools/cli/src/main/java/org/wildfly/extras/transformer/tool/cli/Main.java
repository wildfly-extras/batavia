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
package org.wildfly.extras.transformer.tool.cli;

import org.wildfly.extras.transformer.TransformerBuilder;
import org.wildfly.extras.transformer.ArchiveTransformer;
import org.wildfly.extras.transformer.Config;
import org.wildfly.extras.transformer.TransformerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Command line tool for transforming class files or jar files.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 * @author Scott Marlow 
 */
public final class Main {

    private static final String PACKAGES_MAPPING_OPTION = "--packages-mapping=";
    private static final String PER_CLASS_MAPPING_OPTION = "--per-class-mapping=";
    private static final String TEXT_FILES_MAPPING_OPTION = "--text-files-mapping=";

    public static void main(final String... args) throws IOException {
        if (!validParameters(args)) {
            printUsage();
            System.exit(1);
        }

        final TransformerBuilder builder = TransformerFactory.getInstance().newTransformer();
        if (args.length > 2) {
            for (int i = 0; i < args.length - 2; i++) {
                if (args[i].startsWith(PACKAGES_MAPPING_OPTION)) {
                    builder.setConfiguration(Config.PACKAGES_MAPPING, args[i].substring(PACKAGES_MAPPING_OPTION.length()));
                } else if (args[i].startsWith(PER_CLASS_MAPPING_OPTION)) {
                    builder.setConfiguration(Config.PER_CLASS_MAPPING, args[i].substring(PER_CLASS_MAPPING_OPTION.length()));
                } else if (args[i].startsWith(TEXT_FILES_MAPPING_OPTION)) {
                    builder.setConfiguration(Config.TEXT_FILES_MAPPING, args[i].substring(TEXT_FILES_MAPPING_OPTION.length()));
                }
            }
        }
        final ArchiveTransformer archiveTransformer = builder.build();
        final File sourceArchive = new File(args[args.length - 2]);
        final File targetArchive = new File(args[args.length - 1]);
        final boolean transformed = archiveTransformer.transform(sourceArchive, targetArchive);
        if (transformed) {
            System.out.println("Archive " + args[args.length - 2] + " was transformed to " + args[args.length - 1] + " according to given transformation rules.");
        } else {
            System.out.println("Archive " + args[args.length - 2] + " was copied to " + args[args.length - 1] + ". No transformation rule was applicable.");
        }
    }

    private static boolean validParameters(final String... args) {
        if (args == null || args.length < 2) {
            System.err.println("At least 2 arguments are required");
            return false;
        }
        if (args.length > 5) {
            System.err.println("Maximum 5 arguments can be specified");
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
        if (args.length > 2) {
            boolean packagesMappingDefined = false;
            boolean perClassMappingDefined = false;
            boolean textFilesMappingDefined = false;
            for (int i = 0; i < args.length - 2; i++) {
                if (args[i].startsWith(PACKAGES_MAPPING_OPTION)) {
                    if (packagesMappingDefined) {
                        System.err.println(PACKAGES_MAPPING_OPTION + " can be specified only once");
                        return false;
                    }
                    packagesMappingDefined = true;
                    continue;
                }
                if (args[i].startsWith(PER_CLASS_MAPPING_OPTION)) {
                    if (perClassMappingDefined) {
                        System.err.println(PER_CLASS_MAPPING_OPTION + " can be specified only once");
                        return false;
                    }
                    perClassMappingDefined = true;
                    continue;
                }
                if (args[i].startsWith(TEXT_FILES_MAPPING_OPTION)) {
                    if (textFilesMappingDefined) {
                        System.err.println(TEXT_FILES_MAPPING_OPTION + " can be specified only once");
                        return false;
                    }
                    textFilesMappingDefined = true;
                    continue;
                }
                System.err.println("Unknown option: " + args[i]);
                return false;
            }
        }
        final File sourceFile = new File(args[args.length - 2]);
        if (!sourceFile.exists()) {
            System.err.println("Source archive doesn't exist: " + sourceFile.getAbsolutePath());
            return false;
        }
        final File targetFile = new File(args[args.length - 1]);
        if (targetFile.exists()) {
            System.err.println("Target archive exists: " + targetFile.getAbsolutePath());
            return false;
        }
        return true;
    }

    private static void printUsage() {
        System.err.println();
        System.err.println("Usage: " + Main.class.getName() + " [options] source.archive target.archive");
        System.err.println("");
        System.err.println("Where options include:");
        System.err.println("   " + PACKAGES_MAPPING_OPTION + "<config>");
        System.err.println("              If this parameter is not specified on the command line");
        System.err.println("              default packages mapping configuration will be used");
        System.err.println("   " + PER_CLASS_MAPPING_OPTION + "<config>");
        System.err.println("              If this parameter is not specified on the command line");
        System.err.println("              default per class mapping configuration will be used");
        System.err.println("   " + TEXT_FILES_MAPPING_OPTION + "<config>");
        System.err.println("              If this parameter is not specified on the command line");
        System.err.println("              default text files mapping configuration will be used");
        System.err.println("");
        System.err.println("Notes:");
        System.err.println(" * source.archive must exist");
        System.err.println(" * target.archive cannot exist");
    }

}
