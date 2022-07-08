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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    
    @Parameter(property = "invert", required = false, defaultValue = "false")
    private boolean invert;

    @Parameter(required = false, readonly = true)
    private String outputFolder;

    /**
     * Ignore existing source files if they exist in this projects source directory.
     */
    @Parameter(alias = "ignore-existing", defaultValue = "true", property = "transform.ignore.existing")
    private boolean ignoreExisting;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        LifecyclePhase lifecyclePhase = valueOf(execution.getLifecyclePhase());
        try {
            if (inputFile != null && inputFile.isDirectory()) {
                File outputDir = getOutputDirectory(lifecyclePhase);
                try {
                    getLog().info("Transforming contents of folder " + inputFile + " to " + outputDir);
                    HandleTransformation.transformDirectory(inputFile, outputDir, configsDir, getLog().isDebugEnabled(), overwrite, invert, getIgnored(lifecyclePhase));
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
                final Path baseDir = mavenProject.getBasedir().getCanonicalFile().toPath();
                transformSources(baseDir, sourceProjectPath, GENERATE_SOURCES);
                transformSources(baseDir, sourceProjectPath, GENERATE_TEST_SOURCES);
                transformSources(baseDir, sourceProjectPath, GENERATE_RESOURCES);
                transformSources(baseDir, sourceProjectPath, GENERATE_TEST_RESOURCES);
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

    private void transformSources(Path baseDir, Path sourceProjectPath, LifecyclePhase lifecyclePhase) throws IOException {
        List<String> roots = getSourceRoots(lifecyclePhase);
        getLog().debug(lifecyclePhase + " roots are " + roots);
        File outputDir = null;
        for (String rawRoot : roots) {
            Path canonicalRoot = new File(rawRoot).getCanonicalFile().toPath();
            if (canonicalRoot.startsWith(baseDir)) {
                Path relative = baseDir.relativize(canonicalRoot);
                Path input = sourceProjectPath.resolve(relative);
                if (Files.exists(input) && Files.isDirectory(input)) {
                    outputDir = getOutputDirectory(lifecyclePhase);
                    getLog().info("Transforming contents of folder " + input + " to " + outputDir);
                    HandleTransformation.transformDirectory(input.toFile(), outputDir, configsDir, getLog().isDebugEnabled(), overwrite, invert, getIgnored(lifecyclePhase));

                }
            } else {
                // TODO perhaps we could transform source that's external to the project directory
                getLog().info("Source folder " + rawRoot + " is not relative to " + baseDir + " -- skipping transformation");
            }
        }
        if (outputDir != null) {
            switch (lifecyclePhase) {
                case GENERATE_TEST_SOURCES:
                    mavenProject.addTestCompileSourceRoot(outputDir.getAbsolutePath());
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
                case GENERATE_SOURCES:
                default:
                    mavenProject.addCompileSourceRoot(outputDir.getAbsolutePath());
                    break;
            }
        }
    }

    private List<String> getSourceRoots(LifecyclePhase phase) {
        List<String> result = null;
        List<Resource> resources = null;
        switch (phase) {
            case GENERATE_TEST_SOURCES:
                result = mavenProject.getTestCompileSourceRoots();
                break;
            case GENERATE_RESOURCES:
                resources = mavenProject.getResources();
                break;
            case GENERATE_TEST_RESOURCES:
                resources = mavenProject.getTestResources();
                break;
            case GENERATE_SOURCES:
            default:
                result = mavenProject.getCompileSourceRoots();
                break;
        }
        if (result == null) {
            if (resources != null) {
                result = resources.stream().map(Resource::getDirectory).collect(Collectors.toList());
            } else {
                result = Collections.emptyList();
            }
        }
        return result;
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

    private Set<String> getIgnored(LifecyclePhase phase) throws IOException {
        if (!ignoreExisting) {
            return Collections.emptySet();
        }
        switch (phase) {
            case GENERATE_SOURCES:
                return getSourceFiles(Paths.get(mavenProject.getBuild().getSourceDirectory()));
            case GENERATE_TEST_SOURCES:
                return getSourceFiles(Paths.get(mavenProject.getBuild().getTestSourceDirectory()));
            case GENERATE_RESOURCES:
                return getSourceFiles(mavenProject.getResources());
            case GENERATE_TEST_RESOURCES:
                return getSourceFiles(mavenProject.getTestResources());
        }
        // There is nothing being transformed, therefore nothing to be ignored.
        return Collections.emptySet();
    }

    private Set<String> getSourceFiles(final Path sourceDirectory) throws IOException {
        final Set<String> sourceFiles = new HashSet<>();
        if (Files.exists(sourceDirectory)) {
            // Walk the path for the source files to ignore
            Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    sourceFiles.add(sourceDirectory.relativize(file).toString());
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return sourceFiles;
    }

    private Set<String> getSourceFiles(final Collection<Resource> resources) throws IOException {
        final Set<String> sourceFiles = new HashSet<>();
        for (Resource resource : resources) {
            sourceFiles.addAll(getSourceFiles(Paths.get(resource.getDirectory())));
        }
        return sourceFiles;
    }
}
