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
package org.wildfly.transformer.tool.shared;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.wildfly.transformer.Transformer;
import org.wildfly.transformer.Transformer.Resource;
import org.wildfly.transformer.TransformerFactory;

/**
 * Command line tool for transforming class files or jar files.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 * @author Scott Marlow
 */
public abstract class Common {

    protected static final String CLASS_FILE_EXT = ".class";
    protected static final String JAR_FILE_EXT = ".jar";

    protected static void transformClassFile(final File inClassFile, final File outClassFile) throws IOException {
        if (inClassFile.length() > Integer.MAX_VALUE) {
            throw new UnsupportedOperationException("File " + inClassFile.getAbsolutePath() + " too big! Maximum allowed file size is " + Integer.MAX_VALUE + " bytes");
        }

        final Transformer t = TransformerFactory.getInstance().newTransformer().build();
        byte[] clazz = new byte[(int)inClassFile.length()];
        readBytes(new FileInputStream(inClassFile), clazz, true);
        final Resource newResource = t.transform(new Resource(inClassFile.getName(), clazz));
        clazz = newResource != null ? newResource.getData() : clazz;
        writeBytes(new FileOutputStream(outClassFile), clazz, true);
    }

    protected static void safeClose(final Closeable c) {
        try {
            if (c != null) c.close();
        } catch (final Throwable t) {
            // ignored
        }
    }

    protected static void readBytes(final InputStream is, final byte[] clazz, final boolean closeStream) throws IOException {
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

    protected static void writeBytes(final OutputStream os, final byte[] clazz, final boolean closeStream) throws IOException {
        try {
            os.write(clazz);
        } finally {
            if (closeStream) {
                safeClose(os);
            }
        }
    }

    protected static void transformJarFile(final File inJarFile, final File outJarFile) throws IOException {
        final Transformer t = TransformerFactory.getInstance().newTransformer().build();
        final Calendar calendar = Calendar.getInstance();
        JarFile jar = null;
        JarOutputStream jarOutputStream = null;
        JarEntry inJarEntry, outJarEntry;
        byte[] buffer;
        Resource oldResource, newResource;

        try {
            jar = new JarFile(inJarFile);
            jarOutputStream = new JarOutputStream(new FileOutputStream(outJarFile));

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
                newResource = t.transform(oldResource);
                if (newResource == null) {
                    newResource = oldResource;
                }
                // writing potentially modified jar file entry
                outJarEntry = new JarEntry(newResource.getName());
                outJarEntry.setSize(newResource.getData().length);
                outJarEntry.setTime(calendar.getTimeInMillis());
                jarOutputStream.putNextEntry(outJarEntry);
                writeBytes(jarOutputStream, newResource.getData(), false);
                jarOutputStream.closeEntry();
            }
        } finally {
            safeClose(jar);
            safeClose(jarOutputStream);
        }
    }
}
