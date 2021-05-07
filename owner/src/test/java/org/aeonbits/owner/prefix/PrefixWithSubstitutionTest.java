package org.aeonbits.owner.prefix;

import static org.junit.Assert.assertEquals;

import org.aeonbits.owner.Config;
import static org.aeonbits.owner.Config.Prefix;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

public class PrefixWithSubstitutionTest {

    @Prefix("testprefix.")
    public interface ConfigWithSubstitutionFile extends Config {
        String story();
    }

    @Test
    public void testConfigWithSubstitutionFile() {
        ConfigWithSubstitutionFile conf = ConfigFactory.create(ConfigWithSubstitutionFile.class);
        assertEquals("The quick brown fox jumped over the lazy dog", conf.story());
    }

    @Test
    public void testConfigWithSubstitutionAnnotation() {
        ConfigWithSubstitutionAnnotations conf = ConfigFactory.create(ConfigWithSubstitutionAnnotations.class);
        assertEquals("The quick brown fox jumped over the lazy dog", conf.story());
    }

    @Test
    public void testSubInterface() {
        ConfigWithSubtstitutionAnnotationsSubInterface conf = ConfigFactory.create(ConfigWithSubtstitutionAnnotationsSubInterface.class);
        assertEquals("Please grandma, tell me the story of 'The quick brown fox jumped over the lazy dog'", conf.tellmeTheStory());
    }

    public static interface ConfigWithSubtstitutionAnnotationsSubInterface extends ConfigWithSubstitutionAnnotations {
        @DefaultValue("grandma")
        public String teller();

        @DefaultValue("Please ${teller}, tell me the story of '${story}'")
        public String tellmeTheStory();
    }

    public static interface ConfigWithSubstitutionAnnotations extends Config {

        @DefaultValue("The ${animal} jumped over the ${target}")
        String story();

        @DefaultValue("quick ${color} fox")
        String animal();

        @DefaultValue("${target.attribute} dog")
        String target();

        @Key("target.attribute")
        @DefaultValue("lazy")
        String targetAttribute();

        @DefaultValue("brown")
        String color();
    }
}
