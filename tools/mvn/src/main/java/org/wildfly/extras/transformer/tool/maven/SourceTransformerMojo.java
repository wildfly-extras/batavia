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


import java.io.File;
import java.io.IOException;
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
 
    @Parameter(defaultValue = "${configs.dir}", readonly = true)
    private String configsDir;

    @Parameter(property = "overwrite", required = false, defaultValue="false")
    private boolean overwrite;

    @Parameter(required = false, readonly = true)
    private String outputFolder;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
         Path outputDirectory = new File(mavenProject.getBuild().getDirectory()).toPath();
        if (outputFolder == null) {
            if (LifecyclePhase.GENERATE_SOURCES.id().equals(execution.getLifecyclePhase())) {
                outputDirectory = outputDirectory.resolve("generated-sources").resolve("main");
            } else if (LifecyclePhase.GENERATE_TEST_SOURCES.id().equals(execution.getLifecyclePhase())) {
                outputDirectory = outputDirectory.resolve("generated-sources").resolve("test");
            } else if (LifecyclePhase.GENERATE_RESOURCES.id().equals(execution.getLifecyclePhase())) {
                outputDirectory = outputDirectory.resolve("generated-resources").resolve("main");
            } else if (LifecyclePhase.GENERATE_TEST_RESOURCES.id().equals(execution.getLifecyclePhase())) {
                outputDirectory = outputDirectory.resolve("generated-resources").resolve("test");
            }
        } else {
            outputDirectory = new File(outputFolder).toPath();
        }
        File outputDir = outputDirectory.toFile();
        if (inputFile != null && inputFile.isDirectory()) {
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            // transform files in output folder 
            if (outputDir.isDirectory()) {
                try {
                    getLog().info("Transforming contents of folder " + inputFile + " to " + outputDir);
                    HandleTransformation.transformDirectory(inputFile, outputDir, configsDir, getLog().isDebugEnabled(), overwrite);
                    if(LifecyclePhase.GENERATE_SOURCES.id().equals(execution.getLifecyclePhase())) {
                        mavenProject.addCompileSourceRoot(new File(outputDir, inputFile.getName()).getAbsolutePath());
                    } else if (LifecyclePhase.GENERATE_TEST_SOURCES.id().equals(execution.getLifecyclePhase())) {
                        mavenProject.addTestCompileSourceRoot(new File(outputDir, inputFile.getName()).getAbsolutePath());
                    } else if(LifecyclePhase.GENERATE_RESOURCES.id().equals(execution.getLifecyclePhase())) {
                        Resource resource = new Resource();
                        resource.setDirectory(outputDir.getAbsolutePath());
                        mavenProject.addResource(resource);
                    }else if(LifecyclePhase.GENERATE_TEST_RESOURCES.id().equals(execution.getLifecyclePhase())) {
                        Resource resource = new Resource();
                        resource.setDirectory(outputDir.getAbsolutePath());
                        mavenProject.addTestResource(resource);
                    }
                } catch (IOException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        }
    }
}
