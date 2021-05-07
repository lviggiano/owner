package org.aeonbits.owner.prefix;

import static org.aeonbits.owner.Config.DisableableFeature.*;
import static org.aeonbits.owner.util.Collections.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Config.*;
import org.junit.Test;

public class PrefixKeyExpansionTest {

    @Sources("classpath:org/aeonbits/owner/variableexpansion/KeyExpansionExample.xml")
    @Prefix("servers.${env}.")
    public interface MyConfig extends Config {

        String name();

        String hostname();

        Integer port();

        String user();

        @DisableFeature(VARIABLE_EXPANSION)
        String password();
    }

    @Test
    public void testKeyExpansion() {
        MyConfig cfg = ConfigFactory.create(MyConfig.class, map("env", "dev"));

        assertEquals("DEV", cfg.name());
        assertEquals("devhost", cfg.hostname());
        assertEquals(new Integer(6000), cfg.port());
        assertEquals("myuser1", cfg.user());
        assertNull(cfg.password()); // expansion is disabled on method level
    }

    @DisableFeature(VARIABLE_EXPANSION)
    @Sources("classpath:org/aeonbits/owner/variableexpansion/KeyExpansionExample.xml")
    @Prefix("servers.${env}.")
    public interface MyConfigWithExpansionDisabled extends Config {

        String name();

        String hostname();

        Integer port();

        String user();

        String password();
    }

    @Test
    public void testKeyExpansionDisabled() {
        MyConfigWithExpansionDisabled cfg = ConfigFactory.create(MyConfigWithExpansionDisabled.class, map("env", "dev"));

        assertNull(cfg.name());
        assertNull(cfg.hostname());
        assertNull(cfg.port());
        assertNull(cfg.user());
        assertNull(cfg.password());
    }

    @Sources("classpath:org/aeonbits/owner/variableexpansion/KeyExpansionExample.xml")
    @Prefix("servers.${env}.")
    public interface ExpandsFromAnotherKey extends Config {

        @DisableFeature(PREFIX)
        @DefaultValue("dev")
        String env();

        String name();

        String hostname();

        Integer port();

        String user();

        @DisableFeature(VARIABLE_EXPANSION)
        String password();
    }

    @Test
    public void testKeyExpansionFromAnotherKey() {
        ExpandsFromAnotherKey cfg = ConfigFactory.create(ExpandsFromAnotherKey.class);

        assertEquals("DEV", cfg.name());
        assertEquals("devhost", cfg.hostname());
        assertEquals(new Integer(6000), cfg.port());
        assertEquals("myuser1", cfg.user());
        assertNull(cfg.password()); // expansion is disabled on method level
    }

    @Test
    public void testKeyExpansionFromAnotherKeyWithImportOverriding() {
        ExpandsFromAnotherKey cfg = ConfigFactory.create(ExpandsFromAnotherKey.class, map("env", "uat"));

        assertEquals("UAT", cfg.name());
        assertEquals("uathost", cfg.hostname());
        assertEquals(new Integer(60020), cfg.port());
        assertEquals("myuser2", cfg.user());
        assertNull("mypass2", cfg.password()); // expansion is disabled on method level
    }

    @Sources("classpath:org/aeonbits/owner/variableexpansion/KeyExpansionExample.xml")
    @Prefix("servers.${env}.")
    public interface UseOfDefaultValueIfNotFound extends Config {

        @DisableFeature(PREFIX)
        @DefaultValue("dev")
        String env();

        @Config.Key("nonDefinedInSourceKey")
        @Config.DefaultValue("wantedValue")
        String undefinedPropInSource();

    }

    @Test
    public void testKeyExpansionAndDefaultValue() {
        UseOfDefaultValueIfNotFound cfg = ConfigFactory.create(UseOfDefaultValueIfNotFound.class);

        assertEquals("dev", cfg.env());
        assertEquals("wantedValue", cfg.undefinedPropInSource());
    }
}
