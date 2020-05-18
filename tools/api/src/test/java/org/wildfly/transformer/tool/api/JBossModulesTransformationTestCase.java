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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jdenise
 */
public class JBossModulesTransformationTestCase {

    @Test
    public void testFailures() throws Exception {
        {
            boolean failed = false;
            try {
                ToolUtils.transformModules(Paths.get("foo"), Paths.get("bar"), null, false, null);
                failed = true;
            } catch (Exception ex) {
                // OK should have failed
            }
            if (failed) {
                throw new Exception("Test should have failed");
            }
        }

        {
            Path f = Files.createTempFile("foo", null);
            f.toFile().deleteOnExit();
            boolean failed = false;
            try {
                ToolUtils.transformModules(f, Paths.get("bar"), null, false, null);
                failed = true;
            } catch (Exception ex) {
                // OK should have failed
            }
            if (failed) {
                throw new Exception("Test should have failed");
            }
        }

        {
            Path modules = Files.createTempDirectory("transform-modules-tests");
            try {
                boolean failed = false;
                try {
                    ToolUtils.transformModules(modules, Paths.get("bar"), Paths.get("foo").toString(), false, null);
                    failed = true;
                } catch (Exception ex) {
                    // OK should have failed
                }
                if (failed) {
                    throw new Exception("Test should have failed");
                }
            } finally {
                recursiveDelete(modules);
            }
        }

        {
            Path modules = Files.createTempDirectory("transform-modules-tests");
            Path f = Files.createTempFile("foo", null);
            f.toFile().deleteOnExit();
            try {
                boolean failed = false;
                try {
                    ToolUtils.transformModules(modules, f, Paths.get("foo").toString(), false, null);
                    failed = true;
                } catch (Exception ex) {
                    // OK should have failed
                }
                if (failed) {
                    throw new Exception("Test should have failed");
                }
            } finally {
                recursiveDelete(modules);
            }
        }

        {
            Path modules = Files.createTempDirectory("transform-modules-tests");
            Path targetModules = Files.createTempDirectory("transform-modules-tests");
            try {
                boolean failed = false;
                try {
                    ToolUtils.transformModules(modules, targetModules, Paths.get("foo").toString(), false, null);
                    failed = true;
                } catch (Exception ex) {
                    // OK should have failed
                }
                if (failed) {
                    throw new Exception("Test should have failed");
                }
            } finally {
                recursiveDelete(modules);
                recursiveDelete(targetModules);
            }
        }
    }

    @Test
    public void testTransformModules() throws Exception {
        Path modules = Files.createTempDirectory("transform-modules-tests");
        doTestTransformModules(modules, modules);

        modules = Files.createTempDirectory("transform-modules-tests");
        Path layers = modules.resolve("system/layers/base");
        Files.createDirectories(layers);
        doTestTransformModules(modules, layers);
        modules = Files.createTempDirectory("transform-modules-tests");
        Path addOns = modules.resolve("system/add-ons/foo");
        Files.createDirectories(addOns);
        doTestTransformModules(modules, addOns);
    }

