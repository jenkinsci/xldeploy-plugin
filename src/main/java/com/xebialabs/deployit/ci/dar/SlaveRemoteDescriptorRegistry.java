package com.xebialabs.deployit.ci.dar;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import com.xebialabs.deployit.booter.remote.BooterConfig;
import com.xebialabs.deployit.plugin.api.reflect.Descriptor;
import com.xebialabs.deployit.plugin.api.reflect.DescriptorRegistry;
import com.xebialabs.deployit.plugin.api.reflect.DescriptorRegistryId;
import com.xebialabs.deployit.plugin.api.reflect.Type;

public class SlaveRemoteDescriptorRegistry extends DescriptorRegistry {

    private final Map<Type, Descriptor> descriptors = Maps.newHashMap();

    private final Multimap<Type, Type> subtypes = HashMultimap.create();


    protected SlaveRemoteDescriptorRegistry(final DescriptorRegistryId id) {
        super(id);
    }

    @Override
    protected boolean isLocal() {
        return false;
    }

    @Override
    protected Collection<Descriptor> _getDescriptors() {
        return descriptors.values();
    }

    @Override
    protected Collection<Type> _getSubtypes(Type supertype) {
        return subtypes.get(supertype);
    }

    @Override
    protected Descriptor _getDescriptor(Type type) {
        return descriptors.get(type);
    }

    @Override
    protected boolean _exists(Type type) {
        return descriptors.containsKey(type);
    }

    public static void boot(List<Descriptor> descriptors, BooterConfig booterConfig) {

        SlaveRemoteDescriptorRegistry registry = new SlaveRemoteDescriptorRegistry(booterConfig);
        DescriptorRegistry.add(registry);
        for (Descriptor descriptor : descriptors) {
            registry.register(descriptor);
        }
    }

    private void register(Descriptor descriptor) {
        descriptors.put(descriptor.getType(), descriptor);
        for (Type type : descriptor.getSuperClasses()) {
            subtypes.put(type, descriptor.getType());
        }
        for (Type type : descriptor.getInterfaces()) {
            subtypes.put(type, descriptor.getType());
        }
    }
}
