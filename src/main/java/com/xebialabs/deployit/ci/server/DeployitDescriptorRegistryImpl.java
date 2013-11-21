package com.xebialabs.deployit.ci.server;

import java.util.*;
import javax.annotation.Nullable;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.common.util.concurrent.Monitor;

import com.xebialabs.deployit.booter.remote.BooterConfig;
import com.xebialabs.deployit.booter.remote.DeployitCommunicator;
import com.xebialabs.deployit.booter.remote.RemoteBooter;
import com.xebialabs.deployit.booter.remote.RemoteDescriptorRegistry;
import com.xebialabs.deployit.plugin.api.reflect.*;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.plugin.api.udm.Deployable;
import com.xebialabs.deployit.plugin.api.udm.EmbeddedDeployable;
import com.xebialabs.deployit.plugin.api.udm.artifact.FolderArtifact;
import com.xebialabs.deployit.plugin.api.udm.artifact.SourceArtifact;
import com.xebialabs.deployit.plugin.api.udm.base.*;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.collect.Sets.newLinkedHashSet;
import static java.util.EnumSet.of;

public class DeployitDescriptorRegistryImpl implements DeployitDescriptorRegistry {

    private BooterConfig booterConfig;

    private Monitor LOCK = new Monitor();
    private Iterable<Descriptor> allDeployableDescriptors;


    DeployitDescriptorRegistryImpl(BooterConfig booterConfig) {
        this.booterConfig = booterConfig;
    }

    private DeployitCommunicator getCommunicator() {
        return RemoteBooter.boot(booterConfig);
    }

    private RemoteDescriptorRegistry getDescriptorRegistry() {
        if (DescriptorRegistry.getDescriptorRegistry(booterConfig) == null) {
            getCommunicator();
        }
        return (RemoteDescriptorRegistry) DescriptorRegistry.getDescriptorRegistry(booterConfig);
    }

    @Override
    public Type typeForClass(Class<?> clazz) {
        return getDescriptorRegistry().lookupType(clazz);
    }

    @Override
    public Type typeForName(String name) {
        return getDescriptorRegistry().lookupType(name);
    }

