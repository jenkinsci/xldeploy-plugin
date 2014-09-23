package com.xebialabs.deployit.ci.server;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Predicate;

import com.xebialabs.deployit.booter.remote.DeployitCommunicator;
import com.xebialabs.deployit.plugin.api.reflect.Descriptor;
import com.xebialabs.deployit.plugin.api.reflect.PropertyDescriptor;
import com.xebialabs.deployit.plugin.api.reflect.Type;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.plugin.api.udm.base.BaseConfigurationItem;

public interface DeployitDescriptorRegistry {
    String UDM_ENVIRONMENT = "udm.Environment";
    String UDM_APPLICATION = "udm.Application";
    String UDM_ARTIFACT = "udm.Artifact";
    String UDM_DEPLOYABLE = "udm.Deployable";
    String UDM_EMBEDDED_DEPLOYABLE = "udm.EmbeddedDeployable";

    Type typeForClass(Class<?> clazz);

    Type typeForName(String name);

    <T extends BaseConfigurationItem> T newInstance(Class<T> clazz, String name);

    ConfigurationItem newInstance(String type, String name);

    Collection<Descriptor> getDescriptors();

    Descriptor getDescriptor(String type);

    void setProperty(ConfigurationItem ci, String propName, String value);

    List<String> getDeployableArtifactTypes();

    List<String> getDeployableResourceTypes();

    List<String> getEmbeddedDeployableTypes();

    List<String> getEditablePropertiesForDeployableType(String type);

    List<String> getPropertiesForDeployableType(String type, Predicate<PropertyDescriptor> propertyPredicate);

    void addEmbedded(ConfigurationItem parent, ConfigurationItem embed);

    void reload();

    public DeployitCommunicator getCommunicator();
}
