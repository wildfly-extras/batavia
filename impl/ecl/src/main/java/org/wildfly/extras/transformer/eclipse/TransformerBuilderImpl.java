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

import java.io.IOException;

import org.wildfly.extras.transformer.TransformerBuilder;
import org.wildfly.extras.transformer.ArchiveTransformer;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class TransformerBuilderImpl extends TransformerBuilder {

    @Override
    protected ArchiveTransformer buildInternal() throws IOException {
        return new ArchiveTransformerImpl(configs, verbose != null ? verbose : false);
    }

}
