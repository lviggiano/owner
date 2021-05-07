/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

import static org.aeonbits.owner.Config.DisableableFeature.PARAMETER_FORMATTING;
import static org.aeonbits.owner.Config.DisableableFeature.VARIABLE_EXPANSION;
import static org.aeonbits.owner.Converters.SpecialValue.NULL;
import static org.aeonbits.owner.Converters.convert;
import static org.aeonbits.owner.PreprocessorResolver.resolvePreprocessors;
import static org.aeonbits.owner.util.Util.isFeatureDisabled;
import static org.aeonbits.owner.util.Reflection.invokeDefaultMethod;
import static org.aeonbits.owner.util.Reflection.isDefault;

/**
 * This {@link InvocationHandler} receives method calls from the delegate instantiated by {@link ConfigFactory} and maps
 * it to a property value from a property file, or a {@link Config.DefaultValue} specified in method annotation.
 * <p>
 * The {@link Config.Key} annotation can be used to override default mapping between method names and property names.
 * </p>
 * <p>
 * Automatic conversion is handled between the property value and the return type expected by the method of the
 * delegate.
 * </p>
 *
 * @author Luigi R. Viggiano
 */
class PropertiesInvocationHandler implements InvocationHandler, Serializable {

    private static final long serialVersionUID = 5432212884255718342L;
    private transient List<DelegateMethodHandle> delegates;
    private final Object jmxSupport;
    private final StrSubstitutor substitutor;
    final PropertiesManager propertiesManager;


    PropertiesInvocationHandler(PropertiesManager manager, Object jmxSupport) {
        this.propertiesManager = manager;
        this.jmxSupport = jmxSupport;
        delegates = findDelegates(manager, jmxSupport);
        this.substitutor = new StrSubstitutor(manager.load());
    }

    public Object invoke(Object proxy, Method invokedMethod, Object... args) throws Throwable {
        propertiesManager.syncReloadCheck();

        if (isDefault(invokedMethod))
            return invokeDefaultMethod(proxy, invokedMethod, args);

        DelegateMethodHandle delegate = getDelegateMethod(invokedMethod);
        if (delegate != null)
            return delegate.invoke(args);

        return resolveProperty(invokedMethod, args);
    }

    private DelegateMethodHandle getDelegateMethod(Method invokedMethod) {
        for (DelegateMethodHandle delegate : delegates)
            if (delegate.matches(invokedMethod))
                return delegate;
        return null;
    }

    private Object resolveProperty(Method method, Object... args) {
        String key = expandKey(method, args);
        String value = propertiesManager.getProperty(key);

        // TODO: this if should go away! See #84 and #86
        if (value == null && !isFeatureDisabled(method, VARIABLE_EXPANSION)) {
            String unexpandedKey = propertiesManager.propertiesMapper().key(method);
            value = propertiesManager.getProperty(unexpandedKey);
        }
        if (value == null)
            return null;
        value = preProcess(method, value);
        Object result = convert(method, method.getReturnType(),
                format(method, propertiesManager
                    .decryptIfNecessary(method, expandVariables(method, value)),
                    args));
        if (result == NULL) return null;
        return result;
    }

    private String preProcess(Method method, String value) {
        List<Preprocessor> preprocessors = resolvePreprocessors(method);
        String result = value;
        for (Preprocessor preprocessor : preprocessors)
            result = preprocessor.process(result);
        return result;
    }

    private String expandKey(Method method, Object... args) {
        String key = propertiesManager.propertiesMapper().key(method);
        if (isFeatureDisabled(method, VARIABLE_EXPANSION))
            return key;
        return substitutor.replace(key, args);
    }

    private String format(Method method, String format, Object... args) {
        if (isFeatureDisabled(method, PARAMETER_FORMATTING))
            return format;

        // If there are no arguments to format, we can just return.
        // This is also helpful when the {@code format} is a property value that contains a '%' character,
        // such as '@#$%^&*()" (e.g., a clear-text password). In such cases, the '%' character is not
        // a placeholder in a format string -- its just a random character in the property value.
        if ( args == null || args.length == 0 )
            return format;

        try {
            // Do this to achieve property expansion
            return String.format(format, args);
            }
        catch ( Exception e ) {
            // There's no guarantee that a property value from a config file
            // is a legal format string. When formatting doesn't work, let's
            // just return the original property value.
            return format;
            }
    }

    private String expandVariables(Method method, String value) {
        if (isFeatureDisabled(method, VARIABLE_EXPANSION))
            return value;
        return substitutor.replace(value);
    }

    private List<DelegateMethodHandle> findDelegates(Object... targets) {
        List<DelegateMethodHandle> result = new LinkedList<DelegateMethodHandle>();
        for (Object target : targets) {
            if (target == null)
                continue;
            Method[] methods = target.getClass().getMethods();
            for (Method m : methods)
                if (m.getAnnotation(Delegate.class) != null)
                    result.add(new DelegateMethodHandle(target, m));
        }
        return result;
    }

    public <T extends Config> void setProxy(T proxy) {
        propertiesManager.setProxy(proxy);
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        delegates = findDelegates(propertiesManager, jmxSupport);
    }
}
