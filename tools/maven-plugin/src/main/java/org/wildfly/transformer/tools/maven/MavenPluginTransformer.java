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
package org.wildfly.transformer.tools.maven;

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
 * @goal transform-classes
 * @phase package
 */
@Mojo(name = "package", defaultPhase = LifecyclePhase.PACKAGE)
public class MavenPluginTransformer extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File projectRootFolder;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    @Parameter(defaultValue = "${project.groupId}", required = true, readonly = true)
    private String groupId;

    @Parameter(defaultValue = "${project.artifactId}", required = true, readonly = true)
    private String artifactId;

    @Parameter(defaultValue = "${project.version}", required = true, readonly = true)
    private String version;

    @Parameter(defaultValue = "${project.packaging}", required = true, readonly = true)
    private String packaging;

    @Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
    private String buildFolder;

    @Parameter(defaultValue = "${project.build.finalName}", required = true, readonly = true)
    private String targetName;

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private String outputFolder;

    @Parameter(defaultValue = "${packages.mapping.config}", readonly = true)
    private String packagesMapping;

    @Parameter(defaultValue = "${project.compileClasspathElements}", required = true, readonly = true)
    private List<String> compileClasspathElements;

    /**
     * Specifying the inputFile + outputFile, is kind of an experiment to allow the maven plugin to
     * transform a specific input file, into an output file.  It will be done in addition to maven processing.
     */
    @Parameter(property = "inputFile")
    private File inputFile;

    @Parameter(property = "outputFile")
    private File outputFile;

    public void execute() throws MojoExecutionException {
        dump();

        if (inputFile != null && outputFile != null) {
            try {
                System.out.println("transforming specific input " + inputFile.getName() + " into " + outputFile.getName());
                HandleTransformation.transformFile(inputFile, outputFile, packagesMapping);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }

        if (outputFolder != null) {
            File outputDirectory = new File(outputFolder);
            // transform files in output folder 
            if (outputDirectory.isDirectory()) {
                // TODO: also handle transforming project dependencies,
                //       each transformed dependendency needs to be switched to, in project pom
                //       look at various other wildfly/quarkus maven plugins as well for ideas.  

                // transform files in output folder
                try {
                    System.out.println("transforming contents of folder " + outputDirectory);
                    HandleTransformation.transformDirectory(outputDirectory, packagesMapping);
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
            inputFile = new File(buildFolder + File.separatorChar + targetName + "." + packaging.toString());
        } else if (buildFolder != null && targetName != null) {
            inputFile = new File(buildFolder + File.separatorChar + targetName + "." + "jar");
        }
        if (inputFile != null && inputFile.exists()) {
            outputFile = new File(inputFile.getName() + ".temp");
            System.out.println("transforming " + inputFile.getName() + " into " + outputFile.getName());
            try {
                HandleTransformation.transformFile(inputFile, outputFile, packagesMapping);
                if (outputFile.exists()) {
                    System.out.println("transformer generated output file " + outputFile.getName() + " " +
                            " outputFile size = " + outputFile.length());
                    System.out.println("deleting " + inputFile.getName());
                    inputFile.delete();
                    System.out.println("rename " + outputFile.getName() + " to " + inputFile.getName());
                    outputFile.renameTo(inputFile);
                } else {
                    System.out.println("transformer didn't generate " + outputFile.getName());
                }
            } catch (IOException e) {
                System.out.println(" caught exception " + e.getMessage());
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }


    }

    private void dump() {
        System.out.println(this.getClass().getName() + " dump of maven Mojo state stuff:");
        Map pluginContext = getPluginContext();
        // show plugin context map key + values
        for (Object key : pluginContext.keySet()) {
            System.out.println("plugin context key: " + key +
                    " value: " + pluginContext.get(key));
        }
        System.out.println("projectRootFolder = " + projectRootFolder);
        System.out.println("mavenProject = " + mavenProject);
        System.out.println("groupId = " + groupId);
        System.out.println("artifactId = " + artifactId);
        System.out.println("version = " + version);
        System.out.println("packaging = " + packaging);
        System.out.println("outputFolder = " + outputFolder);
        System.out.println("buildFolder = " + buildFolder);
        System.out.println("compileClasspathElements = " + compileClasspathElements);
        System.out.println("inputJar =  " + inputFile);
        System.out.println("outputJar =  " + outputFile);
        System.out.println("targetName = " + targetName);
        System.out.println("packagesMapping = " + packagesMapping);
    }

}
