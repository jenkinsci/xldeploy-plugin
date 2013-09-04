package com.xebialabs.deployit.ci;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.xebialabs.deployit.client.DeploymentOptions;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.xebialabs.deployit.ci.util.ListBoxModels.emptyModel;
import static com.xebialabs.deployit.ci.util.ListBoxModels.of;


public class JenkinsDeploymentOptions extends DeploymentOptions implements Describable<JenkinsDeploymentOptions> {

    public final String environment;

    @Deprecated // The options has been moved to the main section @see DeployitNotifier
    public final boolean verbose;

    public final VersionKind versionKind;
    public String version;

    @DataBoundConstructor
    public JenkinsDeploymentOptions(String environment, VersionKind versionKind, boolean generateDeployedOnUpgrade, boolean skipMode, boolean testMode, boolean rollbackOnError, boolean verbose) {
        super(skipMode, testMode, false, false, false, false, generateDeployedOnUpgrade, rollbackOnError ? false : true, rollbackOnError, "");
        this.verbose = verbose;
        this.environment = environment;
        this.versionKind = versionKind;
    }

    public Descriptor<JenkinsDeploymentOptions> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<JenkinsDeploymentOptions> {
        @Override
        public String getDisplayName() {
            return "DeploymentOptions";
        }

        public ListBoxModel doFillEnvironmentItems(@QueryParameter(value = "credential") @RelativePath(value = "..") String credential, @QueryParameter(value = "credential") String credential2) {
            credential = !isNullOrEmpty(credential) ? credential : credential2;
            DeployitNotifier.DeployitDescriptor deployitDescriptor = getDeployitDescriptor();
            return isNullOrEmpty(credential) ? emptyModel() : of(deployitDescriptor.environments(credential));
        }

        protected DeployitNotifier.DeployitDescriptor getDeployitDescriptor() {
            return (DeployitNotifier.DeployitDescriptor) Jenkins.getInstance().getDescriptorOrDie(DeployitNotifier.class);
        }
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
