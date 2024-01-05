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

package org.wildfly.extras.transformer.findependencies;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.wildfly.extras.transformer.findependencies.classfileapi.ClassfileAPI;

/**
 * TestFindTestJar
 *
 * @author Scott Marlow
 */
public class TestFindTestJar {

    @AfterEach
    public void cleanup() {
        ClassReference.clear();
    }

    @Test
    public void testJarDownload() {
        File ejbjar = fromTargetFile("dist/com/sun/ts/tests/jpa/jpa22/repeatable/convert/jpa_jpa22_repeatable_converts_stateless3_vehicle_ejb.jar");
        assertNotNull(ejbjar);
    }

    @Test
    public void testClassfileAPI() throws Throwable {

        Main.main(new String[]{"-file",  "target/classes/org/wildfly/extras/transformer/findependencies/classfileapi/ClassfileAPI.class"});
        Set<String> classnames =  ClassReference.getClassNames();
        assertTrue(classnames.contains("java.lang.classfile.constantpool.ClassEntry"), "expect to find java.lang.classfile.constantpool.ClassEntry in " + classnames);
    }

    @Test
    public void testProcessJar() throws Throwable {
        File ejbjar = fromTargetFile("dist/com/sun/ts/tests/jpa/jpa22/repeatable/convert/jpa_jpa22_repeatable_converts_stateless3_vehicle_ejb.jar");
        assertNotNull(ejbjar);
        Main.main(new String[]{"-file", ejbjar.getPath()});
        // ArchiveTransformerImpl jTrans = new ArchiveTransformerImpl(Filter.defaultFilter());
        // jTrans.transform(ejbjar);
        Set<String> classnames =  ClassReference.getClassNames();
        assertTrue(classnames.contains("jakarta.persistence.EntityTransaction"), "expect to find jakarta.persistence.EntityTransaction in " + classnames);
    }

    @Test
    public void testEar() throws Throwable {
        File ear = fromTargetFile("dist/com/sun/ts/tests/jpa/jpa22/repeatable/convert/jpa_jpa22_repeatable_converts_vehicles.ear");
        assertNotNull(ear);
        ArchiveTransformerImpl jTrans = new ArchiveTransformerImpl(Filter.defaultFilter());
        jTrans.transform(ear);
        Set<String> classnames =  ClassReference.getClassNames();
        assertTrue(classnames.contains("jakarta.persistence.Persistence"), "expect to find jakarta.persistence.Persistence in " + classnames);
    }

    private static final String legacyTCKZipDownload = "https://download.eclipse.org/jakartaee/platform/10/jakarta-jakartaeetck-10.0.2.zip";
    private static final String unzippedLegacyTCK = "jakartaeetck";
    private static final String LegacyTCKFolderPropName = "LegacyTCKFolder";
    private static final String defaultFolderName = "legacytck";
    private static final String legacyTCKZip = "jakarta-jakartaeetck-10.0.2.zip";

