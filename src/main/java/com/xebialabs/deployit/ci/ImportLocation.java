package com.xebialabs.deployit.ci;

import java.io.File;
import java.io.IOException;

import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;

import hudson.FilePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

public abstract class ImportLocation implements Describable<ImportLocation> {

    protected File generatedLocation;

    public Descriptor<ImportLocation> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    public abstract String getDarFileLocation(FilePath workspace, JenkinsDeploymentListener deploymentListener) ;

    public void setGeneratedLocation(File generatedLocation) {
        this.generatedLocation = generatedLocation;
    }

    /**
     * You need to override this method if your import location creates temp files
     */
    public void cleanup() throws IOException, InterruptedException {}

}
