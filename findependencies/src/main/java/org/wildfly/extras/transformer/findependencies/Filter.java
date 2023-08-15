/*
 * Copyright 2023 Red Hat, Inc, and individual contributors.
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

package org.wildfly.extras.transformer.findependencies;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Filter to be applied against a classname reference to see if it matches
 *
 * @author Scott Marlow
 */
public class Filter {

    private final HashSet<String> match = new HashSet();

    private Filter(String... checkValues) {
        match.addAll(Arrays.asList(checkValues));
    }

    /**
     * Create a new Filter with specified package names to be included.  The package name specified needs to match against start of the
     *
     * @param match
     * @return the
     */
    public static Filter filter(String... match) {
        Filter filter = new Filter(match);
        return filter;
    }

    public static Filter defaultFilter() {
        return filter("jakarta");
    }

    /**
     * Check if the specified Class package name matches one of the (Filter construction time) identified class packages.
     *
     * @param checkIfClassNameMatches
     * @return
     */
    public boolean matchClassPackage(String checkIfClassNameMatches) {
        for (String check: match) {
            if (checkIfClassNameMatches.contains(check))
                return true;
        }
        return false;
    }

    public boolean matchDescriptor(String descriptor) {
        // TODO: extract the classname references and check if any specified classes match.
        //  TODO: Remove the following check after we are parsing the descriptor
        for (Iterator<String> it = match.iterator(); it.hasNext(); ) {
            String check = it.next();
            if (descriptor.contains(check)) {
                return true;
            }
        }
        return false;
    }


}
