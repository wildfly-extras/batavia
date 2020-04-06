package org.wildfly.transformer;

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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

/**
 * Transform inputJar to outputJar.
 *
 * @goal touch
 * @phase process-sources
 */
@Mojo(name = "enhance", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class MavenPluginTransformer
        extends AbstractMojo {

    
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
    
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private String outputFolder;
    
    @Parameter(defaultValue = "${project.compileClasspathElements}", required = true, readonly = true)
    private List<String> compileClasspathElements;
    
    //@Parameter(defaultValue = "inputJar.jar",required = false)
    //private File inputJar;

    //@Parameter(defaultValue = "outputJar.jar",required = false)
    //private File outputJar;

    public void execute()
            throws MojoExecutionException {
        dump();

        File outputDirectory = new File(outputFolder);
        // transform files in output folder 
        if (outputDirectory.isDirectory()) {
            // TODO: also handle transforming project dependencies,
            //       each transformed dependendency needs to be switched to, in project pom
            
            // For now, just transform files in output folder
            try {
                HandleTransformation.transformFolder(outputDirectory);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        } else {
            System.out.println("TODO: handle other cases, like transforming a jar/war/ear file");
        }
                
        //if (!outputJar.exists()) {
        //    outputJar.mkdirs();
        //}
        //
        //if (!inputJar.exists()) {
        //    throw new MojoExecutionException("input file " + inputJar.getName() + " does not exist");
        //}
        
    }

    void dump() {
        System.out.println("dump of maven Mojo state stuff:");
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
        if (outputFolder != null) {
            try {
                Process process = Runtime.getRuntime().exec("tree " + outputFolder);
                int exitValue = 0;
                try {
                    exitValue = process.waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (exitValue != 0) {
                    System.out.println("ls command failed with: " + exitValue);
                }
                String line;
                BufferedReader br = 
                        new BufferedReader(new InputStreamReader(process.getInputStream()));
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
                process.destroy();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("compileClasspathElements = " + compileClasspathElements);
        
        
    }
}
