package com.xebialabs.deployit.ci.workflow;

import com.sun.istack.NotNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class Resource extends AbstractDescribableImpl<Resource> {

    public String path;
    public String username;
    public String password;

    @DataBoundConstructor
    public Resource(String path, String username, String password) {
        this.path = path;
        this.username = username;
        this.password = password;
    }

    @DataBoundSetter
    public void setPath(@NotNull String path) {
        this.path = path;
    }

    @DataBoundSetter
    public void setUsername(String username) {
        this.username = username;
    }

    @DataBoundSetter
    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isURLResource() {
        return path.toUpperCase().startsWith("HTTP");
    }

    @Extension
    public static class ResourceDescriptor extends Descriptor<Resource> {

        public ResourceDescriptor(final Class<? extends Resource> clazz) {
            super(clazz);
        }

        public ResourceDescriptor() {
        }

        @Override
        public String getDisplayName() {
            return "Resource";
        }

    }
}