    private void doTestTransformModules(Path rootModules, Path modules) throws Exception {
        Path targetRootModules = Files.createTempDirectory("target-transform-modules-tests");
        Path targetModules = targetRootModules.resolve(rootModules.relativize(modules));
        try {
            Path dir = Files.createDirectories(modules.resolve("javax/foo/bar/main"));
            Path p = dir.resolve("foo.txt");
            Files.write(p, "hello".getBytes());

            Path jsonDir = Files.createDirectories(modules.resolve("javax/json/api/main"));

            String jsonModule = "<module xmlns=\"urn:jboss:module:1.9\" name=\"javax.json.api\">\n"
                    + "</module>";
            Path json = jsonDir.resolve("module.xml");
            Files.write(json, jsonModule.getBytes());
            Path bindDir = Files.createDirectories(modules.resolve("javax/json/bind/api/main"));
            String bindModule = "<module xmlns=\"urn:jboss:module:1.9\" name=\"javax.json.bind.api\">\n"
                    + "    <dependencies>\n"
                    + "        <module name=\"javax.json.api\"/>\n"
                    + "    </dependencies>\n"
                    + "</module>";
            Path bind = bindDir.resolve("module.xml");
            Files.write(bind, bindModule.getBytes());

            Path otherDir = Files.createDirectories(modules.resolve("foo/main"));
            String otherModule = "<module xmlns=\"urn:jboss:module:1.9\" name=\"foo\">\n"
                    + "    <dependencies>\n"
                    + "        <module name=\"javax.json.api\"/>\n"
                    + "        <module name=\"javax.json.bind.api\"/>\n"
                    + "    </dependencies>\n"
                    + "</module>";
            Path other = otherDir.resolve("module.xml");
            Files.write(other, otherModule.getBytes());

            Map<String, TransformedModule> transformedModules = ToolUtils.transformModules(rootModules, targetRootModules, null, false, null);
            Assert.assertTrue(transformedModules.size() == 3);
            Path targetP = targetModules.resolve(modules.relativize(p));
            Assert.assertTrue(Files.exists(targetP));
            Assert.assertEquals("hello", new String(Files.readAllBytes(targetP)));

            Path targetJson = targetModules.resolve(modules.relativize(json));
            Assert.assertFalse(Files.exists(targetJson));
            Path jakartaJson = targetModules.resolve("jakarta/json/api/main/module.xml");
            Assert.assertTrue(Files.exists(jakartaJson));
            Assert.assertFalse(new String(Files.readAllBytes(jakartaJson)).contains("javax"));
            Assert.assertTrue(new String(Files.readAllBytes(jakartaJson)).contains("jakarta.json.api"));

            Path targetBind = targetModules.resolve(modules.relativize(bind));
            Assert.assertFalse(Files.exists(targetBind));
            Path jakartaBind = targetModules.resolve("jakarta/json/bind/api/main/module.xml");
            Assert.assertTrue(Files.exists(jakartaBind));
            Assert.assertFalse(new String(Files.readAllBytes(jakartaBind)).contains("javax"));
            Assert.assertTrue(new String(Files.readAllBytes(jakartaBind)).contains("jakarta.json.api"));
            Assert.assertTrue(new String(Files.readAllBytes(jakartaBind)).contains("jakarta.json.bind.api"));

            Path targetOther = targetModules.resolve(modules.relativize(other));
            Assert.assertTrue(Files.exists(targetOther));
            Assert.assertFalse(new String(Files.readAllBytes(targetOther)).contains("javax"));
            Assert.assertTrue(new String(Files.readAllBytes(targetOther)).contains("jakarta.json.api"));
            Assert.assertTrue(new String(Files.readAllBytes(targetOther)).contains("jakarta.json.bind.api"));

            TransformedModule jsonTransModule = transformedModules.get("javax.json.api");
            Assert.assertNotNull(jsonTransModule);
            Assert.assertTrue(jsonTransModule.getName().equals("jakarta.json.api"));
            Assert.assertTrue(jsonTransModule.getTransformedDependencies().isEmpty());

            TransformedModule bindTransModule = transformedModules.get("javax.json.bind.api");
            Assert.assertNotNull(bindTransModule);
            Assert.assertTrue(bindTransModule.getName().equals("jakarta.json.bind.api"));
            Assert.assertTrue(bindTransModule.getTransformedDependencies().get("javax.json.api").equals("jakarta.json.api"));

            TransformedModule otherTransModule = transformedModules.get("foo");
            Assert.assertNotNull(otherTransModule);
            Assert.assertTrue(otherTransModule.getName().equals("foo"));
            Assert.assertTrue(otherTransModule.getTransformedDependencies().get("javax.json.api").equals("jakarta.json.api"));
            Assert.assertTrue(otherTransModule.getTransformedDependencies().get("javax.json.bind.api").equals("jakarta.json.bind.api"));

        } finally {
            recursiveDelete(rootModules);
            recursiveDelete(targetRootModules);
        }
    }

