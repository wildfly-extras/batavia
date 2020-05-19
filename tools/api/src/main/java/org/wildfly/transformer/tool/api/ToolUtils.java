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
package org.wildfly.transformer.tool.api;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.wildfly.transformer.Transformer;
import org.wildfly.transformer.Transformer.Resource;
import org.wildfly.transformer.TransformerBuilder;
import org.wildfly.transformer.TransformerFactory;

/**
 * Command line tool for transforming class files or jar files.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 * @author Scott Marlow
 */
public final class ToolUtils {

    private ToolUtils() {
        // forbidden instantiation
    }

    public static final String CLASS_FILE_EXT = ".class";
    public static final String JAR_FILE_EXT = ".jar";

    public static void transformClassFile(final File inClassFile, final File targetDir, final String packagesMappingFile) throws IOException {
        if (inClassFile.length() > Integer.MAX_VALUE) {
            throw new UnsupportedOperationException("File " + inClassFile.getAbsolutePath() + " too big! Maximum allowed file size is " + Integer.MAX_VALUE + " bytes");
        }
        if (!targetDir.mkdirs()) throw new IOException("Couldn't create directory: " + targetDir.getAbsolutePath());
        final Transformer t = newTransformer(packagesMappingFile);
        byte[] clazz = new byte[(int)inClassFile.length()];
        readBytes(new FileInputStream(inClassFile), clazz, true);
        final Resource origResource = new Resource(inClassFile.getName(), clazz);
        final Resource[] newResources = t.transform(origResource);
        if (newResources.length == 0) {
            writeBytes(new FileOutputStream(new File(targetDir, origResource.getName())), origResource.getData(), true);
        } else {
            for (Resource newResource : newResources) {
                writeBytes(new FileOutputStream(new File(targetDir, newResource.getName())), newResource.getData(), true);
            }
        }
    }

    public static void transformJarFile(final File inJarFile, final File targetDir, final String packagesMappingFile) throws IOException {
        final Transformer t = newTransformer(packagesMappingFile);
        final Calendar calendar = Calendar.getInstance();
        JarFile jar = null;
        JarOutputStream jarOutputStream = null;
        JarEntry inJarEntry, outJarEntry;
        byte[] buffer;
        Resource oldResource;
        Resource[] newResources;

        try {
            jar = new JarFile(inJarFile);
            jarOutputStream = new JarOutputStream(new FileOutputStream(new File(targetDir, inJarFile.getName())));

            for (final Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements(); ) {
                // jar file entry preconditions
                inJarEntry = e.nextElement();
                if (inJarEntry.getSize() == 0) {
                    continue; // directories
                }
                if (inJarEntry.getSize() < 0) {
                    throw new UnsupportedOperationException("File size " + inJarEntry.getName() + " unknown! File size must be positive number");
                }
                if (inJarEntry.getSize() > Integer.MAX_VALUE) {
                    throw new UnsupportedOperationException("File " + inJarEntry.getName() + " too big! Maximum allowed file size is " + Integer.MAX_VALUE + " bytes");
                }
                // reading original jar file entry
                buffer = new byte[(int) inJarEntry.getSize()];
                readBytes(jar.getInputStream(inJarEntry), buffer, true);
                oldResource = new Resource(inJarEntry.getName(), buffer);
                // transform resource
                newResources = t.transform(oldResource);
                if (newResources.length == 0) {
                    newResources = new Resource[] {oldResource};
                }
                // writing potentially modified jar file entries
                for (Resource newResource : newResources) {
                    outJarEntry = new JarEntry(newResource.getName());
                    outJarEntry.setSize(newResource.getData().length);
                    outJarEntry.setTime(calendar.getTimeInMillis());
                    jarOutputStream.putNextEntry(outJarEntry);
                    writeBytes(jarOutputStream, newResource.getData(), false);
                    jarOutputStream.closeEntry();
                }
            }
        } finally {
            safeClose(jar);
            safeClose(jarOutputStream);
        }
    }

    /**
     * Transform JBoss module.xml and artifacts.
     *
     * @param modulesDir Modules root directory containing modules to transform.
     * @param modulesTargetDir Target modules root directory containing
     * transformed modules. This directory must exist.
     * @param modulesMappingFile Custom module names mapping. If null, default
     * mapping is applied.
     * @param transformArtifacts Provide true to transform discovered artifacts.
     * @param packagesMappingFile Custom java packages mapping. If null, default
     * mapping is applied.
     * @return A map of transformed modules.
     * @throws IOException
     */
    public static Map<String, TransformedModule> transformModules(final Path modulesDir, final Path modulesTargetDir,
            final String modulesMappingFile, final boolean transformArtifacts, final String packagesMappingFile) throws IOException {
        return JBossModulesTransformer.transform(modulesDir, modulesTargetDir, modulesMappingFile,
                transformArtifacts, packagesMappingFile);
    }

    private static void safeClose(final Closeable c) {
        try {
            if (c != null) c.close();
        } catch (final Throwable t) {
            // ignored
        }
    }

    private static void readBytes(final InputStream is, final byte[] clazz, final boolean closeStream) throws IOException {
        try {
            int offset = 0;
            while (offset < clazz.length) {
                offset += is.read(clazz, offset, clazz.length - offset);
            }
        } finally {
            if (closeStream) {
                safeClose(is);
            }
        }
    }

    private static void writeBytes(final OutputStream os, final byte[] clazz, final boolean closeStream) throws IOException {
        try {
            os.write(clazz);
        } finally {
            if (closeStream) {
                safeClose(os);
            }
        }
    }

    private static Transformer newTransformer(final String packagesMappingFile) throws IOException {
        final TransformerBuilder builder = TransformerFactory.getInstance().newTransformer();
        if (packagesMappingFile != null) {
            builder.setPackagesMapping(packagesMappingFile);
        }
        return builder.build();
    }

}
