/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.ConverterClass;
import org.aeonbits.owner.Config.CollectionConverterClass;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.beans.PropertyEditorManager.findEditor;
import static java.lang.Boolean.getBoolean;
import static java.lang.reflect.Modifier.isStatic;
import static org.aeonbits.owner.Converters.SpecialValue.NULL;
import static org.aeonbits.owner.Converters.SpecialValue.SKIP;
import static org.aeonbits.owner.util.Util.expandUserHome;
import static org.aeonbits.owner.util.Util.unreachableButCompilerNeedsThis;
import static org.aeonbits.owner.util.Util.unsupported;
import static org.aeonbits.owner.util.Reflection.isClassAvailable;

/**
 * Converter class from {@link java.lang.String} to property types.
 *
 * @author Luigi R. Viggiano
 */
enum Converters {

    ARRAY {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            if (!targetType.isArray()) return SKIP;

            Class<?> type = targetType.getComponentType();

            if (text.trim().isEmpty())
                return Array.newInstance(type, 0);

            Tokenizer tokenizer = TokenizerResolver.resolveTokenizer(targetMethod);
            String[] chunks = tokenizer.tokens(text);

            Converters converter = doConvert(targetMethod, type, chunks[0]).getConverter();
            Object result = Array.newInstance(type, chunks.length);

            for (int i = 0; i < chunks.length; i++) {
                String chunk = chunks[i];
                Object value = converter.tryConvert(targetMethod, type, chunk);
                Array.set(result, i, value);
            }

            return result;
        }
    },

    METHOD_WITH_COLLECTION_CONVERTER_CLASS_ANNOTATION {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            CollectionConverterClass annotation = targetMethod.getAnnotation(CollectionConverterClass.class);
            if (annotation == null) return SKIP;

            Class<? extends Converter> converterClass = annotation.value();
            return convertWithConverterClass(targetMethod, text, converterClass);
        }
    },

    COLLECTION {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            if (!Collection.class.isAssignableFrom(targetType)) return SKIP;

            Object[] array = convertToArray(targetMethod, text);
            Collection<Object> collection = Arrays.asList(array);
            Collection<Object> result = instantiateCollection(targetType);
            result.addAll(collection);
            return result;
        }

        private Object[] convertToArray(Method targetMethod, String text) {
            Class<?> type = getGenericType(targetMethod);
            Object stub = Array.newInstance(type, 0);
            return (Object[]) ARRAY.tryConvert(targetMethod, stub.getClass(), text);
        }

        private Class<?> getGenericType(Method targetMethod) {
            if (targetMethod.getGenericReturnType() instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) targetMethod.getGenericReturnType();
                return (Class<?>) parameterizedType.getActualTypeArguments()[0];
            }
            // Default generic type for raw collections.
            return String.class;
        }

        private <T> Collection<T> instantiateCollection(Class<? extends T> targetType) {
            if (targetType.isInterface())
                return instantiateCollectionFromInterface(targetType);
            return instantiateCollectionFromClass(targetType);
        }

        @SuppressWarnings("unchecked")
        private <T> Collection<T> instantiateCollectionFromClass(Class<? extends T> targetType) {
            try {
                return (Collection<T>) targetType.newInstance();
            } catch (Exception e) {
                throw unsupported(e, "Cannot instantiate collection of type '%s'", targetType.getCanonicalName());
            }
        }

        private <T> Collection<T> instantiateCollectionFromInterface(Class<? extends T> targetType) {
            if (List.class.isAssignableFrom(targetType))
                return new ArrayList<T>();
            else if (SortedSet.class.isAssignableFrom(targetType))
                return new TreeSet<T>();
            else if (Set.class.isAssignableFrom(targetType))
                return new LinkedHashSet<T>();
            return new ArrayList<T>();
        }

    },

    METHOD_WITH_CONVERTER_CLASS_ANNOTATION {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            ConverterClass annotation = targetMethod.getAnnotation(ConverterClass.class);
            if (annotation == null) return SKIP;

            Class<? extends Converter> converterClass = annotation.value();
            return convertWithConverterClass(targetMethod, text, converterClass);
        }
    },

    METHOD_WITH_REGISTERED_CONVERTER {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            if (!converterRegistry.containsKey(targetType)) return SKIP;

            Class<? extends Converter> converterClass = converterRegistry.get(targetType);
            return convertWithConverterClass(targetMethod, text, converterClass);
        }
    },

    PROPERTY_EDITOR {
        private final boolean isPropertyEditorAvailable =
                isClassAvailable("java.beans.PropertyEditorManager");

        private final boolean isPropertyEditorDisabled =
                getBoolean("org.aeonbits.owner.property.editor.disabled");

        private final boolean canUsePropertyEditors = isPropertyEditorAvailable && !isPropertyEditorDisabled;

        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            if (!canUsePropertyEditors)
                return SKIP;

            PropertyEditor editor = findEditor(targetType);
            if (editor == null) return SKIP;
            try {
                editor.setAsText(text);
                return editor.getValue();
            } catch (Exception e) {
                throw unsupportedConversion(e, targetType, text);
            }
        }
    },

    /*
     * This is needed for cases like when the PropertyEditor classes are not available
     */
    PRIMITIVE {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            if (!targetType.isPrimitive()) return SKIP;
            if (targetType == Byte.TYPE) return Byte.parseByte(text);
            if (targetType == Short.TYPE) return Short.parseShort(text);
            if (targetType == Integer.TYPE) return Integer.parseInt(text);
            if (targetType == Long.TYPE) return Long.parseLong(text);
            if (targetType == Boolean.TYPE) return Boolean.parseBoolean(text);
            if (targetType == Float.TYPE) return Float.parseFloat(text);
            if (targetType == Double.TYPE) return Double.parseDouble(text);
            return SKIP;
        }
    },

    FILE {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            if (targetType != File.class) return SKIP;
            return new File(expandUserHome(text));
        }
    },

    CLASS {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            if (targetType != Class.class) return SKIP;
            try {
                return Class.forName(text);
            } catch (ClassNotFoundException ex) {
                throw unsupported(ex, CANNOT_CONVERT_MESSAGE, text, targetType.getCanonicalName());
            }
        }
    },

    CLASS_WITH_STRING_CONSTRUCTOR {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            try {
                Constructor<?> constructor = targetType.getConstructor(String.class);
                return constructor.newInstance(text);
            } catch (Exception e) {
                return SKIP;
            }
        }
    },

    CLASS_WITH_VALUE_OF_METHOD {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            try {
                Method method = targetType.getMethod("valueOf", String.class);
                if (isStatic(method.getModifiers()))
                    return method.invoke(null, text);
                return SKIP;
            } catch (Exception e) {
                return SKIP;
            }
        }
    },

    CLASS_WITH_OBJECT_CONSTRUCTOR {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            try {
                Constructor<?> constructor = targetType.getConstructor(Object.class);
                return constructor.newInstance(text);
            } catch (Exception e) {
                return SKIP;
            }
        }
    },

    UNSUPPORTED {
        @Override
        Object tryConvert(Method targetMethod, Class<?> targetType, String text) {
            throw unsupportedConversion(targetType, text);
        }
    };

    private static Object convertWithConverterClass(
            Method targetMethod, String text, Class<? extends Converter> converterClass) {
        Converter<?> converter;
        try {
            converter = converterClass.newInstance();
        } catch (InstantiationException e) {
            throw unsupported(e, "Converter class %s can't be instantiated: %s",
                    converterClass.getCanonicalName(), e.getMessage());
        } catch (IllegalAccessException e) {
            throw unsupported(e, "Converter class %s can't be accessed: %s",
                    converterClass.getCanonicalName(), e.getMessage());
        }
        Object result = converter.convert(targetMethod, text);
        if (result == null) return NULL;
        return result;
    }

    private static final Map<Class<?>, Class<? extends Converter<?>>> converterRegistry =
            new ConcurrentHashMap<Class<?>, Class<? extends Converter<?>>>();

    abstract Object tryConvert(Method targetMethod, Class<?> targetType, String text);

    static void setTypeConverter(Class<?> type, Class<? extends Converter<?>> converter) {
        converterRegistry.put(type, converter);
    }

    public static void removeTypeConverter(Class<?> type) {
        converterRegistry.remove(type);
    }

    static Object convert(Method targetMethod, Class<?> targetType, String text) {
        return doConvert(targetMethod, targetType, text).getConvertedValue();
    }

    private static ConversionResult doConvert(Method targetMethod, Class<?> targetType, String text) {
        for (Converters converter : values()) {
            Object convertedValue = converter.tryConvert(targetMethod, targetType, text);
            if (convertedValue != SKIP)
                return new ConversionResult(converter, convertedValue);
        }
        return unreachableButCompilerNeedsThis();
    }

    private static UnsupportedOperationException unsupportedConversion(
            Exception cause, Class<?> targetType, String text) {
        return unsupported(cause, CANNOT_CONVERT_MESSAGE, text, targetType.getCanonicalName());
    }

    private static UnsupportedOperationException unsupportedConversion(Class<?> targetType, String text) {
        return unsupported(CANNOT_CONVERT_MESSAGE, text, targetType.getCanonicalName());
    }

    private static class ConversionResult {
        private final Converters converter;
        private final Object convertedValue;

        public ConversionResult(Converters converter, Object convertedValue) {
            this.converter = converter;
            this.convertedValue = convertedValue;
        }

        public Converters getConverter() {
            return converter;
        }

        public Object getConvertedValue() {
            return convertedValue;
        }
    }

    enum SpecialValue {
        /**
         * The NULL object: when tryConvert returns this object, the conversion result is null.
         */
        NULL,

        /**
         * The SKIP object: when tryConvert returns this object the conversion is skipped in favour of the next one.
         */
        SKIP
    }

    static final String CANNOT_CONVERT_MESSAGE = "Cannot convert '%s' to %s";
}
