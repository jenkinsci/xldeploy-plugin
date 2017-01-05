package com.xebialabs.deployit.ci.workflow;

import com.google.inject.Inject;
import com.sun.istack.NotNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class XLDeployPackageStep extends AbstractStepImpl {

    public String darPath;
    public String manifestPath;
    public String artifactsPath;

    @DataBoundConstructor
    public XLDeployPackageStep(String artifactsPath, String manifestPath, String darPath) {
        this.manifestPath = manifestPath;
        this.darPath = darPath;
        this.artifactsPath = artifactsPath;
    }

    @DataBoundSetter
    public void setManifestPath(@NotNull String manifestPath) {
        this.manifestPath = manifestPath;
    }

    @DataBoundSetter
    public void setArtifactsPath(@NotNull String artifactsPath) {
        this.artifactsPath = artifactsPath;
    }

    @DataBoundSetter
    public void setDarPath(@NotNull String darPath) {
        this.darPath = darPath;
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
            return "xldCreatePackage";
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

        @StepContextParameter
        private transient FilePath ws;

        @Override
        protected Void run() throws Exception {
            DARPackageUtil packageUtil = new DARPackageUtil(step.artifactsPath, step.manifestPath, step.darPath, envVars);
            String packagePath =  ws.getChannel().call(packageUtil);
            listener.getLogger().println("XL Deploy package created : " + packagePath);
            return null;
        }

    }
}
