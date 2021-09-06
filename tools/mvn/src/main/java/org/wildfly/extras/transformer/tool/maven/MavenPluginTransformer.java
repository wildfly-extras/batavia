/*
 * Copyright 2020 The Apache Software Foundation.
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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Transform inputJar to outputJar.
 *
 * @author Scott Marlow
 */
@Mojo(name = "transform-classes", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class MavenPluginTransformer extends AbstractMojo {


    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    @Parameter(defaultValue = "${project.packaging}", required = true, readonly = true)
    private String packaging;

    @Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
    private String buildFolder;

    @Parameter(defaultValue = "${project.build.finalName}", required = true, readonly = true)
    private String targetName;

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private String outputFolder;

    @Parameter(defaultValue = "${configs.dir}", readonly = true)
    private String configsDir;

    @Parameter(defaultValue = "${project.compileClasspathElements}", required = true, readonly = true)
    private List<String> compileClasspathElements;

    @Parameter(property = "invert", required = false, defaultValue = "false")
    private boolean invert;

    /**
     * Specifying the inputFile + outputFile, is kind of an experiment to allow the maven plugin to
     * transform a specific input file, into an output directory. It will be done in addition to maven processing.
     */
    @Parameter(property = "inputFile")
    private File inputFile;

    @Parameter(property = "outputFile")
    private File outputFile;

    @Parameter(property = "overwrite", required = false, defaultValue="false")
    private boolean overwrite;

    @Override
    public void execute() throws MojoExecutionException {
        if (getLog().isDebugEnabled()) {
            dump();
        }
        if (inputFile != null && inputFile.isFile() && outputFile != null) {
            try {
                getLog().info("transforming specific input " + inputFile.getAbsolutePath() + " into " + outputFile.getAbsolutePath());
                HandleTransformation.transformFile(inputFile, outputFile, configsDir, getLog().isDebugEnabled(), invert);
                return;
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }

        if (inputFile != null && inputFile.isDirectory() && outputFolder != null) {
            File outputDirectory = new File(outputFolder);
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }
            // transform files in output folder 
            if (outputDirectory.isDirectory()) {
                try {
                    getLog().info("Transforming contents of folder " + inputFile + " to " + outputFolder);
                    HandleTransformation.transformDirectory(inputFile, outputDirectory, configsDir, getLog().isDebugEnabled(), overwrite, invert);
                    return;
                } catch (IOException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        }

        inputFile = null;

        if (mavenProject != null && mavenProject != null && mavenProject.getArtifact() != null && mavenProject.getArtifact().getFile() != null) {
            inputFile = mavenProject.getArtifact().getFile();
        } else if ((packaging.contains("jar") || packaging.contains("war") || packaging.contains("ear"))
                && buildFolder != null && targetName != null) {
            inputFile = new File(buildFolder + File.separatorChar + targetName + "." + packaging);
        } else if (buildFolder != null && targetName != null) {
            inputFile = new File(buildFolder + File.separatorChar + targetName + "." + "jar");
        }
        if (inputFile != null && inputFile.exists()) {
            File outputDir = new File(inputFile.getParentFile(), inputFile.getName() + ".temp");
            File outputFile = new File(outputDir, inputFile.getName());
            getLog().info("transforming " + inputFile.getAbsolutePath() + " into " + outputFile.getAbsolutePath());
            try {
                HandleTransformation.transformFile(inputFile, outputFile, configsDir, getLog().isDebugEnabled(), invert);
                if (outputDir.exists()) {
                    getLog().info("transformer generated output file " + outputFile.getAbsolutePath() + " "
                            + " outputFile size = " + outputFile.length());
                    getLog().info("deleting " + inputFile.getName());
                    inputFile.delete();
                    getLog().info("rename " + outputFile.getAbsolutePath() + " to " + inputFile.getAbsolutePath());
                    outputFile.renameTo(inputFile);
                } else {
                    getLog().info("transformer didn't generate " + outputDir.getAbsolutePath());
                }
            } catch (IOException e) {
                getLog().info(" caught exception " + e.getMessage());
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }

    }

    private void dump() {
        getLog().debug(this.getClass().getName() + " dump of maven Mojo state stuff:");
        Map pluginContext = getPluginContext();
        for (Object key : pluginContext.keySet()) {
            getLog().debug("plugin context key: " + key + " value: " + pluginContext.get(key));
        }
        getLog().debug("mavenProject = " + mavenProject);
        getLog().debug("packaging = " + packaging);
        getLog().debug("outputFolder = " + outputFolder);
        getLog().debug("buildFolder = " + buildFolder);
        getLog().debug("compileClasspathElements = " + compileClasspathElements);
        getLog().debug("inputJar =  " + inputFile);
        getLog().debug("outputJar =  " + outputFile);
        getLog().debug("targetName = " + targetName);
        getLog().debug("configsDirectory = " + configsDir);
    }

}
