/*
 * Copyright 2021 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extras.transformer.tool.maven;

import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_RESOURCES;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_SOURCES;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_TEST_RESOURCES;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_TEST_SOURCES;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Transforming source and resource files.
 * Use the proper lifecyle phase to process sources and tests.
 *
 * @author Emmanuel Hugonnet (c) 2021 Red Hat, Inc.
 */
@Mojo(name = "transform-sources", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class SourceTransformerMojo extends AbstractMojo {

    @Component
    protected MojoExecution execution;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    /**
     * Specifying the inputFile + outputFile, is kind of an experiment to allow the maven plugin to
     * transform a specific input file, into an output directory. It will be done in addition to maven processing.
     */
    @Parameter(property = "inputFile")
    private File inputFile;

    @Parameter(name = "source-project", property = "source.project")
    private File sourceProject;

    @Parameter(defaultValue = "${configs.dir}", readonly = true)
    private String configsDir;

    @Parameter(property = "overwrite", required = false, defaultValue = "false")
    private boolean overwrite;

    @Parameter(required = false, readonly = true)
    private String outputFolder;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        LifecyclePhase lifecyclePhase = valueOf(execution.getLifecyclePhase());
        try {
            if (inputFile != null && inputFile.isDirectory()) {
                File outputDir = getOutputDirectory(lifecyclePhase);
                try {
                    getLog().info("Transforming contents of folder " + inputFile + " to " + outputDir);
                    HandleTransformation.transformDirectory(inputFile, outputDir, configsDir, getLog().isDebugEnabled(), overwrite);
                    switch (lifecyclePhase) {
                        case GENERATE_SOURCES:
                            mavenProject.addCompileSourceRoot(new File(outputDir, inputFile.getName()).getAbsolutePath());
                            break;
                        case GENERATE_TEST_SOURCES:
                            mavenProject.addTestCompileSourceRoot(new File(outputDir, inputFile.getName()).getAbsolutePath());
                            break;
                        case GENERATE_RESOURCES:
                            Resource resource = new Resource();
                            resource.setDirectory(outputDir.getAbsolutePath());
                            mavenProject.addResource(resource);
                            break;
                        case GENERATE_TEST_RESOURCES:
                            Resource testResource = new Resource();
                            testResource.setDirectory(outputDir.getAbsolutePath());
                            mavenProject.addTestResource(testResource);
                            break;
                    }
                } catch (IOException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            } else if (sourceProject != null && sourceProject.isDirectory()) {
                getLog().info("Transforming contents of project " + sourceProject);
                Path sourceProjectPath = sourceProject.toPath();
                Path input = sourceProjectPath.resolve("src").resolve("main").resolve("java");
                if (Files.exists(input) && Files.isDirectory(input)) {
                    File outputDir = getOutputDirectory(GENERATE_SOURCES);
                    getLog().info("Transforming contents of folder " + input + " to " + outputDir);
                    HandleTransformation.transformDirectory(input.toFile(), outputDir, configsDir, getLog().isDebugEnabled(), overwrite);
                    mavenProject.addCompileSourceRoot(outputDir.getAbsolutePath());
                }
                input = sourceProjectPath.resolve("src").resolve("test").resolve("java");
                if (Files.exists(input) && Files.isDirectory(input)) {
                    File outputDir = getOutputDirectory(GENERATE_TEST_SOURCES);
                    getLog().info("Transforming contents of folder " + input + " to " + outputDir);
                    HandleTransformation.transformDirectory(input.toFile(), outputDir, configsDir, getLog().isDebugEnabled(), overwrite);
                    mavenProject.addTestCompileSourceRoot(outputDir.getAbsolutePath());
                }
                input = sourceProjectPath.resolve("src").resolve("main").resolve("resources");
                if (Files.exists(input) && Files.isDirectory(input)) {
                    File outputDir = getOutputDirectory(GENERATE_RESOURCES);
                    getLog().info("Transforming contents of folder " + input + " to " + outputDir);
                    HandleTransformation.transformDirectory(input.toFile(), outputDir, configsDir, getLog().isDebugEnabled(), overwrite);
                    Resource resource = new Resource();
                    resource.setDirectory(outputDir.getAbsolutePath());
                    mavenProject.addResource(resource);
                }
                input = sourceProjectPath.resolve("src").resolve("test").resolve("resources");
                if (Files.exists(input) && Files.isDirectory(input)) {
                    File outputDir = getOutputDirectory(GENERATE_TEST_RESOURCES);
                    getLog().info("Transforming contents of folder " + input + " to " + outputDir);
                    HandleTransformation.transformDirectory(input.toFile(), outputDir, configsDir, getLog().isDebugEnabled(), overwrite);
                    Resource testResource = new Resource();
                    testResource.setDirectory(outputDir.getAbsolutePath());
                    mavenProject.addTestResource(testResource);
                }
            }
        } catch (IOException ioex) {
            throw new MojoExecutionException("Error transforming code", ioex);
        }
    }

    private LifecyclePhase valueOf(String phase) {
        if (GENERATE_TEST_SOURCES.id().equals(phase)) {
            return GENERATE_TEST_SOURCES;
        }
        if (GENERATE_RESOURCES.id().equals(phase)) {
            return GENERATE_RESOURCES;
        }
        if (GENERATE_TEST_RESOURCES.id().equals(phase)) {
            return GENERATE_TEST_RESOURCES;
        }
        return GENERATE_SOURCES;
    }

    private File getOutputDirectory(LifecyclePhase phase) throws IOException {
        Path outputDirectory = new File(mavenProject.getBuild().getDirectory()).toPath();
        if (outputFolder == null) {
            switch (phase) {
                case GENERATE_TEST_SOURCES:
                    outputDirectory = outputDirectory.resolve("generated-test-sources").resolve("transformed");
                    break;
                case GENERATE_RESOURCES:
                    outputDirectory = outputDirectory.resolve("generated-resources").resolve("transformed");
                    break;
                case GENERATE_TEST_RESOURCES:
                    outputDirectory = outputDirectory.resolve("generated-test-resources").resolve("transformed");
                    break;
                case GENERATE_SOURCES:
                default:
                    outputDirectory = outputDirectory.resolve("generated-sources").resolve("transformed");
                    break;
            }
        } else {
            outputDirectory = new File(outputFolder).toPath();
        }
        if (!Files.exists(outputDirectory)) {
            Files.createDirectories(outputDirectory);
        }
        return outputDirectory.toFile();
    }
}