    @Override
    public <T extends BaseConfigurationItem> T newInstance(Class<T> clazz, String name) {
        try {
            T bci = clazz.newInstance();
            bci.setId(name);
            bci.setType(typeForClass(clazz));
            return bci;
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ConfigurationItem newInstance(String type, String name) {
        Descriptor descriptor = getDescriptor(type);
        BaseConfigurationItem ci;

        if (descriptor.isAssignableTo(typeForClass(EmbeddedDeployable.class))) {
            ci = new BaseEmbeddedDeployable();
        } else if (descriptor.isAssignableTo(typeForClass(SourceArtifact.class))) {
            if (descriptor.isAssignableTo(typeForClass(FolderArtifact.class))) {
                ci = new BaseDeployableFolderArtifact();
            } else {
                ci = new BaseDeployableFileArtifact();
            }
        } else if (descriptor.isAssignableTo(typeForClass(Deployable.class))) {
            ci = new BaseDeployable();
        } else {
            ci = new BaseConfigurationItem();
        }

        ci.setId(name);
        ci.setType(descriptor.getType());
        for (PropertyDescriptor pd : descriptor.getPropertyDescriptors()) {
            if (pd.isAsContainment()) {
                switch (pd.getKind()) {
                    case LIST_OF_CI:
                        ci.setProperty(pd.getName(), newArrayList());
                        break;
                    case SET_OF_CI:
                        ci.setProperty(pd.getName(), newHashSet());
                        break;
                    default:
                        break;
                }
            }
        }
        return ci;
    }


    @Override
    public Collection<Descriptor> getDescriptors() {
        return getDescriptorRegistry().getLoadedDescriptors();
    }

    @Override
    public Descriptor getDescriptor(String type) {
        return getDescriptorRegistry().getLoadedDescriptor(type);
    }

    private Descriptor getDescriptor(Type type) {
        return getDescriptorRegistry().getLoadedDescriptor(type);
    }

    @Override
    public void setProperty(ConfigurationItem ci, String propName, String value) {
        PropertyDescriptor pd = getDescriptor(ci.getType()).getPropertyDescriptor(propName);
        pd.set(ci, convertValue(value, pd));
    }

    private Object convertValue(String val, PropertyDescriptor pd) {
        if (val == null) return null;
        switch (pd.getKind()) {
            case BOOLEAN:
                return Boolean.parseBoolean(val);
            case INTEGER:
                if (val.isEmpty()) return null;
                return Integer.parseInt(val);
            case CI:
                return convertToCiRef(val, pd);
            case SET_OF_STRING:
                return newLinkedHashSet(splitValue(val));
            case LIST_OF_STRING:
                return newArrayList(splitValue(val));
            case SET_OF_CI:
                return newLinkedHashSet(convertToCiRefs(val, pd));
            case LIST_OF_CI:
                return newArrayList(convertToCiRefs(val, pd));
            case MAP_STRING_STRING:
                return Splitter.on('&').withKeyValueSeparator("=").split(val);
            default:
                return val;
        }
    }

    private Iterable<ConfigurationItem> convertToCiRefs(String val, final PropertyDescriptor pd) {
        return FluentIterable.from(splitValue(val)).transform(new Function<String, ConfigurationItem>() {
            @Nullable
            @Override
            public ConfigurationItem apply(@Nullable final String input) {
                return convertToCiRef(input, pd);
            }
        });
    }

    private ConfigurationItem convertToCiRef(String name, PropertyDescriptor pd) {
        BaseConfigurationItem ci = new BaseConfigurationItem();
        ci.setId(name);
        ci.setType(pd.getReferencedType());
        return ci;
    }


    private Iterable<String> splitValue(String val) {
        return Splitter.on(',').trimResults().omitEmptyStrings().split(val);
    }

    private Iterable<Descriptor> getAllDeployableDescriptors() {
        LOCK.enter();
        try {
            if (allDeployableDescriptors == null) {
                Predicate<Descriptor> predicate = Predicates.or(new DescriptorPredicate(typeForName(UDM_DEPLOYABLE)),
                        new DescriptorPredicate(typeForName(UDM_EMBEDDED_DEPLOYABLE)));
                allDeployableDescriptors = FluentIterable.from(getDescriptors()).filter(predicate);
            }
        } finally {
            LOCK.leave();
        }
        return allDeployableDescriptors;
    }


    @Override
    public List<String> getDeployableArtifactTypes() {
        DescriptorPredicate predicate = new DescriptorPredicate(typeForName(UDM_ARTIFACT));
        return toSortedDeployableTypeListing(predicate);
    }

    private List<String> toSortedDeployableTypeListing(Predicate<Descriptor> descriptorsWithType) {
        return FluentIterable.from(getAllDeployableDescriptors())
                .filter(descriptorsWithType)
                .transform(DESCRIPTOR_TO_TYPE_NAME)
                .toSortedImmutableList(Ordering.natural());
    }

    @Override
    public List<String> getDeployableResourceTypes() {
        Predicate<Descriptor> predicate = Predicates.not(Predicates.<Descriptor>or(
                new DescriptorPredicate(typeForName(UDM_ARTIFACT)),
                new DescriptorPredicate(typeForName(UDM_EMBEDDED_DEPLOYABLE))));
        return toSortedDeployableTypeListing(predicate);
    }

    @Override
    public List<String> getEmbeddedDeployableTypes() {
        DescriptorPredicate predicate = new DescriptorPredicate(typeForName(UDM_EMBEDDED_DEPLOYABLE));
        return toSortedDeployableTypeListing(predicate);
    }

    @Override
    public List<String> getEditablePropertiesForDeployableType(String type) {
        final Type embeddedDeployableType = typeForName(UDM_EMBEDDED_DEPLOYABLE);
        Predicate<PropertyDescriptor> editablePropertyDescriptors = new Predicate<PropertyDescriptor>() {
            @Override
            public boolean apply(PropertyDescriptor pd) {
                return !pd.isHidden() && !pd.getName().equals("tags") && !isEmbeddedProperty(pd, embeddedDeployableType);
            }
        };
        Descriptor descriptor = getDescriptor(type);
        return FluentIterable.from(descriptor.getPropertyDescriptors())
                .filter(editablePropertyDescriptors)
                .transform(PROPERTY_DESCRIPTOR_TO_NAME)
                .toSortedImmutableList(Ordering.natural());
    }

    @Override
    public void addEmbedded(ConfigurationItem parent, ConfigurationItem embed) {
        com.xebialabs.deployit.plugin.api.reflect.Descriptor descriptor = getDescriptor(parent.getType().toString());
        for (PropertyDescriptor pd : descriptor.getPropertyDescriptors()) {
            if (isMatchingEmbeddedProperty(pd, embed.getType())) {
                Collection col = (Collection) pd.get(parent);
                if (col == null) {
                    col = pd.getKind() == PropertyKind.LIST_OF_CI ? newArrayList() : newHashSet();
                    pd.set(parent, col);
                }
                col.add(embed);
                return;
            }
        }
        throw new RuntimeException("Failed to find property that embeds " + embed + " into parent " + parent);
    }

    @Override
    public void reload() {
        LOCK.enter();
        try {
            getDescriptorRegistry().reboot(getCommunicator());
            allDeployableDescriptors = null;
        } finally {
            LOCK.leave();
        }
    }

    private boolean isEmbeddedProperty(PropertyDescriptor pd, Type embeddedDeployableType) {
        return pd.isAsContainment() && (pd.getKind() == PropertyKind.LIST_OF_CI || pd.getKind() == PropertyKind.SET_OF_CI) &&
                pd.getReferencedType().isSubTypeOf(embeddedDeployableType);
    }

    private boolean isMatchingEmbeddedProperty(PropertyDescriptor pd, Type matchType) {
        return pd.isAsContainment() && (pd.getKind() == PropertyKind.LIST_OF_CI || pd.getKind() == PropertyKind.SET_OF_CI) &&
                pd.getReferencedType().equals(matchType);
    }

    private static final Function<Descriptor, String> DESCRIPTOR_TO_TYPE_NAME = new Function<Descriptor, String>() {
        @Override
        public String apply(Descriptor input) {
            return input.getType().toString();
        }
    };

    private static final Function<PropertyDescriptor, String> PROPERTY_DESCRIPTOR_TO_NAME = new Function<PropertyDescriptor, String>() {
        @Override
        public String apply(PropertyDescriptor input) {
            return input.getName();
        }
    };

    private static class DescriptorPredicate implements Predicate<Descriptor> {

        private Type type;

        DescriptorPredicate(Type type) {
            this.type = type;
        }

        @Override
        public boolean apply(Descriptor input) {
            return input.isAssignableTo(type);
        }
    }
}
