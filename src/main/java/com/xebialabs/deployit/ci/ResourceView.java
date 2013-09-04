package com.xebialabs.deployit.ci;

import com.google.common.collect.ImmutableMap;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.client.Deployable;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.xebialabs.deployit.ci.util.ListBoxModels.of;

public class ResourceView extends DeployableView {

    @DataBoundConstructor
    public ResourceView(String type, String name, String tags, List<NameValuePair> properties) {
        super(type, name, tags, properties);
    }

    @Extension
    public static final class DescriptorImpl extends DeployableViewDescriptor {

        @Override
        public String getDisplayName() {
            return "Resource";
        }

        public ListBoxModel doFillTypeItems() {
            return of(getDeployitDescriptor().getAllResourceTypes());
        }
    }


}
