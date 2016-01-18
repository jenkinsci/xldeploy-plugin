package com.xebialabs.deployit.ci.Util;

import com.xebialabs.deployit.ci.util.Strings2;
import org.junit.Test;

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class Strings2Test {

    @Test
    public void shouldReturnMapWithKeyValueWithNoSpecialCharacter() {
        String testValue = "key1=value1&key2=value2&key3=value3";
        Map<String, String> returnedMap = (Map<String, String>) Strings2.convertToMap(testValue);
        assertThat(returnedMap.size(), is(3));
        assertMapKeyValues("key1", "value1", returnedMap);
        assertMapKeyValues("key2", "value2", returnedMap);
        assertMapKeyValues("key3", "value3", returnedMap);
    }

    private void assertMapKeyValues(String testKey, String testValue, Map<String, String> map) {
        assertTrue(map.containsKey(testKey));
        assertThat(map.get(testKey), is(testValue));
    }

    @Test
    public void shouldReturnMapWithKeyValueWithSpecialCharacter() {
        String testValue = "key1\\&key11=value1&key2=value2\\=value22&key3=value3";
        Map<String, String> returnedMap = (Map<String, String>) Strings2.convertToMap(testValue);
        assertThat(returnedMap.size(), is(3));
        assertMapKeyValues("key1&key11", "value1", returnedMap);
        assertMapKeyValues("key2", "value2=value22", returnedMap);
        assertMapKeyValues("key3", "value3", returnedMap);
    }

    @Test
    public void shouldReturnMapWithKeyValueWithNoValues() {
        String testValue = "key1\\&key11=\\=value1&key2=value2\\=value22&key3=";
        Map<String, String> returnedMap = (Map<String, String>) Strings2.convertToMap(testValue);
        assertThat(returnedMap.size(), is(3));
        assertMapKeyValues("key1&key11", "=value1", returnedMap);
        assertMapKeyValues("key2", "value2=value22", returnedMap);
        assertMapKeyValues("key3", "", returnedMap);
    }
}