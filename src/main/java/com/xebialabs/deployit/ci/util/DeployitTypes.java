package com.xebialabs.deployit.ci.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.xebialabs.deployit.client.Descriptors;
import com.xebialabs.deployit.plugin.api.reflect.Descriptor;
import com.xebialabs.deployit.plugin.api.reflect.PropertyDescriptor;

import java.util.Collection;
import java.util.Map;

public class DeployitTypes {
    private final Map<String, Descriptor> descriptors;

    public DeployitTypes(Descriptors descriptorList) {
        Builder<String, Descriptor> typesToDescriptors = ImmutableMap.builder();
        for (Descriptor descriptor : descriptorList.getDescriptors()) {
            typesToDescriptors.put(descriptor.getType().toString(), descriptor);
        }
        descriptors = typesToDescriptors.build();
    }

    public Descriptor getDescriptor(String type) {
        return descriptors.get(type);
    }

    public Collection<PropertyDescriptor> getPropertyDescriptors(String type) {
        return getDescriptor(type).getPropertyDescriptors();
    }
}