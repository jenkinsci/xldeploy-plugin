package com.xebialabs.deployit.ci;

import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import hudson.Extension;
import hudson.FilePath;
import org.kohsuke.stapler.DataBoundConstructor;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.emptyToNull;

public class URLLocation extends ImportLocation {
    public final String url;

    @DataBoundConstructor
    public URLLocation(String url) {
        this.url = url;
    }

    @Override
    public String getDarFileLocation(FilePath workspace, JenkinsDeploymentListener deploymentListener) {
        checkNotNull(emptyToNull(url), "URL is empty or null");
        return url;
    }

    @Extension
    public static final class DescriptorImpl extends ImportLocationDescriptor {
        @Override
        public String getDisplayName() {
            return "URL";
        }
    }
}
