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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.NodeList;
import org.wildfly.transformer.TransformerBuilder;

/**
 *
 * @author jdenise
 */
final class JBossModulesTransformer {

    private static final String SYSTEM = "system";
    private static final String LAYERS = "layers";
    private static final String ADD_ONS = "add-ons";

    private static Boolean DEBUG = Boolean.getBoolean("transform.modules.debug");

    static Map<String, TransformedModule> transform(Path modulesDir, Path modulesTargetDir, String modulesMappingFile,
            boolean transformArtifacts,
            final String packagesMappingFile) throws IOException {
        if (!Files.exists(modulesDir) || !Files.isDirectory(modulesDir)) {
            throw new IllegalArgumentException("Invalid modules directory " + modulesDir);
        }
        if (!Files.exists(modulesTargetDir) || !Files.isDirectory(modulesTargetDir)) {
            throw new IllegalArgumentException("Invalid modules target directory " + modulesTargetDir);
        }
        Map<String, String> modulesMapping = buildMapping(modulesMappingFile);
        Map<String, String> nameMapping = new HashMap<>();
        for (Entry<String, String> entry : modulesMapping.entrySet()) {
            String key = entry.getKey().replaceAll("/", ".");
            String value = entry.getValue().replaceAll("/", ".");
            nameMapping.put(key, value);
        }
        Map<Path, Set<Path>> files = new HashMap<>();
        visitLayers(modulesDir, files);
        visitAddOns(modulesDir, files);
        visitOtherModules(modulesDir, files);
        Map<String, TransformedModule> transformedModules = new HashMap<>();
        for (Entry<Path, Set<Path>> entry : files.entrySet()) {
            //recreate the root dir
            Path targetRootDir = modulesTargetDir.resolve(entry.getKey());
            Path srcRootDir = modulesDir.resolve(entry.getKey());
            Files.createDirectories(targetRootDir);
            for (Path path : entry.getValue()) {
                Path transformed = path;
                String name = null;
                for (Entry<String, String> mapping : modulesMapping.entrySet()) {
                    Path key = Paths.get(mapping.getKey());
                    Path value = Paths.get(mapping.getValue());
                    if (path.equals(key)) { // an empty directory that matches the mapping
                        transformed = value;
                    } else if (path.startsWith(key)) {
                        Path suffix = path.subpath(key.getNameCount(), path.getNameCount());
                        transformed = value.resolve(suffix);
                        name = value.toString().replaceAll("/", ".");
                        break;
                    }
                }

                Path src = srcRootDir.resolve(path);
                // Just a directory, create transformed one
                if (Files.isDirectory(src)) {
                    Files.createDirectories(targetRootDir.resolve(transformed));
                    continue;
                }
                //create the parent directories
                Path parentDir = targetRootDir.resolve(transformed.getParent());
                Files.createDirectories(parentDir);
                Path target = parentDir.resolve(transformed.getFileName());
                //copy content from original
                if (src.getFileName().toString().equals("module.xml")) {
                    // Transform it
                    transformDescriptor(src, target, name, nameMapping, transformedModules);
                } else {
                    if (transformArtifacts && src.toString().endsWith(".jar")) {
                        if (DEBUG) {
                            System.out.println("Transforming jar file " + src.toFile());
                        }
                        ToolUtils.transformJarFile(src.toFile(), target.toFile(), packagesMappingFile);
                    } else {
                        Files.copy(src, target);
                    }
                }
            }
        }
        return transformedModules;
    }

    private static Map<String, String> buildMapping(String mappingFile) throws IOException {
        InputStream instream = null;
        if (mappingFile != null) {
            final File userConfig = new File(mappingFile);

            if (userConfig.exists() && userConfig.isFile()) {
                instream = new FileInputStream(mappingFile);
            } else {
                throw new IllegalArgumentException("Invalid jboss modules mapping file " + userConfig);
            }
        }
        if (instream == null) {
            instream = TransformerBuilder.class.getResourceAsStream("/jboss-modules-default.mapping");
        }
        if (instream == null) {
            throw new IllegalArgumentException("Couldn't find specified jboss modules mapping file neither on file system nor on class path");
        }

        final Properties modulesMapping = new Properties();
        modulesMapping.load(instream);
        Map<String, String> mapping = new HashMap<>();
        for (String key : modulesMapping.stringPropertyNames()) {
            mapping.put(key, modulesMapping.getProperty(key));
        }
        return mapping;
    }

