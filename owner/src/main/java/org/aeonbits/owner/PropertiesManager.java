/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.crypto.Decryptor;
import org.aeonbits.owner.crypto.IdentityDecryptor;
import org.aeonbits.owner.event.*;
import org.aeonbits.owner.util.Util;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import static java.util.Collections.synchronizedList;
import static org.aeonbits.owner.Config.LoadType.FIRST;
import static org.aeonbits.owner.util.Util.*;

/**
 * Loads properties and manages access to properties handling concurrency.
 *
 * @author Luigi R. Viggiano
 */
class PropertiesManager implements Reloadable, Accessible, Mutable {

    private static final long serialVersionUID = 0L;

    private final Class<? extends Config> clazz;
    private final Map<?, ?>[] imports;
    private final Properties properties;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReadLock readLock = lock.readLock();
    private final WriteLock writeLock = lock.writeLock();

    private final LoadType loadType;
    private final List<URI> uris;
    private final HotReloadLogic hotReloadLogic;

    private volatile boolean loading = false;

    final List<ReloadListener> reloadListeners = synchronizedList(new LinkedList<ReloadListener>());

    private Object proxy;
    private final LoadersManager loaders;


    /**
     * A cache of encryptedKeys with its decryptor.
     * <p>
     * This allows each key has its own decryptor.
     * Reflection is slow.
     */
    private Map<Method, Decryptor> encryptedKeys = new HashMap<Method, Decryptor>();
    
    private PropertiesMapper propertiesMapper;

    final List<PropertyChangeListener> propertyChangeListeners = synchronizedList(
            new LinkedList<PropertyChangeListener>() {
                @Override
                public boolean remove(Object o) {
                    Iterator iterator = iterator();
                    while (iterator.hasNext()) {
                        Object item = iterator.next();
                        if (item.equals(o)) {
                            iterator.remove();
                            return true;
                        }
                    }
                    return false;
                }
            });

