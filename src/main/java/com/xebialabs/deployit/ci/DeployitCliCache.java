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

package com.xebialabs.deployit.ci;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import com.xebialabs.deployit.ci.util.DeployitTypes;
import com.xebialabs.deployit.plugin.api.reflect.Descriptor;
import com.xebialabs.deployit.plugin.api.reflect.PropertyDescriptor;
import com.xebialabs.deployit.plugin.api.reflect.Type;
import com.xebialabs.deployit.plugin.api.udm.EmbeddedDeployable;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;

public class DeployitCliCache {

    private String deployitServerUrl;
    private String deployitClientProxyUrl;

    final LoadingCache<Credential, DeployitCliTemplate> cliTemplateCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<Credential, DeployitCliTemplate>() {
                @Override
                public DeployitCliTemplate load(Credential credential) throws Exception {
                    if (Strings.emptyToNull(deployitServerUrl) == null) {
                        throw new RuntimeException("deployit-plugin: no deployit server url, return");
                    }
                    return new DeployitCliTemplate(deployitServerUrl, deployitClientProxyUrl, credential);
                }
            });

    final LoadingCache<Credential, List<String>> embeddedResourcesCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<Credential, List<String>>() {
                @Override
                public List<String> load(Credential key) throws Exception {
                    return Lists.transform(ImmutableList.copyOf(Iterables.filter(getDeployitTypes(key).getDescriptors().getDescriptors(), new Predicate<Descriptor>() {
                        @Override
                        public boolean apply(Descriptor input) {
                            return input.getInterfaces().contains(Type.valueOf("udm.EmbeddedDeployable"));
                        }
                    })), TO_TYPE);
                }
            });

    final LoadingCache<Credential, List<String>> resourcesCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<Credential, List<String>>() {
                @Override
                public List<String> load(Credential key) throws Exception {
                    return Lists.transform(getDeployitTypes(key).getDescriptors().getDeployableResources(), TO_TYPE);
                }
            });

    final LoadingCache<Credential, List<String>> artifactsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<Credential, List<String>>() {
                @Override
                public List<String> load(Credential key) throws Exception {
                    return Lists.transform(getDeployitTypes(key).getDescriptors().getDeployableArtifacts(), TO_TYPE);
                }
            });

    private final LoadingCache<Credential, Map<String, List<String>>> indexedPropertiesCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<Credential, Map<String, List<String>>>() {
                @Override
                public Map<String, List<String>> load(Credential key) throws Exception {
                    return ImmutableMap.copyOf(getSettablePropertyNamesByType(getCliTemplate(key).getDeployitTypes()));
                }
            });

    private Map<String, List<String>> getSettablePropertyNamesByType(final DeployitTypes deployitTypes) {
        Map<String, List<String>> propertiesIndexedByMap = newHashMap(deployitTypes.getDescriptors().getPropertiesIndexedByMap());
        final Type embeddedDeployableType = deployitTypes.typeForClass(EmbeddedDeployable.class);
        for (final Descriptor descriptor : deployitTypes.getDescriptors().getDescriptors()) {
            List<String> props = propertiesIndexedByMap.get(descriptor.getType().toString());
            if (props == null) { continue; }

            List<String> filteredProps = newArrayList(Iterables.filter(props, new Predicate<String>() {
                @Override
                public boolean apply(String prop) {
                    PropertyDescriptor pd = descriptor.getPropertyDescriptor(prop);
                    return pd != null && !pd.isHidden() && !pd.getName().equals("tags") &&
                            !deployitTypes.isEmbeddedProperty(pd, embeddedDeployableType);
                }
            }));
            propertiesIndexedByMap.put(descriptor.getType().toString(), filteredProps);
        }
        return propertiesIndexedByMap;
    }

    private final static Function<Descriptor, String> TO_TYPE = new Function<Descriptor, String>() {
        public String apply(Descriptor input) {
            return input.getType().toString();
        }
    };

    public DeployitCliCache(String deployitServerUrl, String deployitClientProxyUrl) {
        this.deployitServerUrl = deployitServerUrl;
        this.deployitClientProxyUrl = deployitClientProxyUrl;
        invalidateCaches();
    }

    private void invalidateCaches() {
        resourcesCache.invalidateAll();
        artifactsCache.invalidateAll();
        indexedPropertiesCache.invalidateAll();
    }

    public DeployitCliTemplate getCliTemplate(Credential key) throws ExecutionException {
        return cliTemplateCache.get(key);
    }

    public DeployitTypes getDeployitTypes(Credential credential) {
        try {
            return getCliTemplate(credential).getDeployitTypes();
        } catch (ExecutionException e) {
            throw new RuntimeException("failing when fetching descriptors for " + credential, e);
        }
    }

    public List<String> resources(Credential credential) {
        try {
            return resourcesCache.get(credential);
        } catch (ExecutionException e) {
            throw new RuntimeException("failing when fetching resources for " + credential, e);
        }
    }

    public List<String> embeddedResources(Credential credential) {
        try {
            return embeddedResourcesCache.get(credential);
        } catch (ExecutionException e) {
            throw new RuntimeException("failing when fetching embedded resources for " + credential, e);
        }
    }

    public List<String> artifacts(Credential credential) {
        try {
            return artifactsCache.get(credential);
        } catch (ExecutionException e) {
            throw new RuntimeException("failing when fetching artifacts for " + credential, e);
        }
    }

    public Map<String, List<String>> getPropertiesIndexedByType(Credential credential) {
        try {
            return indexedPropertiesCache.get(credential);
        } catch (ExecutionException e) {
            throw new RuntimeException("failing when fetching indexedPropertiesCache for " + credential, e);
        }
    }

    public void setDeployitServerUrl(String deployitServerUrl) {
        this.deployitServerUrl = deployitServerUrl;
        invalidateCaches();
    }

    public void setDeployitClientProxyUrl(String deployitClientProxyUrl) {
        this.deployitClientProxyUrl = deployitClientProxyUrl;
        invalidateCaches();
    }
}