    private static void visitLayers(final Path srcModulesDir, Map<Path, Set<Path>> allFiles) throws IOException {
        final Path layersDir = srcModulesDir.resolve(SYSTEM).resolve(LAYERS);
        if (Files.exists(layersDir)) {
            try (Stream<Path> layers = Files.list(layersDir)) {
                final Iterator<Path> i = layers.iterator();
                while (i.hasNext()) {
                    Path p = i.next();
                    Set<Path> files = new HashSet<>();
                    allFiles.put(srcModulesDir.relativize(p), files);
                    visit(p, files);
                }
            }
        }

    }

    private static void visitAddOns(Path srcModulesDir, Map<Path, Set<Path>> allFiles) throws IOException {
        final Path addOnsDir = srcModulesDir.resolve(SYSTEM).resolve(ADD_ONS);
        if (Files.exists(addOnsDir)) {
            try (Stream<Path> addOn = Files.list(addOnsDir)) {
                final Iterator<Path> i = addOn.iterator();
                while (i.hasNext()) {
                    Path p = i.next();
                    Set<Path> files = new HashSet<>();
                    allFiles.put(srcModulesDir.relativize(p), files);
                    visit(p, files);
                }
            }
        }
    }

    private static void visitOtherModules(Path modulesDir, Map<Path, Set<Path>> allFiles) throws IOException {
        Set<Path> files = new HashSet<>();
        allFiles.put(Paths.get(""), files);
        Files.walkFileTree(modulesDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // Skip system sub directory, it is handled when handling layers and add-ons
                if (dir.getFileName().toString().equals(SYSTEM) && dir.getParent().equals(modulesDir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                // Add empty directories.
                try (Stream<Path> stream = Files.list(dir)) {
                    if (stream.count() == 0) {
                        files.add(modulesDir.relativize(dir));
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                files.add(modulesDir.relativize(file));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void visit(Path source, Set<Path> files) throws IOException {
        Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>() {

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        // Add empty directories.
                        try (Stream<Path> stream = Files.list(dir)) {
                            if (stream.count() == 0) {
                                files.add(source.relativize(dir));
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {

                files.add(source.relativize(file));

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void transformDescriptor(Path orig, Path target, String name, Map<String, String> nameMapping, Map<String, TransformedModule> transformedModules) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(orig.toFile())) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            org.w3c.dom.Document document = null;
            try {
                DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

                document = documentBuilder.parse(fileInputStream);
            } catch (Exception ex) {
                throw new IOException("Failed to parse document", ex);
            }

            org.w3c.dom.Element root = document.getDocumentElement();
            if (!root.getTagName().equals("module")
                    && !root.getTagName().equals("module-alias")) {
                return;
            }
            String originalName = root.getAttribute("name");
            TransformedModule transformedModule = null;
            if (name != null) {
                if (DEBUG) {
                    System.out.println("Transforming JBoss module " + originalName + " => " + name);
                }
                root.setAttribute("name", name);
                transformedModule = new TransformedModule(name, originalName);
            }
            final NodeList dependenciesElement = root.getElementsByTagName("dependencies");
            if (dependenciesElement != null && dependenciesElement.getLength() > 0) {

                org.w3c.dom.Element deps = (org.w3c.dom.Element) dependenciesElement.item(0);

                final NodeList modules = deps.getElementsByTagName("module");
                if (modules != null && modules.getLength() > 0) {
                    final int artifactCount = modules.getLength();
                    for (int i = 0; i < artifactCount; i++) {
                        final org.w3c.dom.Element element = (org.w3c.dom.Element) modules.item(i);
                        String value = element.getAttribute("name");
                        String transformed = nameMapping.get(value);
                        if (transformed != null) {
                            if (DEBUG) {
                                System.out.println("module " + root.getAttribute("name") + ", " + value + " => " + transformed);
                            }
                            if (transformedModule == null) {
                                transformedModule = new TransformedModule(originalName, originalName);
                            }
                            transformedModule.addDependency(value, transformed);
                            element.setAttribute("name", transformed);
                        }
                    }
                }
            }
            // now serialize the result
            Files.deleteIfExists(target);
            try {
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                StreamResult output = new StreamResult(target.toFile());
                DOMSource input = new DOMSource(document);

                transformer.transform(input, output);
            } catch (TransformerException ex) {
                throw new IOException(ex);
            }
            if (transformedModule != null) {
                transformedModules.put(originalName, transformedModule);
            }
        }
    }
}
