package com.xebialabs.deployit.ci;

import com.xebialabs.deployit.ci.server.DeployitDescriptorRegistry;
import com.xebialabs.deployit.ci.server.DeployitServer;
import com.xebialabs.deployit.ci.util.ListBoxModels;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.util.Collection;

import static com.xebialabs.deployit.ci.PackageProperty.PackagePropertyDescriptor.ONLY_SIMPLE_EDITABLE_PROPERTIES;

public class DeploymentProperty extends NameValuePair {

    @DataBoundConstructor
    public DeploymentProperty(String propertyName, String propertyValue) {
        super(propertyName, propertyValue);
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<NameValuePair> {

        public static String PROPERTY_TYPE = "udm.DeployedApplication";

        @Override
        public String getDisplayName() {
            return DeploymentProperty.class.getSimpleName();
        }

        public ListBoxModel doFillPropertyNameItems(
                @QueryParameter(value = "credential") @RelativePath(value = "../..") String credentialExistingProps,
                @QueryParameter(value = "credential") @RelativePath(value = "..") String credentialNewProps,
                @AncestorInPath AbstractProject project) {
            String creds = credentialExistingProps != null ? credentialExistingProps : credentialNewProps;
            Credential overridingCredential = RepositoryUtils.retrieveOverridingCredentialFromProject(project);
            // load type descriptor
            DeployitServer deployitServer = RepositoryUtils.getDeployitServer(creds, overridingCredential);
            DeployitDescriptorRegistry descriptorRegistry = deployitServer.getDescriptorRegistry();
            Collection<String> properties = descriptorRegistry.getPropertiesForDeployableType(PROPERTY_TYPE, ONLY_SIMPLE_EDITABLE_PROPERTIES);
            return ListBoxModels.of(properties);
        }

    }

}
