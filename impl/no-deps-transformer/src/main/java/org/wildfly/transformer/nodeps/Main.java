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
package org.wildfly.transformer.nodeps;

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

/**
 * Command line tool for transforming class files or jar files.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
public final class Main {

    private static final String CLASS_FILE_EXT = ".class";
    private static final String JAR_FILE_EXT = ".jar";

    public static void main(final String... args) throws IOException {
        if (!validParameters(args)) {
            printUsage();
            System.exit(1);
        }

        final File sourceFile = new File(args[0]);
        final File targetFile = new File(args[1]);
        if (sourceFile.getName().endsWith(CLASS_FILE_EXT)) {
            transformClassFile(sourceFile, targetFile);
        } else if (sourceFile.getName().endsWith(JAR_FILE_EXT)) {
            transformJarFile(sourceFile, targetFile);
        }
    }

    private static boolean validParameters(final String... args) {
        if (args.length != 2) {
            System.err.println("2 arguments required");
            return false;
        }
        if (args[0] == null || args[1] == null) {
            System.err.println("Argument cannot be null");
            return false;
        }
        if ("".equals(args[0]) || "".equals(args[1])) {
            System.err.println("Argument cannot be empty string");
            return false;
        }
        final File sourceFile = new File(args[0]);
        if (!sourceFile.getName().endsWith(CLASS_FILE_EXT) && !sourceFile.getName().endsWith(JAR_FILE_EXT)) {
            System.err.println("Supported file extensions are " + CLASS_FILE_EXT + " or " + JAR_FILE_EXT + " : " + sourceFile.getAbsolutePath());
            return false;
        }
        if (!sourceFile.exists()) {
            System.err.println("Couldn't find file " + sourceFile.getAbsolutePath());
            return false;
        }
        final File targetFile = new File(args[1]);
        if (targetFile.exists()) {
            System.err.println("Delete file or directory " + targetFile.getAbsolutePath());
            return false;
        }
        return true;
    }

    private static void transformClassFile(final File inClassFile, final File outClassFile) throws IOException {
        if (inClassFile.length() > Integer.MAX_VALUE) {
            throw new UnsupportedOperationException("File " + inClassFile.getAbsolutePath() + " too big! Maximum allowed file size is " + Integer.MAX_VALUE + " bytes");
        }

        final Transformer t = TransformerFactoryImpl.getInstance().newTransformer();
        byte[] clazz = new byte[(int)inClassFile.length()];
        readBytes(new FileInputStream(inClassFile), clazz, true);
        clazz = t.transform(clazz);
        writeBytes(new FileOutputStream(outClassFile), clazz, true);
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

    private static void transformJarFile(final File inJarFile, final File outJarFile) throws IOException {
        final Transformer t = TransformerFactoryImpl.getInstance().newTransformer();
        final Calendar calendar = Calendar.getInstance();
        JarFile jar = null;
        JarOutputStream jarOutputStream = null;
        JarEntry inJarEntry, outJarEntry;
        byte[] buffer, newBuffer = null;

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
                // transform byte code of class files
                if (inJarEntry.getName().endsWith(CLASS_FILE_EXT)) {
                    newBuffer = t.transform(buffer);
                    if (newBuffer == null) {
                        newBuffer = buffer;
                    }
                }
                // writing modified jar file entry
                outJarEntry = new JarEntry(inJarEntry.getName());
                outJarEntry.setSize(newBuffer.length);
                outJarEntry.setTime(calendar.getTimeInMillis());
                jarOutputStream.putNextEntry(outJarEntry);
                writeBytes(jarOutputStream, newBuffer, false);
                jarOutputStream.closeEntry();
            }
        } finally {
            safeClose(jar);
            safeClose(jarOutputStream);
        }
    }

    private static void printUsage() {
        System.err.println();
        System.err.println("Usage: " + Main.class.getName() + " source.class target.class");
        System.err.println("       (to transform a class)");
        System.err.println("   or  " + Main.class.getName() + " source.jar target.jar");
        System.err.println("       (to transform a jar file)");
        System.err.println("");
        System.err.println("Notes:");
        System.err.println(" * source.class or source.jar must exist");
        System.err.println(" * target.class or target.jar cannot exist");
    }

}
