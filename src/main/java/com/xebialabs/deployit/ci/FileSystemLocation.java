package com.xebialabs.deployit.ci;

import com.google.common.collect.Lists;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import hudson.Extension;
import hudson.FilePath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;

public class FileSystemLocation extends ImportLocation {
    public final String location;
    public final String workingDirectory;

    @DataBoundConstructor
    public FileSystemLocation(String location, String workingDirectory) {
        this.location = location;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String getDarFileLocation(FilePath workspace, JenkinsDeploymentListener deploymentListener) {
        checkNotNull(emptyToNull(location), "location is empty or null");
        FilePath root = (isNullOrEmpty(workingDirectory) ? workspace : new FilePath(new File(workingDirectory)));
        try {
            return ArtifactView.findFileFromPattern(location, root, deploymentListener).getPath();
        } catch (IOException exception) {
            throw new RuntimeException(format("Unable to find DAR from %s in %s", location, root), exception);
        }
    }

    @Extension
    public static final class DescriptorImpl extends ImportLocationDescriptor {
        @Override
        public String getDisplayName() {
            return "FileSystem";
        }
    }

}
