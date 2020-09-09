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
package org.wildfly.extras.transformer.eclipse;

import org.eclipse.transformer.action.Changes;
import org.eclipse.transformer.Transformer;
import org.wildfly.extras.transformer.ArchiveTransformer;
import org.wildfly.extras.transformer.Config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class ArchiveTransformerImpl extends ArchiveTransformer {

    public static final String DEFAULT_RENAMES_REFERENCE = "jakarta-renames.properties";
    public static final String DEFAULT_MASTER_TXT_REFERENCE = "jakarta-txt-master.properties";
    public static final String DEFAULT_PER_CLASS_DIRECT_REFERENCE = "jakarta-per-class.properties";
    public static final String DEFAULT_DIRECT_REFERENCE = "jakarta-direct.properties";

    ArchiveTransformerImpl(final Map<Config, String> configs, final boolean verbose) {
        super(configs, verbose);
    }

    private Map<Transformer.AppOption, String> getOptionDefaults() {
        final HashMap<Transformer.AppOption, String> optionDefaults = new HashMap<>();
        optionDefaults.put(Transformer.AppOption.RULES_RENAMES, configs.containsKey(Config.PACKAGES_MAPPING) ? configs.get(Config.PACKAGES_MAPPING) : DEFAULT_RENAMES_REFERENCE);
        optionDefaults.put(Transformer.AppOption.RULES_MASTER_TEXT, configs.containsKey(Config.TEXT_FILES_MAPPING) ? configs.get(Config.TEXT_FILES_MAPPING) : DEFAULT_MASTER_TXT_REFERENCE);
        optionDefaults.put(Transformer.AppOption.RULES_PER_CLASS_CONSTANT, configs.containsKey(Config.PER_CLASS_MAPPING) ? configs.get(Config.PER_CLASS_MAPPING) : DEFAULT_PER_CLASS_DIRECT_REFERENCE);
        optionDefaults.put(Transformer.AppOption.RULES_DIRECT, DEFAULT_DIRECT_REFERENCE); //TODO: provide configuration option in builder
        return optionDefaults;
    }

    @Override
    public boolean transform(final File inJarFile, final File outJarFile) throws IOException {
        boolean transformed;
        try {
            if (!verbose) {
                // Disable all logging that is very verbose.
                System.setProperty("org.slf4j.simpleLogger.log.Transformer", "error");
            }
            List<String> args = new ArrayList<>();
            args.add(inJarFile.getAbsolutePath());
            args.add(outJarFile.getAbsolutePath());
            if (verbose) {
                args.add("-v");
            } else {
                args.add("--quiet");
            }
            String[] array = new String[args.size()];
            transformed = transform(args.toArray(array), true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return transformed;
    }

    private boolean transform(String[] args, boolean silent) throws IOException {
        java.io.ByteArrayOutputStream devNull = new java.io.ByteArrayOutputStream();
        Transformer jTrans;
        if (silent) {
            jTrans = new Transformer(new java.io.PrintStream(devNull), new java.io.PrintStream(devNull));
        } else {
            jTrans = new Transformer(System.out, System.err);
        }
        jTrans.setOptionDefaults(ArchiveTransformerImpl.class, getOptionDefaults());
        jTrans.setArgs(args);

        @SuppressWarnings("unused")
        int rc = jTrans.run();
        if (rc != 0) {
            throw new IOException("Error occured during transformation. Error code " + rc);
        }
        // New API needed in eclipse transformer.
        Changes changes = jTrans.getLastActiveChanges();
        if (changes != null) {
            return changes.hasChanges();
        }
        return false;
    }}
