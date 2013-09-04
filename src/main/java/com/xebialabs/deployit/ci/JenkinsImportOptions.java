package com.xebialabs.deployit.ci;

import java.io.File;
import org.kohsuke.stapler.DataBoundConstructor;

import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

public class JenkinsImportOptions implements Describable<JenkinsImportOptions> {

    public final ImportLocation mode;

    @DataBoundConstructor
    public JenkinsImportOptions(ImportLocation mode) {
        this.mode = mode;
    }

    public Descriptor<JenkinsImportOptions> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    public String getDarFileLocation(FilePath workspace, JenkinsDeploymentListener deploymentListener) {
        return mode.getDarFileLocation(workspace, deploymentListener);
    }

    public void setGeneratedDarLocation(File generatedDarLocation) {
        mode.setGeneratedLocation(generatedDarLocation);
    }

    public ImportLocation getMode() {
        return mode;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<JenkinsImportOptions> {

        @Override
        public String getDisplayName() {
            return "JenkinsImportOptions";
        }

        public DescriptorExtensionList<ImportLocation, Descriptor<ImportLocation>> getLocationDescriptors() {
            return Jenkins.getInstance().<ImportLocation, Descriptor<ImportLocation>>getDescriptorList(ImportLocation.class);
        }
    }
}
