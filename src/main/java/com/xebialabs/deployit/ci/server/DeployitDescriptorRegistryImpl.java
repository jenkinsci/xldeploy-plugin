/**
 * Copyright (c) 2014, XebiaLabs B.V., All rights reserved.
 *
 *
 * The XL Deploy plugin for Jenkins is licensed under the terms of the GPLv2
 * <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most XebiaLabs Libraries.
 * There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
 * this software, see the FLOSS License Exception
 * <https://github.com/jenkinsci/deployit-plugin/blob/master/LICENSE>.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation; version 2
 * of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
 * Floor, Boston, MA 02110-1301  USA
 */

package com.xebialabs.deployit.ci.server;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.Monitor;

import com.xebialabs.deployit.booter.remote.BooterConfig;
import com.xebialabs.deployit.booter.remote.DeployitCommunicator;
import com.xebialabs.deployit.booter.remote.RemoteBooter;
import com.xebialabs.deployit.booter.remote.RemoteDescriptor;
import com.xebialabs.deployit.booter.remote.RemoteDescriptorRegistry;
import com.xebialabs.deployit.booter.remote.RemotePropertyDescriptor;
import com.xebialabs.deployit.ci.DeployitPluginException;
import com.xebialabs.deployit.plugin.api.reflect.Descriptor;
import com.xebialabs.deployit.plugin.api.reflect.DescriptorRegistry;
import com.xebialabs.deployit.plugin.api.reflect.PropertyDescriptor;
import com.xebialabs.deployit.plugin.api.reflect.PropertyKind;
import com.xebialabs.deployit.plugin.api.reflect.Type;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.plugin.api.udm.Deployable;
import com.xebialabs.deployit.plugin.api.udm.DeploymentPackage;
import com.xebialabs.deployit.plugin.api.udm.EmbeddedDeployable;
import com.xebialabs.deployit.plugin.api.udm.Version;
import com.xebialabs.deployit.plugin.api.udm.artifact.FolderArtifact;
import com.xebialabs.deployit.plugin.api.udm.artifact.SourceArtifact;
import com.xebialabs.deployit.plugin.api.udm.base.BaseConfigurationItem;
import com.xebialabs.deployit.plugin.api.udm.base.BaseDeployable;
import com.xebialabs.deployit.plugin.api.udm.base.BaseDeployableFileArtifact;
import com.xebialabs.deployit.plugin.api.udm.base.BaseDeployableFolderArtifact;
import com.xebialabs.deployit.plugin.api.udm.base.BaseEmbeddedDeployable;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.collect.Sets.newLinkedHashSet;

