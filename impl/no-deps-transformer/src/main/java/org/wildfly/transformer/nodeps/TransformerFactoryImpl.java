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
package org.wildfly.transformer.nodeps;

import org.wildfly.transformer.Transformer;
import org.wildfly.transformer.TransformerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class TransformerFactoryImpl extends TransformerFactory {

    private static final String DEFAULT_CONFIG = "default.mapping";
    private static final char DOT = '.';
    private static final char SEP = '/';

    @Override
    public Transformer newTransformer() throws IOException {
        InputStream is = null;
        try {
            is = TransformerImpl.class.getResourceAsStream(SEP + DEFAULT_CONFIG);
            final Properties defaultMapping = new Properties();
            defaultMapping.load(is);
            String to;
            final TransformerImpl.Builder builder = TransformerImpl.newInstance();
            for (String from : defaultMapping.stringPropertyNames()) {
                to = defaultMapping.getProperty(from);
                if (to.indexOf(DOT) != -1 || from.indexOf(DOT) != -1) {
                    throw new UnsupportedOperationException("Wrong " + DEFAULT_CONFIG + " configuration format");
                }
                builder.addMapping(from, to);
                if (from.indexOf(SEP) != -1 || to.indexOf(SEP) != -1) {
                    builder.addMapping(from.replace(SEP, DOT), to.replace(SEP, DOT));
                }
            }
            return builder.build();
        } finally {
            safeClose(is);
        }
    }

    private static void safeClose(final Closeable c) {
        try {
            if (c != null) c.close();
        } catch (final Throwable t) {
            // ignored
        }
    }

}
