package com.xebialabs.deployit.ci;

import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.RelativePath;
import hudson.util.ListBoxModel;

import static com.xebialabs.deployit.ci.util.ListBoxModels.of;

public class EmbeddedView extends DeployableView {

    public String parentName;

    @DataBoundConstructor
    public EmbeddedView(String type, String name, String parentName, List<NameValuePair> properties) {
        super(type, name, "", properties);
        this.parentName = parentName;
    }

    public String getParentName() {
        return parentName;
    }

    public String getFullyQualifiedName() {
        return getParentName() + "/" + name;
    }

    @Extension
    public static final class DescriptorImpl extends DeployableViewDescriptor {

        @Override
        public String getDisplayName() {
            return "Embedded";
        }

        public ListBoxModel doFillTypeItems(
                @QueryParameter(value = "credential") @RelativePath(value = "..") String credentialExistingEmbeddeds,
                @QueryParameter(value = "credential") @RelativePath(value = "../..") String credentialNewEmbeddeds
        ) {
            String creds = credentialExistingEmbeddeds != null ? credentialExistingEmbeddeds : credentialNewEmbeddeds;
            return of(getDeployitDescriptor().getAllEmbeddedResourceTypes(creds));
        }
    }
}
