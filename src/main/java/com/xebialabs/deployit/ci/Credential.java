package com.xebialabs.deployit.ci;

import com.google.common.base.Function;
import com.xebialabs.deployit.client.DeployitCli;
import com.xebialabs.deployit.engine.api.dto.ServerInfo;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;

public class Credential extends AbstractDescribableImpl<Credential> {

    public static final Function<Credential, String> CREDENTIAL_INDEX = new Function<Credential, String>() {
        public String apply(Credential input) {
            return input.getName();
        }
    };
    public final String name;
    public final String username;
    public final Secret password;

    @DataBoundConstructor
    public Credential(String name, String username, Secret password) {
        this.name = name;
        this.username = username;
        this.password = password;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Extension
    public static final class CredentialDescriptor extends Descriptor<Credential> {
        @Override
        public String getDisplayName() {
            return "Credential";
        }

        public FormValidation doValidate(@QueryParameter String deployitServerUrl, @QueryParameter String deployitClientProxyUrl, @QueryParameter String username, @QueryParameter Secret password) throws IOException {
            try {
                ServerInfo info = new DeployitCliTemplate(deployitServerUrl, deployitClientProxyUrl, username, password).perform(new DeployitCliCallback<ServerInfo>() {
                    public ServerInfo call(DeployitCli cli) {
                        return cli.info();
                    }
                });
                return FormValidation.ok("Your Deployit instance version %s is alive, and your credentials are valid!", info.getVersion());
            } catch (Exception e) {
                e.printStackTrace();
                return FormValidation.error("Deployit configuration is not valid! %s", e.getMessage());
            }
        }
    }

}
