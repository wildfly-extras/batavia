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
package org.wildfly.transformer.tools.maven;

import java.io.File;
import java.io.IOException;

import org.wildfly.transformer.tools.api.Common;

/**
 * HandleTransformation
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author Scott Marlow
 */
final class HandleTransformation extends Common {

    /**
     * Transform the files contained under the folder path specified.
     * 
     * @param folder represents a filesystem path that contains files/subfolders to be transformed.
     */
    static void transformDirectory(final File folder, final String packagesMappingFile) throws IOException {
        final File[] files = folder.listFiles();
        if (files == null) return;

        for (File sourceFile : files) {
            if (sourceFile.isDirectory()) {
                transformDirectory(sourceFile, packagesMappingFile);
            } else if (sourceFile.getName().endsWith(CLASS_FILE_EXT)) {
                transformClassFile(sourceFile, sourceFile, packagesMappingFile);
            } else if (sourceFile.getName().endsWith(JAR_FILE_EXT)) {
                transformJarFile(sourceFile, sourceFile, packagesMappingFile);
            }
        }
    }

    static void transformFile(final File sourceFile, final File targetFile, final String packagesMappingFile) throws IOException {
        if (!sourceFile.exists()) {
            throw new IllegalArgumentException("input file " + sourceFile.getName() + " does not exist");
        }
        if (!sourceFile.getName().endsWith(CLASS_FILE_EXT) && !sourceFile.getName().endsWith(JAR_FILE_EXT)) {
            throw new IllegalArgumentException("Supported file extensions are " + CLASS_FILE_EXT + " or " + JAR_FILE_EXT + " : " + sourceFile.getAbsolutePath());
        }
        if (!sourceFile.exists()) {
            throw new IllegalArgumentException("Couldn't find file " + sourceFile.getAbsolutePath());
        }

        if (sourceFile.getName().endsWith(CLASS_FILE_EXT)) {
            transformClassFile(sourceFile, targetFile, packagesMappingFile);
        } else if (sourceFile.getName().endsWith(JAR_FILE_EXT)) {
            transformJarFile(sourceFile, targetFile, packagesMappingFile);
        }
    }

}
