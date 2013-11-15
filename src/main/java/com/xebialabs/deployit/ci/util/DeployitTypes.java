/**
 * Copyright (c) 2013, XebiaLabs B.V., All rights reserved.
 *
 *
 * The Deployit plugin for Jenkins is licensed under the terms of the GPLv2
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

package com.xebialabs.deployit.ci.util;

import java.util.Collection;
import java.util.Map;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import com.xebialabs.deployit.booter.remote.BooterConfig;
import com.xebialabs.deployit.client.Descriptors;
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

public class DeployitTypes {
    private final Map<String, Descriptor> descriptorsByType;
    private Descriptors descriptors;

    //The CliTemplate gets cached for 10 minutes and can get reused by other jobs depending on how long Jenkins
    //caches the DeployitNotifier Jenkin's descriptor.
    //The perform method runs in a different classloader. The DescriptorRegistry gets loaded on the first call and works fine.
    //When Jenkins reuses the DeployitNotifier descriptor for the next job, the DescriptorRegistry is empty.
    //This causes the Type.valueOf static methods to fail.
    //Hold onto a reference to registry.  This plugin will handle all type,descriptor info via DeployitTypes class.
    private DescriptorRegistry registry;
    private BooterConfig registryId;

    public DeployitTypes(Descriptors descriptors, DescriptorRegistry registry, final BooterConfig config) {
        this.descriptors = descriptors;
        this.registry = registry;
        this.registryId = config;
        Builder<String, Descriptor> typesToDescriptors = ImmutableMap.builder();
        for (Descriptor descriptor : descriptors.getDescriptors()) {
            typesToDescriptors.put(descriptor.getType().toString(), descriptor);
        }
        descriptorsByType = typesToDescriptors.build();
    }

    public BooterConfig getRegistryId() {
        return registryId;
    }

    public Descriptor getDescriptor(String type) {
        return descriptorsByType.get(type);
    }

    public Collection<PropertyDescriptor> getPropertyDescriptors(String type) {
        return getDescriptor(type).getPropertyDescriptors();
    }

    public Descriptors getDescriptors() {
        return descriptors;
    }

    public Type typeForClass(Class<?> clazz) {
        return registry.lookupType(clazz);
    }

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

    public boolean isEmbeddedProperty(PropertyDescriptor pd, Type embeddedType) {
        return pd.isAsContainment() &&
                (pd.getKind() == PropertyKind.LIST_OF_CI || pd.getKind() == PropertyKind.SET_OF_CI) &&
                pd.getReferencedType().equals(embeddedType);
    }

    public void addEmbedded(ConfigurationItem parent, ConfigurationItem embed) {
        Descriptor descriptor = getDescriptor(parent.getType().toString());
        for (PropertyDescriptor pd : descriptor.getPropertyDescriptors()) {
            if (!isEmbeddedProperty(pd, embed.getType())) { continue; }
            Collection col = (Collection) pd.get(parent);
            if (col == null) {
                col = pd.getKind() == PropertyKind.LIST_OF_CI ? newArrayList() : newHashSet();
                pd.set(parent, col);
            }
            col.add(embed);
            return;
        }
        throw new RuntimeException("Failed to find property that embeds " + embed + " into parent " + parent);
    }

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
        return ci;
    }


    public void setProperty(ConfigurationItem ci, String propName, String value) {
        PropertyDescriptor pd = getDescriptor(ci.getType().toString()).getPropertyDescriptor(propName);
        pd.set(ci, convertValue(value, pd.getKind()));
    }

    public static String getNameFromId(String id) {
        String[] nameParts = id.split("/");
        return nameParts[nameParts.length - 1];
    }

    private Object convertValue(String val, PropertyKind kind) {
        if (val == null) return null;
        switch (kind) {
            case BOOLEAN:
                return Boolean.parseBoolean(val);
            case INTEGER:
                if (val.isEmpty()) return null;
                return Integer.parseInt(val);
            case SET_OF_STRING:
                return newLinkedHashSet(splitValue(val));
            case LIST_OF_STRING:
                return newArrayList(splitValue(val));
            case MAP_STRING_STRING:
                return Splitter.on('&').withKeyValueSeparator("=").split(val);
            default:
                return val;
        }
    }

    private static Iterable<String> splitValue(String val) {
        return Splitter.on(',').trimResults().omitEmptyStrings().split(val);
    }
}