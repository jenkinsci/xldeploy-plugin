package com.xebialabs.deployit.ci;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;

import java.util.Collection;

import javax.annotation.Nullable;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.google.common.base.Predicate;

import com.xebialabs.deployit.ci.server.DeployitDescriptorRegistry;
import com.xebialabs.deployit.ci.server.DeployitServer;
import com.xebialabs.deployit.ci.util.ListBoxModels;
import com.xebialabs.deployit.plugin.api.reflect.PropertyDescriptor;
import com.xebialabs.deployit.plugin.api.reflect.PropertyKind;

public class PackageProperty extends NameValuePair {

    @DataBoundConstructor
    public PackageProperty(final String propertyName, final String propertyValue) {
        super(propertyName, propertyValue);
    }

    @Extension
    public static class PackagePropertyDescriptor extends Descriptor<NameValuePair> {
        public static String PACKAGE_TYPE = "udm.DeploymentPackage";

        public PackagePropertyDescriptor(final Class<? extends NameValuePair> clazz) {
            super(clazz);
        }

        public static final Predicate<PropertyDescriptor> ONLY_SIMPLE_EDITABLE_PROPERTIES = new Predicate<PropertyDescriptor>() {
            @Override
            public boolean apply(@Nullable final PropertyDescriptor pd) {
                return !pd.isHidden() && (pd.getKind().isSimple() ||
                        PropertyKind.LIST_OF_STRING == pd.getKind() ||
                        PropertyKind.SET_OF_STRING == pd.getKind() ||
                        PropertyKind.MAP_STRING_STRING == pd.getKind());
            }
        };

        public PackagePropertyDescriptor() {
            super(PackageProperty.class);
        }

        @Override
        public String getDisplayName() {
            return "PackageProperty";
        }

        public ListBoxModel doFillPropertyNameItems(
                @QueryParameter(value = "credential") @RelativePath(value = "../..") String credentialExistingProps,
                @QueryParameter(value = "credential") @RelativePath(value = "..") String credentialNewProps,
                @AncestorInPath AbstractProject project)
        {
            String creds = credentialExistingProps != null ? credentialExistingProps : credentialNewProps;
            String type = PACKAGE_TYPE;
            Credential overridingCredential = RepositoryUtils.retrieveOverridingCredentialFromProject(project);
            // load type descriptor
            DeployitServer deployitServer = RepositoryUtils.getDeployitServer(creds, overridingCredential );
            DeployitDescriptorRegistry descriptorRegistry = deployitServer.getDescriptorRegistry();
            descriptorRegistry.typeForName(PACKAGE_TYPE);
            Collection<String> properties = descriptorRegistry.getPropertiesForDeployableType(type, ONLY_SIMPLE_EDITABLE_PROPERTIES);
            return ListBoxModels.of(properties);
        }

    }
}
