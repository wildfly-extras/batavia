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
    public static void transformFolder(File folder) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for(File sourceFile: files) {
            
            if (sourceFile.isDirectory()) {
                transformFolder(sourceFile);
            }
            else if (sourceFile.getName().endsWith(CLASS_FILE_EXT)) {
                transformClassFile(sourceFile, sourceFile);
            } else if (sourceFile.getName().endsWith(JAR_FILE_EXT)) {
                transformJarFile(sourceFile, sourceFile);
            }
        }
    } 
    
    
}
