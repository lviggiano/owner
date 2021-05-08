/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.typeconversion.collections;

import java.lang.reflect.Method;
import org.aeonbits.owner.Config.CollectionConverterClass;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Converter;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.aeonbits.owner.Config.ConverterClass;
import org.aeonbits.owner.Config.Separator;
import org.aeonbits.owner.Config.TokenizerClass;
import org.aeonbits.owner.Tokenizer;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Adam Huječek
 */
public class CollectionConverterClassTest {

    private MyConfig cfg;

    static public class Server {
        private final String name;
        private final Integer port;

        public Server(String name, Integer port) {
            this.name = name;
            this.port = port;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null || !(obj instanceof Server)) {
                return false;
            }
            final Server other = (Server) obj;
            if (this.name == null) {
                if (other.name != null) {
                    return false;
                }
            } else if(!this.name.equals(other.name)) {
                return false;
            }
            if (this.port == null) {
                if (other.port != null) {
                    return false;
                }
            } else if(!this.port.equals(other.port)) {
                return false;
            }
            return true;
        }
    }

    public interface MyConfig extends Config {
        @DefaultValue("google.com, yahoo.com:8080, owner.aeonbits.org:4000")
        @CollectionConverterClass(UnmodifiableListConverter.class)
        @ConverterClass(ServerConverter.class)
        Collection<Server> serversWithoutSeparatorOrTokenizer();

        @DefaultValue("google.com; yahoo.com:8080; owner.aeonbits.org:4000")
        @CollectionConverterClass(UnmodifiableListConverter.class)
        @ConverterClass(ServerConverter.class)
        @Separator(";")
        Collection<Server> serversWithSeparator();

        @DefaultValue("google.com^yahoo.com:8080^owner.aeonbits.org:4000")
        @CollectionConverterClass(UnmodifiableListConverter.class)
        @ConverterClass(ServerConverter.class)
        @TokenizerClass(SimpleTokenizer.class)
        Collection<Server> serversWithTokenizer();
    }

    public static class ServerConverter implements Converter<Server> {
        @Override
        public Server convert(Method targetMethod, String text) {
            String[] split = text.split(":", -1);
            String name = split[0];
            Integer port = 80;
            if (split.length >= 2)
                port = Integer.valueOf(split[1]);
            return new Server(name, port);
        }
    }

    public static class UnmodifiableListConverter implements Converter<List<?>> {

        @SuppressWarnings("unchecked")
        @Override
        public List<?> convert(Method targetMethod, String text) {
            String[] tokens;
            TokenizerClass tokenizer =
                    targetMethod.getAnnotation(TokenizerClass.class);
            if (tokenizer != null) {
                try {
                    Tokenizer t = tokenizer.value().getDeclaredConstructor().newInstance();
                    tokens = t.tokens(text);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                Separator sep = targetMethod.getAnnotation(Separator.class);
                String delimiter = sep != null ? sep.value() : ",";
                tokens = text.split(delimiter, -1);
            }
            ConverterClass converter =
                    targetMethod.getAnnotation(ConverterClass.class);
            try {
                Converter<?> c = converter.value().getDeclaredConstructor().newInstance();
                List list = new ArrayList(tokens.length);
                for (String token : tokens) {
                    list.add(c.convert(targetMethod, token.trim()));
                }
                return Collections.unmodifiableList(list);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class SimpleTokenizer implements Tokenizer {

        @Override
        public String[] tokens(String values) {
            return values.split("\\^", -1);
        }

    }

    @Before
    public void setUp() throws Exception {
        cfg = ConfigFactory.create(MyConfig.class);
    }

    @Test
    public void itShouldWorkWithoutSeparatorOrTokenizer() throws Exception {
        List<Server> expected = Arrays.asList(
                new Server("google.com", 80),
                new Server("yahoo.com", 8080),
                new Server("owner.aeonbits.org", 4000));
        assertEquals(expected, cfg.serversWithoutSeparatorOrTokenizer());
    }

    @Test
    public void shouldWorkWithSeparator() throws Exception {
        List<Server> expected = Arrays.asList(
                new Server("google.com", 80),
                new Server("yahoo.com", 8080),
                new Server("owner.aeonbits.org", 4000));
        assertEquals(expected, cfg.serversWithSeparator());
    }

    @Test
    public void shouldWorkWithTokenizer() throws Exception {
        List<Server> expected = Arrays.asList(
                new Server("google.com", 80),
                new Server("yahoo.com", 8080),
                new Server("owner.aeonbits.org", 4000));
        assertEquals(expected, cfg.serversWithTokenizer());
    }
}
