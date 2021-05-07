package org.aeonbits.owner;

import static org.aeonbits.owner.Config.DisableableFeature.PREFIX;
import static org.aeonbits.owner.util.Util.isFeatureDisabled;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.EncryptedValue;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.Prefix;

class PropertiesMapper implements Serializable {

    private static final long serialVersionUID = 0L;

    private static class MethodInfo implements Serializable {

        private static final long serialVersionUID = 0L;

        private String key;
        private String prefix;
        private String originalKey;
        private String defaultValue;
        private boolean isEncryptedValue;

        private MethodInfo(Prefix prefixAnnotation, Method method) {

            DefaultValue defaultAnnotation = method.getAnnotation(DefaultValue.class);
            defaultValue = defaultAnnotation == null ? null : defaultAnnotation.value();

            Key keyAnnotation = method.getAnnotation(Key.class);
            originalKey = keyAnnotation == null ? method.getName() : keyAnnotation.value();

            prefix = "";
            if (prefixAnnotation != null && !isFeatureDisabled(method, PREFIX)) {
                prefix = prefixAnnotation.value();
            }

            key = prefix + originalKey;

            isEncryptedValue = method.getAnnotation(EncryptedValue.class) != null;
        }

        private boolean existsDefault() {
            return defaultValue != null;
        }
    }

    private Map<String, MethodInfo> methods;

    private PropertiesMapper(Map<String, MethodInfo> methods) {
        this.methods = methods;
    }

    boolean isEncryptedValue(Method method) {
        return methods.get(method.toString()).isEncryptedValue;
    }

    String key(Method method) {
        return methods.get(method.toString()).key;
    }

    void applyDefaults(Properties properties, Class<?> clazz) {
        Method[] clazzMethods = clazz.getMethods();
        for (Method method : clazzMethods) {
            MethodInfo info = methods.get(method.toString());
            if (info.existsDefault()) {
                properties.put(info.key, info.defaultValue);
            }
        }
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private Map<String, MethodInfo> methods;

        private Builder() {
            methods = new HashMap<String, MethodInfo>();
        }

        Builder apply(Class<?> clazz) {
            Prefix prefixAnnotation = clazz.getAnnotation(Prefix.class);
            for (Method method : clazz.getMethods()) {
                methods.put(method.toString(), new MethodInfo(prefixAnnotation, method));
            }
            return this;
        }

        PropertiesMapper build() {
            return new PropertiesMapper(Collections.unmodifiableMap(methods));
        }
    }
}
