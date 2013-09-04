package com.xebialabs.deployit.ci;

import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

public class JenkinsPackageOptions implements Describable<JenkinsPackageOptions> {

    private final List<DeployableView> deployables;

    @DataBoundConstructor
    public JenkinsPackageOptions(List<DeployableView> deployables) {
        this.deployables = deployables;
    }

    public Descriptor<JenkinsPackageOptions> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    public List<DeployableView> getDeployables() {
        return deployables;
    }


    @Extension
    public static final class DescriptorImpl extends Descriptor<JenkinsPackageOptions> {
        @Override
        public String getDisplayName() {
            return "JenkinsPackageOptions";
        }

        /**
         * Returns all the registered {@link DeployableView} descriptors.
         */
        public DescriptorExtensionList<DeployableView, DeployableViewDescriptor> deployables() {
            return Jenkins.getInstance().<DeployableView, DeployableViewDescriptor>getDescriptorList(DeployableView.class);
        }

        @Override
        public JenkinsPackageOptions newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
        }
    }
}
