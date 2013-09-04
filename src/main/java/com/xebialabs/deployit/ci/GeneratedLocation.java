package com.xebialabs.deployit.ci;

import java.io.File;
import java.io.IOException;
import org.kohsuke.stapler.DataBoundConstructor;
import com.google.common.io.Files;

import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;

import hudson.Extension;
import hudson.FilePath;

import static com.google.common.base.Preconditions.checkNotNull;

public class GeneratedLocation extends ImportLocation {

    private File localTempDir;
    private FilePath localTempDar;

    @DataBoundConstructor
    public GeneratedLocation() {
    }

    /**
     * For local workspace just returns the path;
     * For remote workspace - copies dar file into local temporary location first,
     * then returns temporary path. FilePath.cleanup() method should be used to delete all temporary files.
     */
    @Override
    public String getDarFileLocation(FilePath workspace, JenkinsDeploymentListener deploymentListener) {
        checkNotNull(generatedLocation, "The package has not been generated");

        if (!workspace.isRemote()) {
            return generatedLocation.getPath();
        }

        FilePath remoteDar = new FilePath(workspace.getChannel(), generatedLocation.getPath());
        localTempDir = Files.createTempDir();
        localTempDar = new FilePath(new File(localTempDir, remoteDar.getName()));
        try {
            remoteDar.copyTo(localTempDar);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return localTempDar.getRemote();
    }

    @Extension
    public static final class DescriptorImpl extends ImportLocationDescriptor {
        @Override
        public String getDisplayName() {
            return " Generated";
        }
    }

    @Override
    public void cleanup() throws IOException, InterruptedException {
        localTempDar.delete();
        localTempDir.delete();
    }
}
