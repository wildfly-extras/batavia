/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extras.transformer.tool.maven;

import java.util.Arrays;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractLifecyclePhaseTransformer extends AbstractMojo {

    @Component
    protected MojoExecution execution;
    private final LifecyclePhase[] allowedPhases;

    AbstractLifecyclePhaseTransformer(final LifecyclePhase... phases) {
        this.allowedPhases = Arrays.copyOf(phases, phases.length);
    }

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        final String currentPhase = execution.getLifecyclePhase();
        for (LifecyclePhase allowedPhase : allowedPhases) {
            if (allowedPhase.id().equals(currentPhase)) {
                execute(allowedPhase);
                return;
            }
        }
    }

    protected abstract void execute(LifecyclePhase lifecyclePhase) throws MojoExecutionException, MojoFailureException;
}
