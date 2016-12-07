package com.xebialabs.deployit.ci.workflow;

import com.google.inject.Inject;
import com.sun.istack.NotNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Collections;
import java.util.List;

public class XLDeployPackageStep extends AbstractStepImpl {

    public List<Resource> artifacts = Collections.emptyList();
    public Resource manifest = null;
    public String packageName = null;
    public String packageVersion = null;
    public String manifestPath = null;
    public String manifestUsername = null;
    public String manifestPassword = null;

    @DataBoundConstructor
    public XLDeployPackageStep(List<Resource> artifacts, String manifestPath, String manifestUsername, String manifestPassword, String packageVersion, String packageName) {
        this.artifacts = artifacts;
        this.manifestPath = manifestPath;
        this.manifestUsername = manifestUsername;
        this.manifestPassword = manifestPassword;
        this.manifest = new Resource(manifestPath, manifestUsername, manifestPassword);
        this.packageVersion = packageVersion;
        this.packageName = packageName;
    }

    @DataBoundSetter
    public void setManifestPath(@NotNull String manifestPath) {
        this.manifestPath = manifestPath;
    }

    @DataBoundSetter
    public void setManifestUsername(String manifestUsername) {
        this.manifestUsername = manifestUsername;
    }

    @DataBoundSetter
    public void setManifestPassword(String manifestPassword) {
        this.manifestPassword = manifestPassword;
    }

    @DataBoundSetter
    public void setArtifacts(@NotNull List<Resource> artifacts) {
        this.artifacts = artifacts;
    }

    @DataBoundSetter
    public void setPackageVersion(@NotNull String packageVersion) {
        this.packageVersion = packageVersion;
    }

    @DataBoundSetter
    public void setPackageName(@NotNull String packageName) {
        this.packageName = packageName;
    }

    @Override
    public XLDeployPackageStepDescriptor getDescriptor() {
        return (XLDeployPackageStepDescriptor) super.getDescriptor();
    }

    @Extension
    public static final class XLDeployPackageStepDescriptor extends AbstractStepDescriptorImpl {

        public XLDeployPackageStepDescriptor() {
            super(XLDeployPackageExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "xlDeployPackage";
        }

        @Override
        public String getDisplayName() {
            return "Create a deployment package";
        }

    }

    public static final class XLDeployPackageExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        @Inject
        private transient XLDeployPackageStep step;

        @StepContextParameter
        private transient EnvVars envVars;

        @StepContextParameter
        private transient TaskListener listener;

        @Override
        protected Void run() throws Exception {

            DARPackageUtil packageUtil = new DARPackageUtil(step.artifacts, step.manifest, step.packageName, step.packageVersion, envVars.get("WORKSPACE"));
            String packagePath = packageUtil.createPackage();
            listener.getLogger().println("XL Deploy package created : " + packagePath);
            return null;
        }
    }

}
