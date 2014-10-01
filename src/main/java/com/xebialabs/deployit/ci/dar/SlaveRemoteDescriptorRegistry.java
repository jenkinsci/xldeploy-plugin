package com.xebialabs.deployit.ci.dar;

import java.util.ArrayList;
import java.util.Collection;

import com.xebialabs.deployit.booter.remote.BooterConfig;
import com.xebialabs.deployit.booter.remote.RemoteDescriptorRegistry;
import com.xebialabs.deployit.plugin.api.reflect.Descriptor;
import com.xebialabs.deployit.plugin.api.reflect.DescriptorRegistry;
import com.xebialabs.deployit.plugin.api.reflect.DescriptorRegistryId;

public class SlaveRemoteDescriptorRegistry extends RemoteDescriptorRegistry {

    protected SlaveRemoteDescriptorRegistry(final DescriptorRegistryId id) {
        super(id);
    }

    public synchronized static void boot(Collection<Descriptor> descriptors, BooterConfig booterConfig) {
        SlaveRemoteDescriptorRegistry registry = new SlaveRemoteDescriptorRegistry(booterConfig);
        DescriptorRegistry.remove(booterConfig);
        DescriptorRegistry.add(registry);
        registry.reboot(new ArrayList<Descriptor>(descriptors));
    }
}
