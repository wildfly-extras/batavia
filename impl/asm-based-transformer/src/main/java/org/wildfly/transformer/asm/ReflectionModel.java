package org.wildfly.transformer.asm;

import java.util.HashMap;
import java.util.Map;

/**
 * Model of package protected generated Reflection handler.
 *
 * @author Scott Marlow
 */
class BataviaReflectionModel {
    
    private static Map<String, String> mapping = new HashMap<>();
    private static final char DOT = '.';
    private static final char SEP = '/';
    
    // in the generated code, the mappings need to be based on the specified api/src/main/resources/default.mapping
    static {
        // replace the following call with actual default.mapping rules
        mapping.put("FIND","REPLACE");
        // replace the following call with actual default.mapping rules with '/' changed to '.'
        mapping.put("FIND".replace(SEP, DOT),"REPLACE".replace(SEP, DOT));
    }

    static Class<?> forName(String name) throws ClassNotFoundException {
        name =  replaceJavaXwithJakarta(name);
        return Class.forName(name);
    }
    
    static Class<?> forName(String name, boolean initialize, ClassLoader userClassLoader) throws ClassNotFoundException {
        name =  replaceJavaXwithJakarta(name);
        return Class.forName(name, initialize, userClassLoader);
    }
    
    private static String replaceJavaXwithJakarta(String desc) {
        StringBuilder stringBuilder = new StringBuilder(desc);
        for (Map.Entry<String, String> possibleReplacement: mapping.entrySet()) {
            String key = possibleReplacement.getKey();
            String value = possibleReplacement.getValue();
            int pos = stringBuilder.indexOf(key, 0);
            while(pos > -1) {
                int length = pos  + key.length();
                int next = pos + value.length();
                stringBuilder.replace(pos, length, value);
                pos = stringBuilder.indexOf(key, next);
            }
        }
        return stringBuilder.toString();
    }
}
