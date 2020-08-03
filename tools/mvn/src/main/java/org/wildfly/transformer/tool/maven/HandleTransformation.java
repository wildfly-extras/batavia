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
package org.wildfly.transformer.tool.maven;

import org.wildfly.transformer.ArchiveTransformer;
import org.wildfly.transformer.Config;
import org.wildfly.transformer.TransformerBuilder;
import org.wildfly.transformer.TransformerFactory;

import java.io.File;
import java.io.IOException;

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
    static void transformDirectory(final File folder, final String packagesMappingFile) throws IOException {
        final File[] files = folder.listFiles();
        if (files == null) return;

        for (File sourceFile : files) {
            if (sourceFile.isDirectory()) {
                transformDirectory(sourceFile, packagesMappingFile);
            } else if (sourceFile.getName().endsWith(JAR_FILE_EXT)) {
                transformFile(sourceFile, new File(sourceFile.getParentFile(), sourceFile.getName() + ".transformed"), packagesMappingFile);
            }
        }
    }

    static void transformFile(final File sourceFile, final File targetFile, final String packagesMappingFile) throws IOException {
        if (!sourceFile.exists()) {
            throw new IllegalArgumentException("input file " + sourceFile.getName() + " does not exist");
        }
        if (!sourceFile.getName().endsWith(JAR_FILE_EXT)) {
            throw new IllegalArgumentException("Supported file extensions are " + JAR_FILE_EXT + " : " + sourceFile.getAbsolutePath());
        }
        if (!sourceFile.exists()) {
            throw new IllegalArgumentException("Couldn't find file " + sourceFile.getAbsolutePath());
        }
        if (sourceFile.getName().endsWith(JAR_FILE_EXT)) {
            TransformerBuilder builder = TransformerFactory.getInstance().newTransformer();
            builder.setConfiguration(Config.PACKAGES_MAPPING, packagesMappingFile);
            ArchiveTransformer transformer = builder.build();
            transformer.transform(sourceFile, targetFile);
        }
    }

}
