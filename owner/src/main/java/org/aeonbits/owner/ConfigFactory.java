/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.loaders.Loader;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

/**
 * A static factory class to instantiate {@link Config} instances.
 * <p>
 * By default a {@link Config} sub-interface is associated to a property having the same package name and class name as
 * the interface itself.</p>
 * <p>
 * Method names are mapped to property names contained in the property files.</p>
 * <p>
 * This is a singleton static class, to be used as convenience when only a single factory is needed inside an
 * application. It exposes the {@link #newInstance()} method to create new instances of {@link Factory} objects.
 * </p>
 * @author Luigi R. Viggiano
 */
public final class ConfigFactory {

    static final Factory INSTANCE = newInstance();

    /** Don't let anyone instantiate this class */
    private ConfigFactory() {}

    /**
     * Returns a new instance of a config Factory object.
     *
     * @return a new instance of a config Factory object.
     */
    public static Factory newInstance() {
        ScheduledExecutorService scheduler = newSingleThreadScheduledExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread result = new Thread(r);
                result.setDaemon(true);
                return result;
            }
        });
        Properties props = new Properties();
        return new DefaultFactory(scheduler, props);
    }

    /**
     * Creates a {@link Config} instance from the specified interface
     *
     * @param clazz   the interface extending from {@link Config} that you want to instantiate.
     * @param imports additional variables to be used to resolve the properties.
     * @param <T>     type of the interface.
     * @return an object implementing the given interface, which maps methods to property values.
     */
    public static <T extends Config> T create(Class<? extends T> clazz, Map<?, ?>... imports) {
        for( Map<?, ?> map : imports ){
            for( Object key : map.keySet() ){
                if( key == null || map.get(key) == null){
                    throw new IllegalArgumentException(String.format("An import contains a null value for key: '%s'", key));
                } else if (!(map.get(key) instanceof String)){
                    throw new IllegalArgumentException(String.format("An import contains a non-string value for key: '%s'", key));
                }
            }
        }
        return INSTANCE.create(clazz, imports);
    }

    /**
     * Set a property in the ConfigFactory. Those properties will be used to expand variables specified in the `@Source`
     * annotation, or by the ConfigFactory to configure its own behavior.
     *
     * @param key   the key for the property.
     * @param value the value for the property.
     * @return the old value.
     * @since 1.0.4
     */
    public static String setProperty(String key, String value) {
        return INSTANCE.setProperty(key, value);
    }

    /**
     * Those properties will be used to expand variables specified in the `@Source` annotation, or by the ConfigFactory
     * to configure its own behavior.
     *
     * @return the properties in the ConfigFactory
     * @since 1.0.4
     */
    public static Properties getProperties() {
        return INSTANCE.getProperties();
    }

    /**
     * Those properties will be used to expand variables specified in the `@Source` annotation, or by the ConfigFactory
     * to configure its own behavior.
     *
     * @param properties the properties to set in the config Factory.
     * @since 1.0.4
     */
    public static void setProperties(Properties properties) {
        INSTANCE.setProperties(properties);
    }

    /**
     * Returns the value for a given property.
     *
     * @param key the key for the property
     * @return the value for the property, or <code>null</code> if the property is not set.
     * @since 1.0.4
     */
    public static String getProperty(String key) {
        return INSTANCE.getProperty(key);
    }

    /**
     * Clears the value for the property having the given key. This means, that the given property is removed.
     *
     * @param key the key for the property to remove.
     * @return the old value for the given key, or <code>null</code> if the property was not set.
     * @since 1.0.4
     */
    public static String clearProperty(String key) {
        return INSTANCE.clearProperty(key);
    }

    /**
     * Registers a loader to enables additional file formats.
     *
     * @param loader the loader to register.
     * @throws NullPointerException if specified loader is <code>null</code>.
     * @since 1.0.5
     */
    public static void registerLoader(Loader loader) {
        INSTANCE.registerLoader(loader);
    }

    /**
     * Sets a converter for the given type. Setting a converter via this method will override any default converters
     * but not {@link Config.ConverterClass} annotations.
     *
     * @param type the type for which to set a converter.
     * @param converter the converter class to use for the specified type.
     * @since 1.0.10
     */
    public static void setTypeConverter(Class<?> type, Class<? extends Converter<?>> converter) {
        INSTANCE.setTypeConverter(type, converter);
    }

    /**
     * Removes a converter for the given type.
     * @param type the type for which to remove the converter.
     * @since 1.0.10
     */
    public static void removeTypeConverter(Class<?> type){
        INSTANCE.removeTypeConverter(type);
    }
}