public class DeployitDescriptorRegistryImpl implements DeployitDescriptorRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(DeployitDescriptorRegistryImpl.class);
    private BooterConfig booterConfig;

    private Monitor LOCK = new Monitor();
    private Iterable<Descriptor> allDeployableDescriptors;

    private DeployitCommunicator communicator;

    DeployitDescriptorRegistryImpl(BooterConfig booterConfig) {
        this.booterConfig = booterConfig;
    }

    @Override
    public DeployitCommunicator getCommunicator() {
        LOCK.enter();
        try {
            if (null == communicator) {
                try {
                    communicator = RemoteBooter.getCommunicator(booterConfig);
                    LOG.debug("Reusing existing communicator for config: {}.", safeBooterConfigKey());
                } catch (IllegalStateException ex) {
                    LOG.debug("No communicator found for config: {}. Creating new DeployitCommunicator.", safeBooterConfigKey());
                    DescriptorRegistry.remove(booterConfig);
                    communicator = RemoteBooter.boot(booterConfig);
                }

                fixVersionDepl6949();
            }
        } finally {
            LOCK.leave();
        }

        return communicator;
    }

    private String safeBooterConfigKey() {
        String safePassword = booterConfig.getPassword().replaceAll(".", "*");
        return BooterConfig.builder()
            .withProtocol(booterConfig.getProtocol())
            .withCredentials(booterConfig.getUsername(), safePassword)
            .withHost(booterConfig.getHost())
            .withPort(booterConfig.getPort())
            .withContext(booterConfig.getContext())
            .withProxyHost(booterConfig.getProxyHost())
            .withProxyPort(booterConfig.getProxyPort())
            .build()
            .getKey();
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

    /**
     * This method is only used when we create Dar on a remote side (ie jenkins plugin).
     * TODO: Once the DarPackager and ManifestWriter are rewritten and start to accept generic CIs (DEPL-6925)
     * this method should be removed.
     */
    @Override
    public <T extends BaseConfigurationItem> T newInstance(Class<T> clazz, String id) {
        Type type = typeForClass(clazz);
        try {
            T bci = clazz.newInstance();
            bci.setId(id);
            bci.setType(type);
            initSyntheticProperties(bci, type);

            return bci;
        } catch (Throwable t) {
            String msg = String.format("Unable to create CI class '%s' with id: '%s'", clazz.toString(), id);
            throw new DeployitPluginException(msg, t);
        }
    }

    private <T extends BaseConfigurationItem> void initSyntheticProperties(T bci, Type type) {
        prefillPropertiesWithDefaultValues(bci, type);
    }

    private <T extends BaseConfigurationItem> void prefillPropertiesWithDefaultValues(T bci, Type type) {
        Collection<PropertyDescriptor> propertyDescriptors = type.getDescriptor().getPropertyDescriptors();
        // properties with default values
        for (PropertyDescriptor pd : propertyDescriptors) {
            Object defaultValue = pd.getDefaultValue();
            // hack for boolean
            if (defaultValue == null && PropertyKind.BOOLEAN == pd.getKind()) {
                defaultValue = false;
            }
            if (defaultValue != null) {
                bci.setProperty(pd.getName(), defaultValue);
            }
        }
    }

    // DEPL-6949: add properties required by the DarPackager/ManifestWriter even if they don't exist on XLD
    private void fixVersionDepl6949() {
        // monkey patching "udm.Version" on client side
        Type type = typeForClass(DeploymentPackage.class);
        if (type.isSubTypeOf(Type.valueOf(Version.class))) {
            addMissingPropertyDescriptor(type, "ignoreUndefinedPropertiesInManifest", PropertyKind.BOOLEAN, "true", true);
            addMissingPropertyDescriptor(type, "exportAllPasswords", PropertyKind.BOOLEAN, "false", true);
            addMissingPropertyDescriptor(type, "exportOnlyPasswordPlaceholders", PropertyKind.BOOLEAN, "true", true);
        }
    }

    private void addMissingPropertyDescriptor(Type parentType, String name, PropertyKind kind, String defaultValue, boolean hidden) {
        RemoteDescriptor descriptor = (RemoteDescriptor) parentType.getDescriptor();
        RemotePropertyDescriptor pd = (RemotePropertyDescriptor) descriptor.getPropertyDescriptor(name);
        Collection<PropertyDescriptor> propertyDescriptors = descriptor.getPropertyDescriptors();
        if (null == pd) {
            pd = new RemotePropertyDescriptor();
            pd.setKind(kind);
            pd.setDefaultValue(defaultValue);
            pd.setFqn(parentType.toString() + "." + name);
            pd.setName(name);
            // there is no way to set hidden
            if (hidden) {
                pd.setHidden();
            }
        }
        propertyDescriptors.add(pd);
        descriptor.setPropertyDescriptors(propertyDescriptors);
    }

    @Override
    public ConfigurationItem newInstance(String typeName, String name) {
        Type type = getDescriptorRegistry().lookupType(typeName);
        ConfigurationItem ci = newInstance(type, name);
        return ci;
    }

    private ConfigurationItem newInstance(Type type, String id) {
        try {
            RemoteDescriptor remoteDescriptor = (RemoteDescriptor) getDescriptor(type);
            // T ci = remoteDescriptor.newInstance(id);
            BaseConfigurationItem ci;

            if (remoteDescriptor.isAssignableTo(typeForClass(EmbeddedDeployable.class))) {
                ci = new BaseEmbeddedDeployable();
            } else if (remoteDescriptor.isAssignableTo(typeForClass(SourceArtifact.class))) {
                if (remoteDescriptor.isAssignableTo(typeForClass(FolderArtifact.class))) {
                    ci = new BaseDeployableFolderArtifact();
                } else {
                    ci = new BaseDeployableFileArtifact();
                }
            } else if (remoteDescriptor.isAssignableTo(typeForClass(Deployable.class))) {
                ci = new BaseDeployable();
            } else {
                ci = new BaseConfigurationItem();
            }
            ci.setId(id);
            ci.setType(type);

            for (PropertyDescriptor pd : remoteDescriptor.getPropertyDescriptors()) {
                if (pd.isAsContainment()) {
                    String propertyName = pd.getName();
                    if (null == ci.getProperty(propertyName)) {
                        switch (pd.getKind()) {
                        case LIST_OF_CI:
                            ci.setProperty(propertyName, newArrayList());
                            break;
                        case SET_OF_CI:
                            ci.setProperty(propertyName, newHashSet());
                            break;
                        default:
                            break;
                        }
                    }
                }
            }

            initSyntheticProperties(ci, type);

            return ci;
        } catch (Throwable e) {
            String errorMsg = String.format("Unable to instantiate CI '%s' with id '%s'. %s", type, id, e.getMessage());
            throw new RuntimeException(errorMsg, e);
        }
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
                .toSortedList(Ordering.natural());
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
        return getPropertiesForDeployableType(type, editablePropertyDescriptors);
    }

    @Override
    public List<String> getPropertiesForDeployableType(String type, Predicate<PropertyDescriptor> propertyPredicate) {
        Descriptor descriptor = getDescriptor(type);
        return FluentIterable.from(descriptor.getPropertyDescriptors())
                .filter(propertyPredicate)
                .transform(PROPERTY_DESCRIPTOR_TO_NAME)
                .toSortedList(Ordering.natural());
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
            LOG.warn("About to reload descriptor registry for config: {}.", safeBooterConfigKey());
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
