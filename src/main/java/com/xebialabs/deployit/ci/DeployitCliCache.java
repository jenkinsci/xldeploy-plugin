package com.xebialabs.deployit.ci;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import com.xebialabs.deployit.client.DeployitCli;
import com.xebialabs.deployit.plugin.api.reflect.Descriptor;

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

    final LoadingCache<Credential, List<String>> resourcesCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<Credential, List<String>>() {
                @Override
                public List<String> load(Credential key) throws Exception {
                    return Lists.transform(getCliTemplate(key).perform(new DeployitCliCallback<List<Descriptor>>() {
                        public List<Descriptor> call(DeployitCli cli) {
                            return cli.getDescriptors().getDeployableResources();
                        }
                    }), TO_TYPE);
                }
            });

    final LoadingCache<Credential, List<String>> artifactsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<Credential, List<String>>() {
                @Override
                public List<String> load(Credential key) throws Exception {
                    return Lists.transform(getCliTemplate(key).perform(new DeployitCliCallback<List<Descriptor>>() {
                        public List<Descriptor> call(DeployitCli cli) {
                            return cli.getDescriptors().getDeployableArtifacts();
                        }
                    }), TO_TYPE);
                }
            });

    private final LoadingCache<Credential, Map<String, List<String>>> indexedPropertiesCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<Credential, Map<String, List<String>>>() {
                @Override
                public Map<String, List<String>> load(Credential key) throws Exception {
                    return ImmutableMap.copyOf(getCliTemplate(key).perform(new DeployitCliCallback<Map<String, List<String>>>() {
                        public Map<String, List<String>> call(DeployitCli cli) {
                            return cli.getDescriptors().getPropertiesIndexedByMap();
                        }
                    }));
                }
            });

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

    public List<String> resources(Credential credential) {
        try {
            return resourcesCache.get(credential);
        } catch (ExecutionException e) {
            throw new RuntimeException("failing when fetching resources for " + credential, e);
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
