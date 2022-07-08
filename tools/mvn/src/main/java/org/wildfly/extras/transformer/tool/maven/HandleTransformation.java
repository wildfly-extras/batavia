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
package org.wildfly.extras.transformer.tool.maven;

import org.wildfly.extras.transformer.TransformerBuilder;
import org.wildfly.extras.transformer.TransformerFactory;
import org.wildfly.extras.transformer.ArchiveTransformer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Set;

/**
 * HandleTransformation
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author Scott Marlow
 */
final class HandleTransformation {

    public static final String JAR_FILE_EXT = ".jar";

    /**
     * Transform the files contained under the folder path specified.
     *
     * @param folder represents a filesystem path that contains files/subfolders to be transformed.
     */
    static void transformDirectory(final File folder, final File targetFolder, final String configsDir, final boolean verbose, final boolean overwrite, boolean invert) throws IOException {
        transformDirectory(folder, targetFolder, configsDir, verbose, overwrite, invert, Collections.emptySet());
    }

    /**
     * Transform the files contained under the folder path specified.
     *
     * @param folder represents a filesystem path that contains files/subfolders to be transformed.
     */
    static void transformDirectory(final File folder, final File targetFolder, final String configsDir, final boolean verbose, final boolean overwrite, boolean invert, final
                                   Set<String> ignored) throws IOException {
        final File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File sourceFile : files) {
            File targetFile = new File(targetFolder, sourceFile.getName());

            if (sourceFile.isDirectory()) {
                transformDirectory(sourceFile, new File(targetFolder, sourceFile.getName()), configsDir, verbose, overwrite, invert, ignored);
            } else {
                if (targetFile.exists()) {
                    if (overwrite) {
                        targetFile.delete();
                        transformFile(sourceFile, new File(targetFolder, sourceFile.getName()), configsDir, verbose, invert, ignored);
                    }
                } else {
                    transformFile(sourceFile, new File(targetFolder, sourceFile.getName()), configsDir, verbose, invert, ignored);
                }
            }
        }
    }

    static void transformFile(final File sourceFile, final File targetFile, final String configsDir, final boolean verbose, final boolean invert) throws IOException {
        transformFile(sourceFile, targetFile, configsDir, verbose, invert, Collections.emptySet());
    }

    static void transformFile(final File sourceFile, final File targetFile, final String configsDir, final boolean verbose, final boolean invert, Set<String> ignored) throws IOException {
        if (!sourceFile.exists()) {
            throw new IllegalArgumentException("input file " + sourceFile.getName() + " does not exist");
        }
        // Check if this is an ignored file
        final String path = sourceFile.getPath();
        for (String ignore : ignored) {
            if (path.endsWith(ignore)) {
                return;
            }
        }
        TransformerBuilder builder = TransformerFactory.getInstance().newTransformer();
        if (configsDir != null) {
            builder.setConfigsDir(configsDir);
        }
        builder.setVerbose(verbose);
        builder.setInvert(invert);
        ArchiveTransformer transformer = builder.build();
        if (transformer.canTransformIndividualClassFile() || sourceFile.getName().endsWith(JAR_FILE_EXT)) {
            Files.createDirectories(targetFile.toPath().getParent());
            transformer.transform(sourceFile, targetFile);
        } else {
            throw new IllegalArgumentException("Supported file extensions are " + JAR_FILE_EXT + " : " + sourceFile.getAbsolutePath());
        }
    }

}
