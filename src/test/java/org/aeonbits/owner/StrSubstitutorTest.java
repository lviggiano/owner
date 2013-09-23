/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Luigi R. Viggiano
 */
public class StrSubstitutorTest {

    @Test
    public void shouldReturnNullWhenNullIsProvided() {
        Properties props = new Properties();
        StrSubstitutor substitutor = new StrSubstitutor(props);
        assertNull(substitutor.replace(null));
    }

    @Test
    public void shouldReplaceVariables() {
        Properties values = new Properties();
        values.setProperty("animal", "quick brown fox");
        values.setProperty("target", "lazy\\slow dog");
        String templateString = "The ${animal} jumped over the ${target}.";
        StrSubstitutor sub = new StrSubstitutor(values);
        String resolvedString = sub.replace(templateString);
        assertEquals("The quick brown fox jumped over the lazy\\slow dog.", resolvedString);
    }

    @Test
    public void testRecoursiveResolution() {
        Properties values = new Properties();
        values.setProperty("color", "brown");
        values.setProperty("animal", "quick ${color} fox");
        values.setProperty("target.attribute", "lazy");
        values.setProperty("target.animal", "dog");
        values.setProperty("target", "${target.attribute} ${target.animal}");
        values.setProperty("template", "The ${animal} jumped over the ${target}.");
        String templateString = "${template}";
        StrSubstitutor sub = new StrSubstitutor(values);
        String resolvedString = sub.replace(templateString);
        assertEquals("The quick brown fox jumped over the lazy dog.", resolvedString);
    }

    @Test
    public void testMissingPropertyIsReplacedWithEmptyString() {
        Properties values = new Properties() {{
            setProperty("foo", "fooValue");
            setProperty("baz", "bazValue");
        }};
        String template = "Test: ${foo} ${bar} ${baz} :Test";
        String expected = "Test: fooValue  bazValue :Test";
        String result = new StrSubstitutor(values).replace(template);
        assertEquals(expected, result);
    }

}
