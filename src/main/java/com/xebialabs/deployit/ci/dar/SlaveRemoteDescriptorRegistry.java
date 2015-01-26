package com.xebialabs.deployit.ci.dar;

import java.util.ArrayList;
import java.util.Collection;

import com.xebialabs.deployit.booter.remote.BooterConfig;
import com.xebialabs.deployit.booter.remote.RemoteDescriptorRegistry;
import com.xebialabs.deployit.ci.Versioned;
import com.xebialabs.deployit.plugin.api.reflect.Descriptor;
import com.xebialabs.deployit.plugin.api.reflect.DescriptorRegistry;
import com.xebialabs.deployit.plugin.api.reflect.DescriptorRegistryId;

public class SlaveRemoteDescriptorRegistry extends RemoteDescriptorRegistry implements Versioned {

    private String version;

    protected SlaveRemoteDescriptorRegistry(final DescriptorRegistryId id) {
        super(id);
    }

    public SlaveRemoteDescriptorRegistry(BooterConfig booterConfig, String registryVersion) {
        this(booterConfig);
        this.version = registryVersion;
    }

    public synchronized static void boot(Collection<Descriptor> descriptors, BooterConfig booterConfig, String registryVersion) {
        SlaveRemoteDescriptorRegistry registry = new SlaveRemoteDescriptorRegistry(booterConfig, registryVersion);
        DescriptorRegistry.remove(booterConfig);
        DescriptorRegistry.add(registry);
        registry.reboot(new ArrayList<Descriptor>(descriptors));
    }

    public String getVersion() {
        return version;
    }
}
