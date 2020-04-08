package org.wildfly.transformer;

import java.io.File;
import java.io.IOException;

import org.wildfly.transformer.tool.shared.Common;

/**
 * HandleTransformation
 *
 * @author Scott Marlow
 */
public class HandleTransformation extends Common {

    /**
     * Transform the files contained under the folder path specified.
     * 
     * @param folder represents a filesystem path that contains files/subfolders to be transformed.
     */
    public static void transformDirectory(File folder) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for(File sourceFile: files) {
            
            if (sourceFile.isDirectory()) {
                transformDirectory(sourceFile);
            }
            else if (sourceFile.getName().endsWith(CLASS_FILE_EXT)) {
                transformClassFile(sourceFile, sourceFile);
            } else if (sourceFile.getName().endsWith(JAR_FILE_EXT)) {
                transformJarFile(sourceFile, sourceFile);
            }
        }
    }

    public static void transformFile(File sourceFile, File targetFile) throws IOException {
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
            transformClassFile(sourceFile, targetFile);
        } else if (sourceFile.getName().endsWith(JAR_FILE_EXT)) {
            transformJarFile(sourceFile, targetFile);
        }
    }
}
