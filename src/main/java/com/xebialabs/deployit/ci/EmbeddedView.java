package com.xebialabs.deployit.ci;

import java.util.List;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.xebialabs.deployit.ci.server.DeployitServer;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractProject;
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

    @Override
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
                @QueryParameter(value = "credential") @RelativePath(value = "../..") String credentialNewEmbeddeds,
                @AncestorInPath AbstractProject<?,?> project)
        {
            String creds = credentialExistingEmbeddeds != null ? credentialExistingEmbeddeds : credentialNewEmbeddeds;
            Credential credential = RepositoryUtils.retrieveOverridingCredentialFromProject(project);
            DeployitServer deployitServer = RepositoryUtils.getDeployitServer(creds, credential, project);
            return of(RepositoryUtils.getAllEmbeddedResourceTypes(deployitServer));
        }
    }
}
