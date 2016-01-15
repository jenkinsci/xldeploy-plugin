package com.xebialabs.deployit.ci.server;

import com.xebialabs.deployit.booter.remote.BooterConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DeployitDescriptorRegistrySetPropertyTest {

    @Mock
    private BooterConfig booterConfig;

    private DeployitDescriptorRegistryImpl deployitDescriptorRegistry;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        deployitDescriptorRegistry = new DeployitDescriptorRegistryImpl(booterConfig);
    }

    @Test
    public void shouldReturnMapWithKeyValueWithNoSpecialCharacter() {
        String testValue = "key1=value1&key2=value2&key3=value3";
        Map<String, String> returnedMap = (Map<String, String>) deployitDescriptorRegistry.convertToMap(testValue);
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
        Map<String, String> returnedMap = (Map<String, String>) deployitDescriptorRegistry.convertToMap(testValue);
        assertThat(returnedMap.size(), is(3));
        assertMapKeyValues("key1&key11", "value1", returnedMap);
        assertMapKeyValues("key2", "value2=value22", returnedMap);
        assertMapKeyValues("key3", "value3", returnedMap);
    }

    @Test
    public void shouldReturnMapWithKeyValueWithNoValues() {
        String testValue = "key1\\&key11=\\=value1&key2=value2\\=value22&key3=";
        Map<String, String> returnedMap = (Map<String, String>) deployitDescriptorRegistry.convertToMap(testValue);
        assertThat(returnedMap.size(), is(3));
        assertMapKeyValues("key1&key11", "=value1", returnedMap);
        assertMapKeyValues("key2", "value2=value22", returnedMap);
        assertMapKeyValues("key3", "", returnedMap);
    }
}