    private static String LegacyTCKFolderName = System.getProperty(LegacyTCKFolderPropName, System.getProperty("java.io.tmpdir") + File.separator + defaultFolderName);
    private static final URL tckurl;
    static {
        try {
            tckurl = new URL("https://download.eclipse.org/jakartaee/platform/10/jakarta-jakartaeetck-10.0.2.zip");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static File legacyTckRoot;

    /**
     * Look for a previously downloaded TCK bundle, or download it,  and return the root dir containing the unzipped bundle.
     * @return root directory containing the unzipped TCK bundle
     */
    public static File maybeDownloadTck() {
        if(legacyTckRoot != null) {
            return legacyTckRoot;
        }

        System.out.println("looking for existing copy of jakarta-jakartaeetck-10.0.2.zip in folder " + LegacyTCKFolderName);
        if (System.getProperty("java.io.tmpdir") == null) {
            System.out.println("java.io.tmpdir needs to point to temp folder, exiting with failure code 3");
            System.exit(3);
        }
        if (LegacyTCKFolderName == null) {
            LegacyTCKFolderName = System.getProperty("java.io.tmpdir") + File.separator + defaultFolderName;
            System.out.println(LegacyTCKFolderPropName + "wasn't specified so will instead use " + LegacyTCKFolderName);
        }
        File legacyTCKFolder = new File(LegacyTCKFolderName);
        legacyTCKFolder.mkdirs();
        System.out.println("looking for existing extracted " + legacyTCKZip + " in folder " + LegacyTCKFolderName);

        File target = new File(legacyTCKFolder, "LegacyTCKFolderName");
        target.mkdirs();
        File targetTCKZipFile = new File(target, legacyTCKZip);
        if (targetTCKZipFile.exists()) {
            System.out.println("already downloaded " + targetTCKZipFile.getName());
        } else {
            System.out.println("will download " + legacyTCKZipDownload + " and extract contents into " + target.getName());
            downloadUsingStream(tckurl, target);

            System.out.println("will unzip " + legacyTCKZipDownload + " into " + target.getName());
            unzip(target);
            System.out.println("one time setup is complete");
        }
        legacyTckRoot = target;
        return target;
    }

    private static void downloadUsingStream(URL url, File fileFolder) {
        try {
            File file = new File(fileFolder, legacyTCKZip);
            BufferedInputStream bis = new BufferedInputStream(url.openStream());
            FileOutputStream fis = new FileOutputStream(file);
            byte[] buffer = new byte[10240];
            int count = 0;
            int loop = 0;
            System.out.println("downloading from " + url + " to " + fileFolder.getName());
            while ((count = bis.read(buffer, 0, 10240)) != -1) {
                fis.write(buffer, 0, count);
                if (loop++ == 1024 ) {
                    System.out.print(".");
                    loop = 0;
                }
            }
            fis.close();
            bis.close();
            System.out.println("finished download of " + url);
        } catch (Throwable e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
    private static void unzip(File fileFolder) {
        File file = new File(fileFolder, legacyTCKZip);
        byte[] buffer = new byte[10240];
        System.out.println("Unzipping " + file.getName());
        try {
            ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(file));
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                File newFile = new File(fileFolder, zipEntry.getName());
                String destDirPath = fileFolder.getCanonicalPath();
                String destFilePath = newFile.getCanonicalPath();
                if (!destFilePath.startsWith(destDirPath + File.separator)) {
                    throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
                }
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Could not create folder " + newFile.getName());
                    }
                } else {
                    if (!newFile.getParentFile().isDirectory() && !newFile.getParentFile().mkdirs()) {
                        throw new IOException("Could not create parent folder for " + newFile.getName());
                    }
                    FileOutputStream fileOutputStream = new FileOutputStream(newFile);
                    int len;
                    while ((len = zipInputStream.read(buffer)) > 0) {
                        fileOutputStream.write(buffer, 0, len);
                    }
                    fileOutputStream.close();
                }
                zipEntry = zipInputStream.getNextEntry();
            }
            zipInputStream.closeEntry();
            zipInputStream.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Finished Unzipping " + file.getName());
    }

    public static File fromTargetFile(String file) {
        // Locate or download the legacy TCK
        File target = maybeDownloadTck();
        target = new File(target, unzippedLegacyTCK);
        return locateTargetPackageFolder(target, file);
    }

    private static File locateTargetPackageFolder(File target, String targetFile) {
        File findTCKDistArchive = new File(target, File.separator + targetFile);
        System.out.println("locateTargetPackageFolder will look inside of " + target.getName() + " for findTCKDistArchive = " + findTCKDistArchive.getName());
        System.out.println("looking inside of " + findTCKDistArchive.getName() + " for the archive that contains a test client for package " + targetFile);
        if (findTCKDistArchive.exists()) {
            return findTCKDistArchive;
        } else {
            throw new RuntimeException("could not locate " + targetFile + " in " + findTCKDistArchive.getName());
        }
    }

}
