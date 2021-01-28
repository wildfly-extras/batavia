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
package org.wildfly.extras.transformer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Archive transformer tries to convert given archives to another archives by applying configured transformation rules.
 * Cannot be used concurrently by multiple threads as instances of this class are not thread safe.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class ArchiveTransformer {

    protected final File configsDir;
    protected final boolean verbose;

    protected ArchiveTransformer(final File configsDir, final boolean verbose) {
        this.configsDir = configsDir;
        this.verbose = verbose;
    }

    /**
     * Attempts to apply configured transformations to given <code>source</code> archive and produces new
     * <code>target</code> archive.
     *
     * @param inJarFile archive file to be consumed (can be exploded)
     * @param outJarFile archive file to be produced (will be exploded if source was exploded)
     * @return <code>true</code> if transformations were applied to archive, <code>false</code> if source and target
     * contents are identical.
     * @throws IOException if some I/O error occurs
     */
    public boolean transform(final File inJarFile, final File outJarFile) throws IOException {
        boolean transformed = false;
        final File dir = outJarFile.getParentFile();
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Couldn't create directory: " + dir.getAbsolutePath());
            }
        }
        if (outJarFile.exists()) {
            if (!outJarFile.delete()) {
                throw new IOException("Couldn't delete file: " + outJarFile.getAbsolutePath());
            }
        }
        if (!outJarFile.createNewFile()) {
            throw new IOException("Couldn't create file: " + outJarFile.getAbsolutePath());
        }
        final ResourceTransformer t = newResourceTransformer();
        final Calendar calendar = Calendar.getInstance();
        JarEntry inJarEntry, outJarEntry;
        byte[] buffer;
        ResourceTransformer.Resource oldResource;
        ResourceTransformer.Resource[] newResources;

        try (JarFile jar = new JarFile(inJarFile);
                JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(outJarFile));) {
            for (final Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
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

                oldResource = new ResourceTransformer.Resource(inJarEntry.getName(), buffer);
                // transform resource
                newResources = t.transform(oldResource);
                if (newResources.length == 0) {
                    newResources = new ResourceTransformer.Resource[]{oldResource};
                } else {
                    transformed = true;
                }
                // writing potentially modified jar file entries
                for (ResourceTransformer.Resource newResource : newResources) {
                    outJarEntry = new JarEntry(newResource.getName());
                    outJarEntry.setSize(newResource.getData().length);
                    outJarEntry.setTime(calendar.getTimeInMillis());
                    jarOutputStream.putNextEntry(outJarEntry);
                    writeBytes(jarOutputStream, newResource.getData());
                    jarOutputStream.closeEntry();
                }
            }
        }
        return transformed;
    }

    // TODO: javadoc
    protected ResourceTransformer newResourceTransformer() throws IOException {
        throw new UnsupportedOperationException();
    }

    private static void readBytes(final InputStream is, final byte[] clazz) throws IOException {
        int offset = 0;
        while (offset < clazz.length) {
            offset += is.read(clazz, offset, clazz.length - offset);
        }
    }

    private static void writeBytes(final OutputStream os, final byte[] clazz) throws IOException {
        os.write(clazz);
    }

    public boolean canTransformIndividualClassFile() {
        return false;
    }
}
