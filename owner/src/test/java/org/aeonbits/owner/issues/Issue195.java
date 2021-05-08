package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Issue195 {

    private static final String KEY = "some.key";

    public interface MyConfig extends Config {
        @Key(KEY)
        @DefaultValue("1")
        Integer getSomeValue();
    }

    @Test
    public void testConfigImportMapWithNonStringValue() throws Exception {
        Object[] illegalValues = new Object[]{new Integer("42"), new StringBuffer("42"),
                new StringBuilder("42")};

        for (Object k : illegalValues) {
            HashMap<String, Object> propsMapWithIllegalValue = new HashMap<String, Object>();
            propsMapWithIllegalValue.put(KEY, k);
            try {
                ConfigFactory.create(MyConfig.class, propsMapWithIllegalValue);
                fail("A non-string value should result in an exception");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains(KEY));
            }
        }
        // Make sure that using a string (legal value) actually works too.
        HashMap<String, Object> propsMapWithLegalValue = new HashMap<String, Object>();
        propsMapWithLegalValue.put(KEY, "42");
        MyConfig config = ConfigFactory.create(MyConfig.class, propsMapWithLegalValue);
        assertEquals(new Integer(42), config.getSomeValue());
    }
}