/*
 * Copyright 2023 Red Hat, Inc, and individual contributors.
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

package org.wildfly.extras.transformer.findependencies.archivefile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.wildfly.extras.transformer.findependencies.ClassCollector;
import org.wildfly.extras.transformer.findependencies.Filter;

/**
 * Reader
 *
 * @author Scott Marlow
 */

public abstract class Reader {

    private static final String CLASS_SUFFIX = ".class";


    private void readBytes(final InputStream is, final byte[] clazz) throws IOException {
        int offset = 0;
        while (offset < clazz.length) {
            offset += is.read(clazz, offset, clazz.length - offset);
        }
    }

    /**
     * Attempts to scan through given <code>source</code> archive.
     *
     * @param inJarFile archive file to be consumed (can be exploded)
     * @throws IOException if some I/O error occurs
     */
    public void scan(final File inJarFile) throws IOException {
        JarEntry inJarEntry;
        byte[] buffer;

        if (inJarFile.getName().endsWith(CLASS_SUFFIX)) {
            if (inJarFile.length() < 0) {
                throw new UnsupportedOperationException("File size " + inJarFile.getName() + " unknown! File size must be positive number");
            }
            if (inJarFile.length() > Integer.MAX_VALUE) {
                throw new UnsupportedOperationException("File " + inJarFile.getName() + " too big! Maximum allowed file size is " + Integer.MAX_VALUE + " bytes");
            }
            buffer = new byte[(int) inJarFile.length()];
            try (InputStream in = new FileInputStream(inJarFile)) {
                readBytes(in, buffer);
            }
            collect(buffer, inJarFile.getName());
            return;
        }

        JarFile jar = new JarFile(inJarFile);
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
            try (InputStream in = jar.getInputStream(inJarEntry)) {
                readBytes(in, buffer);
            }

            if (inJarEntry.getName().endsWith(".jar") || inJarEntry.getName().endsWith(".war")) {
                File baseDir = new File(inJarFile.getParentFile().getAbsolutePath());
                String jarName = inJarEntry.getName();
                File libFile = new File(baseDir, jarName);
                libFile.getParentFile().mkdirs();
                if (!libFile.exists()) {
                    // TODO: check what is in the buffer for the .jar or .war file (does it contain the entire archive?)
                    try (FileOutputStream libFileOS = new FileOutputStream(libFile)) {
                        libFileOS.write(buffer);
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                }
                scan(libFile); // recurse to handle written to disk *.jar + *.war files.
            } else {
                if (inJarEntry.getName().endsWith(CLASS_SUFFIX)) {
                    collect(buffer, inJarEntry.getName());
                } else {
                    System.out.println("Reader#scan is ignoring " + inJarEntry.getName());
                }
            }
        }
    }

    public abstract void collect(final byte[] clazz, final String newResourceName);
}