    @Test
    public void testCustomMapping() throws Exception {
        Path modules = Files.createTempDirectory("transform-modules-tests");
        Path targetModules = Files.createTempDirectory("target-transform-modules-tests");
        Path mapping = Files.createTempFile("transform-modules-mapping", null);
        String mappingContent = "javax/foo/api=jakarta/foo/api\njavax/foo/bind/api=jakarta/foo/bind/api";
        Files.write(mapping, mappingContent.getBytes());
        mapping.toFile().deleteOnExit();
        try {
            Path dir = Files.createDirectories(modules.resolve("javax/json/api/main"));
            Path p = dir.resolve("foo.txt");
            Files.write(p, "hello".getBytes());

            Path jsonDir = Files.createDirectories(modules.resolve("javax/foo/api/main"));

            String jsonModule = "<module xmlns=\"urn:jboss:module:1.9\" name=\"javax.foo.api\">\n"
                    + "</module>";
            Path json = jsonDir.resolve("module.xml");
            Files.write(json, jsonModule.getBytes());
            Path bindDir = Files.createDirectories(modules.resolve("javax/foo/bind/api/main"));
            String bindModule = "<module xmlns=\"urn:jboss:module:1.9\" name=\"javax.foo.bind.api\">\n"
                    + "    <dependencies>\n"
                    + "        <module name=\"javax.foo.api\"/>\n"
                    + "    </dependencies>\n"
                    + "</module>";
            Path bind = bindDir.resolve("module.xml");
            Files.write(bind, bindModule.getBytes());
            Map<String, TransformedModule> transformedModules = ToolUtils.transformModules(modules, targetModules, mapping.toString(), true, null);
            Assert.assertTrue(transformedModules.size() == 2);
            Path targetP = targetModules.resolve(modules.relativize(p));
            Assert.assertTrue(Files.exists(targetP));
            Assert.assertEquals("hello", new String(Files.readAllBytes(targetP)));

            Path targetJson = targetModules.resolve(modules.relativize(json));
            Assert.assertFalse(Files.exists(targetJson));
            Path jakartaJson = targetModules.resolve("jakarta/foo/api/main/module.xml");
            Assert.assertTrue(Files.exists(jakartaJson));
            Assert.assertFalse(new String(Files.readAllBytes(jakartaJson)).contains("javax"));
            Assert.assertTrue(new String(Files.readAllBytes(jakartaJson)).contains("jakarta.foo.api"));

            Path targetBind = targetModules.resolve(modules.relativize(bind));
            Assert.assertFalse(Files.exists(targetBind));
            Path jakartaBind = targetModules.resolve("jakarta/foo/bind/api/main/module.xml");
            Assert.assertTrue(Files.exists(jakartaBind));
            Assert.assertFalse(new String(Files.readAllBytes(jakartaBind)).contains("javax"));
            Assert.assertTrue(new String(Files.readAllBytes(jakartaBind)).contains("jakarta.foo.api"));
            Assert.assertTrue(new String(Files.readAllBytes(jakartaBind)).contains("jakarta.foo.bind.api"));

            TransformedModule jsonTransModule = transformedModules.get("javax.foo.api");
            Assert.assertNotNull(jsonTransModule);
            Assert.assertTrue(jsonTransModule.getName().equals("jakarta.foo.api"));
            Assert.assertTrue(jsonTransModule.getTransformedDependencies().isEmpty());

            TransformedModule bindTransModule = transformedModules.get("javax.foo.bind.api");
            Assert.assertNotNull(bindTransModule);
            Assert.assertTrue(bindTransModule.getName().equals("jakarta.foo.bind.api"));
            Assert.assertTrue(bindTransModule.getTransformedDependencies().get("javax.foo.api").equals("jakarta.foo.api"));
        } finally {
            recursiveDelete(modules);
            recursiveDelete(targetModules);
        }
    }

    @Test
    public void testTransformEmptyDirectories() throws Exception {
        Path modules = Files.createTempDirectory("transform-modules-tests");
        Path targetModules = Files.createTempDirectory("target-transform-modules-tests");
        try {
            Path jsonDir = Files.createDirectories(modules.resolve("javax/json/api"));
            Path jakartaDir = Files.createDirectories(modules.resolve("jakarta/json/api"));
            Path bindDir = Files.createDirectories(modules.resolve("javax/json/bind/api/main/foo"));
            Path jakartaBindDir = Files.createDirectories(modules.resolve("jakarta/json/bind/api/main/foo"));
            Path fooDir = Files.createDirectories(modules.resolve("foo/json/bind/api"));
            Map<String, TransformedModule> transformedModules = ToolUtils.transformModules(modules, targetModules, null, false, null);
            Assert.assertTrue(transformedModules.isEmpty());
            Assert.assertTrue(Files.list(targetModules).count() == 2);
            Assert.assertFalse(Files.exists(targetModules.resolve(modules.relativize(jsonDir))));
            Assert.assertTrue(Files.exists(targetModules.resolve(modules.relativize(jakartaDir))));
            Assert.assertFalse(Files.exists(targetModules.resolve(modules.relativize(bindDir))));
            Assert.assertTrue(Files.exists(targetModules.resolve(modules.relativize(jakartaBindDir))));
            Assert.assertTrue(Files.exists(targetModules.resolve(modules.relativize(fooDir))));
        } finally {
            recursiveDelete(modules);
            recursiveDelete(targetModules);
        }
    }

    static void recursiveDelete(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    try {
                        Files.delete(file);
                    } catch (IOException ex) {
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e)
                        throws IOException {
                    if (e != null) {
                        // directory iteration failed
                        throw e;
                    }
                    try {
                        Files.delete(dir);
                    } catch (IOException ex) {
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
        }
    }
}