    PropertiesManager(Class<? extends Config> clazz, Properties properties, ScheduledExecutorService scheduler,
                      VariablesExpander expander, LoadersManager loaders, Map<?, ?>... imports) {
        this.clazz = clazz;
        this.properties = properties;
        this.loaders = loaders;
        this.imports = imports;
        ConfigURIFactory urlFactory = new ConfigURIFactory(clazz.getClassLoader(), expander);
        uris = toURIs(clazz.getAnnotation(Sources.class), urlFactory);

        PropertiesMapper.Builder builder = PropertiesMapper.builder().apply(clazz);

        for (Class<?> inter : clazz.getInterfaces()) {
            this.uris.addAll(toURIs(inter.getAnnotation(Sources.class), urlFactory));
            builder.apply(inter);
        }

        propertiesMapper = builder.build();

        LoadPolicy loadPolicy = clazz.getAnnotation(LoadPolicy.class);
        if (loadPolicy == null) {
            for (Class<?> inter : clazz.getInterfaces()) {
                loadPolicy = inter.getAnnotation(LoadPolicy.class);
                if (loadPolicy != null) {
                    break;
                }
            }
        }
        loadType = (loadPolicy != null) ? loadPolicy.value() : FIRST;

        HotReload hotReload = clazz.getAnnotation(HotReload.class);
        if (hotReload == null) {
            for (Class<?> inter : clazz.getInterfaces()) {
                hotReload = inter.getAnnotation(HotReload.class);
                if (hotReload != null) {
                    break;
                }
            }
        }
        if (hotReload != null) {
            hotReloadLogic = new HotReloadLogic(hotReload, uris, this);

            if (hotReloadLogic.isAsync())
                scheduler.scheduleAtFixedRate(new Runnable() {
                    public void run() {
                        hotReloadLogic.checkAndReload();
                    }
                }, hotReload.value(), hotReload.value(), hotReload.unit());
        } else {
            hotReloadLogic = null;
        }

        // We try to identify the DecryptorClass annotation, to assign the Decryptor to this configuration.
        // If it isn't present then we assign the IdentityDecryptor.
        DecryptorClass decryptorManager = clazz.getAnnotation(DecryptorClass.class);
        Class<? extends Decryptor> decryptorClazz;
        if (decryptorManager != null) {
            decryptorClazz = decryptorManager.value();
        } else {
            decryptorClazz = IdentityDecryptor.class;
        }
        Decryptor classDecryptor = Util.newInstance(decryptorClazz);

        // Reflection is slow, so we will cache all methods with EncryptedValue annotation.
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (propertiesMapper.isEncryptedValue(method)) {
                EncryptedValue encriptedKey = method.getAnnotation(EncryptedValue.class);
                decryptorClazz = encriptedKey.value();
                if (decryptorClazz != IdentityDecryptor.class) {
                    encryptedKeys.put(method, Util.newInstance(decryptorClazz));
                } else {
                    encryptedKeys.put(method, classDecryptor);
                }
            }
        }
    }

    PropertiesMapper propertiesMapper() {
        return propertiesMapper;
    }

    /**
     * If method contains the EncryptedValue annotation it Decrypts the value with the associated {@link Decryptor}.
     *
     * @param method with the key definition.
     * @param value the value to decrypt when the method contains the EncryptedValue annotation
     * @return
     *      the <code>value</code> if the method doesn't contains the EncryptedValue annotation
     *      or the <code>result of decrypt the value</code> if it does.
     */
    String decryptIfNecessary(Method method, String value) {
        // Value can't be null, it has been checked previously in PropertiesInvocationHandler.resolveProperty
        if (this.encryptedKeys.containsKey(method)) {
            Decryptor decryptor = this.encryptedKeys.get(method);
            return decryptor.decrypt(value);
        }
        return value;
    }

    private List<URI> toURIs(Sources sources, ConfigURIFactory uriFactory) {
        String[] specs = specs(sources, uriFactory);
        List<URI> result = new ArrayList<URI>();
        for (String spec : specs) {
            try {
                URI uri = uriFactory.newURI(spec);
                if (uri != null)
                    result.add(uri);
            } catch (URISyntaxException e) {
                throw unsupported(e, "Can't convert '%s' to a valid URI", spec);
            }
        }
        return result;
    }

    private String[] specs(Sources sources, ConfigURIFactory uriFactory) {
        if (sources != null) return sources.value();
        return defaultSpecs(uriFactory);
    }

    private String[] defaultSpecs(ConfigURIFactory uriFactory) {
        String prefix = uriFactory.toClasspathURLSpec(clazz.getName());
        return loaders.defaultSpecs(prefix);
    }

    Properties load() {
        writeLock.lock();
        try {
            return load(properties);
        } finally {
            writeLock.unlock();
        }
    }

    private Properties load(Properties props) {
        try {
            loading = true;
            propertiesMapper.applyDefaults(props, clazz);
            Properties loadedFromFile = doLoad();
            merge(props, loadedFromFile);
            merge(props, reverse(imports));
            return props;
        } finally {
            loading = false;
        }
    }

    @Delegate
    public void reload() {
        writeLock.lock();
        try {
            Properties loaded = load(new Properties());
            List<PropertyChangeEvent> events =
                    fireBeforePropertyChangeEvents(keys(properties, loaded), properties, loaded);
            ReloadEvent reloadEvent = fireBeforeReloadEvent(events, properties, loaded);
            applyPropertyChangeEvents(events);
            firePropertyChangeEvents(events);
            fireReloadEvent(reloadEvent);
        } catch (RollbackBatchException e) {
            ignore();
        } finally {
            writeLock.unlock();
        }
    }

    private Set<?> keys(Map<?, ?>... maps) {
        Set<Object> keys = new HashSet<Object>();
        for (Map<?, ?> map : maps)
            keys.addAll(map.keySet());
        return keys;
    }

    private void applyPropertyChangeEvents(List<PropertyChangeEvent> events) {
        for (PropertyChangeEvent event : events)
            performSetProperty(event.getPropertyName(), event.getNewValue());
    }

    private void fireReloadEvent(ReloadEvent reloadEvent) {
        for (ReloadListener listener : reloadListeners)
            listener.reloadPerformed(reloadEvent);
    }

    private ReloadEvent fireBeforeReloadEvent(List<PropertyChangeEvent> events, Properties oldProperties,
                                              Properties newProperties) throws RollbackBatchException {
        ReloadEvent reloadEvent = new ReloadEvent(proxy, events, oldProperties, newProperties);
        for (ReloadListener listener : reloadListeners)
            if (listener instanceof TransactionalReloadListener)
                ((TransactionalReloadListener) listener).beforeReload(reloadEvent);
        return reloadEvent;
    }


    @Delegate
    public void addReloadListener(ReloadListener listener) {
        if (listener != null)
            reloadListeners.add(listener);
    }

    @Delegate
    public void removeReloadListener(ReloadListener listener) {
        if (listener != null)
            reloadListeners.remove(listener);
    }

    @Delegate
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        if (listener != null)
            propertyChangeListeners.add(listener);
    }

    @Delegate
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        if (listener != null)
            propertyChangeListeners.remove(listener);
    }

    @Delegate
    public void addPropertyChangeListener(final String propertyName, final PropertyChangeListener listener) {
        if (propertyName == null || listener == null) return;

        final boolean transactional = listener instanceof TransactionalPropertyChangeListener;
        propertyChangeListeners.add(new PropertyChangeListenerWrapper(propertyName, listener, transactional));
    }

    private static class PropertyChangeListenerWrapper implements TransactionalPropertyChangeListener, Serializable {

        private final String propertyName;
        private final PropertyChangeListener listener;
        private final boolean transactional;

        PropertyChangeListenerWrapper(String propertyName, PropertyChangeListener listener,
                                      boolean transactional) {
            this.propertyName = propertyName;
            this.listener = listener;
            this.transactional = transactional;

        }

        public void beforePropertyChange(PropertyChangeEvent event) throws RollbackOperationException,
                RollbackBatchException {
            if (transactional && propertyNameMatches(event))
                ((TransactionalPropertyChangeListener) listener).beforePropertyChange(event);
        }

        private boolean propertyNameMatches(PropertyChangeEvent event) {
            return propertyName.equals(event.getPropertyName());
        }

        public void propertyChange(PropertyChangeEvent event) {
            if (propertyNameMatches(event))
                listener.propertyChange(event);
        }

        @Override
        public boolean equals(Object obj) {
            return listener.equals(obj);
        }

        @Override
        public int hashCode() {
            return listener.hashCode();
        }
    }

    private Properties doLoad() {
        return loadType.load(uris, loaders);
    }

    private static void merge(Properties results, Map<?, ?>... inputs) {
        for (Map<?, ?> input : inputs)
            results.putAll(input);
    }

    @Delegate
    public String getProperty(String key) {
        readLock.lock();
        try {
            return properties.getProperty(key);
        } finally {
            readLock.unlock();
        }
    }

    void syncReloadCheck() {
        if (hotReloadLogic != null && hotReloadLogic.isSync())
            hotReloadLogic.checkAndReload();
    }

    @Delegate
    public String getProperty(String key, String defaultValue) {
        readLock.lock();
        try {
            return properties.getProperty(key, defaultValue);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    public void storeToXML(OutputStream os, String comment) throws IOException {
        readLock.lock();
        try {
            properties.storeToXML(os, comment);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    public Set<String> propertyNames() {
        readLock.lock();
        try {
            LinkedHashSet<String> result = new LinkedHashSet<String>();
            for (Enumeration<?> propertyNames = properties.propertyNames(); propertyNames.hasMoreElements(); )
                result.add((String) propertyNames.nextElement());
            return result;
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    public void list(PrintStream out) {
        readLock.lock();
        try {
            properties.list(out);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    public void list(PrintWriter out) {
        readLock.lock();
        try {
            properties.list(out);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    public void store(OutputStream out, String comments) throws IOException {
        readLock.lock();
        try {
            properties.store(out, comments);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    @SuppressWarnings("unchecked")
    public void fill(Map map) {
        readLock.lock();
        try {
            for (String propertyName : propertyNames())
                map.put(propertyName, getProperty(propertyName));
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    public String setProperty(String key, String newValue) {
        writeLock.lock();
        try {
            String oldValue = properties.getProperty(key);
            try {
                if (eq(oldValue, newValue)) return oldValue;

                PropertyChangeEvent event = new PropertyChangeEvent(proxy, key, oldValue, newValue);
                fireBeforePropertyChange(event);
                String result = performSetProperty(key, newValue);
                firePropertyChange(event);
                return result;
            } catch (RollbackException e) {
                return oldValue;
            }
        } finally {
            writeLock.unlock();
        }
    }

    private String performSetProperty(String key, Object value) {
        return (value == null) ?
                performRemoveProperty(key) :
                asString(properties.setProperty(key, asString(value)));
    }

    @Delegate
    public String removeProperty(String key) {
        writeLock.lock();
        try {
            String oldValue = properties.getProperty(key);
            PropertyChangeEvent event = new PropertyChangeEvent(proxy, key, oldValue, null);
            fireBeforePropertyChange(event);
            String result = performRemoveProperty(key);
            firePropertyChange(event);
            return result;
        } catch (RollbackException e) {
            return properties.getProperty(key);
        } finally {
            writeLock.unlock();
        }
    }

    private String performRemoveProperty(String key) {
        return asString(properties.remove(key));
    }

    @Delegate
    public void clear() {
        writeLock.lock();
        try {
            List<PropertyChangeEvent> events =
                    fireBeforePropertyChangeEvents(keys(properties), properties, new Properties());
            applyPropertyChangeEvents(events);
            firePropertyChangeEvents(events);
        } catch (RollbackBatchException e) {
            ignore();
        } finally {
            writeLock.unlock();
        }
    }

    @Delegate
    public void load(InputStream inStream) throws IOException {
        writeLock.lock();
        try {
            Properties loaded = new Properties();
            loaded.load(inStream);
            performLoad(keys(loaded), loaded);
        } catch (RollbackBatchException ex) {
            ignore();
        } finally {
            writeLock.unlock();
        }
    }

    private void performLoad(Set keys, Properties props) throws RollbackBatchException {
        List<PropertyChangeEvent> events = fireBeforePropertyChangeEvents(keys, properties, props);
        applyPropertyChangeEvents(events);
        firePropertyChangeEvents(events);
    }

    @Delegate
    public void load(Reader reader) throws IOException {
        writeLock.lock();
        try {
            Properties loaded = new Properties();
            loaded.load(reader);
            performLoad(keys(loaded), loaded);
        } catch (RollbackBatchException ex) {
            ignore();
        } finally {
            writeLock.unlock();
        }
    }

    void setProxy(Object proxy) {
        this.proxy = proxy;
    }

    @Delegate
    @Override
    public String toString() {
        readLock.lock();
        try {
            return properties.toString();
        } finally {
            readLock.unlock();
        }
    }

    boolean isLoading() {
        return loading;
    }

    private List<PropertyChangeEvent> fireBeforePropertyChangeEvents(
            Set keys, Properties oldValues, Properties newValues) throws RollbackBatchException {
        List<PropertyChangeEvent> events = new ArrayList<PropertyChangeEvent>();
        for (Object keyObject : keys) {
            String key = (String) keyObject;
            String oldValue = oldValues.getProperty(key);
            String newValue = newValues.getProperty(key);
            if (!eq(oldValue, newValue)) {
                PropertyChangeEvent event =
                        new PropertyChangeEvent(proxy, key, oldValue, newValue);
                try {
                    fireBeforePropertyChange(event);
                    events.add(event);
                } catch (RollbackOperationException e) {
                    ignore();
                }
            }
        }
        return events;
    }

    private void firePropertyChangeEvents(List<PropertyChangeEvent> events) {
        for (PropertyChangeEvent event : events)
            firePropertyChange(event);
    }

    private void fireBeforePropertyChange(PropertyChangeEvent event) throws RollbackBatchException,
            RollbackOperationException {
        for (PropertyChangeListener listener : propertyChangeListeners)
            if (listener instanceof TransactionalPropertyChangeListener)
                ((TransactionalPropertyChangeListener) listener).beforePropertyChange(event);
    }

    private void firePropertyChange(PropertyChangeEvent event) {
        for (PropertyChangeListener listener : propertyChangeListeners)
            listener.propertyChange(event);
    }

    @Delegate
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Proxy)) return false;
        InvocationHandler handler = Proxy.getInvocationHandler(obj);
        if (!(handler instanceof PropertiesInvocationHandler))
            return false;
        PropertiesInvocationHandler propsInvocationHandler = (PropertiesInvocationHandler) handler;
        PropertiesManager that = propsInvocationHandler.propertiesManager;
        return this.equals(that);
    }

    private boolean equals(PropertiesManager that) {
        if (!this.isAssignationCompatibleWith(that))
            return false;
        this.readLock.lock();
        try {
            that.readLock.lock();
            try {
                return this.properties.equals(that.properties);
            } finally {
                that.readLock.unlock();
            }
        } finally {
            this.readLock.unlock();
        }
    }

    private boolean isAssignationCompatibleWith(PropertiesManager that) {
        return this.clazz.isAssignableFrom(that.clazz) || that.clazz.isAssignableFrom(this.clazz);
    }

    @Delegate
    @Override
    public int hashCode() {
        readLock.lock();
        try {
            return properties.hashCode();
        } finally {
            readLock.unlock();
        }
    }

}
