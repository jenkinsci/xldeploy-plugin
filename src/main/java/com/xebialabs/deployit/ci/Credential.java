/**
 * Copyright (c) 2014, XebiaLabs B.V., All rights reserved.
 *
 *
 * The XL Deploy plugin for Jenkins is licensed under the terms of the GPLv2
 * <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most XebiaLabs Libraries.
 * There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
 * this software, see the FLOSS License Exception
 * <https://github.com/jenkinsci/deployit-plugin/blob/master/LICENSE>.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation; version 2
 * of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
 * Floor, Boston, MA 02110-1301  USA
 */

package com.xebialabs.deployit.ci;

import com.google.common.base.Function;
import com.google.common.base.Strings;

import com.xebialabs.deployit.ci.server.DeployitServer;
import com.xebialabs.deployit.ci.server.DeployitServerFactory;
import com.xebialabs.deployit.engine.api.dto.ServerInfo;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import static hudson.util.FormValidation.error;
import static hudson.util.FormValidation.ok;

public class Credential extends AbstractDescribableImpl<Credential> {

    public static final Function<Credential, String> CREDENTIAL_INDEX = new Function<Credential, String>() {
        @Override
        public String apply(Credential input) {
            return input.getName();
        }
    };
    public final String name;
    public final String username;
    public final Secret password;
    private final SecondaryServerInfo secondaryServerInfo;

    @DataBoundConstructor
    public Credential(String name, String username, Secret password, SecondaryServerInfo secondaryServerInfo) {
        this.name = name;
        this.username = username;
        this.password = password;
        this.secondaryServerInfo = secondaryServerInfo;
    }

    public String getName() {
        return name;
    }

    public  String getSecondaryServerUrl() {
        if (secondaryServerInfo != null) {
            return secondaryServerInfo.secondaryServerUrl;
        }
        return null;
    }

    public  String getSecondaryProxyUrl() {
        if (secondaryServerInfo != null) {
            return secondaryServerInfo.secondaryProxyUrl;
        }
        return null;
    }

    public String resolveServerUrl(String defaultUrl) {
        if (secondaryServerInfo != null) {
            return secondaryServerInfo.resolveServerUrl(defaultUrl);
        }
        return defaultUrl;
    }

    public String resolveProxyUrl(String defaultUrl) {
        if (secondaryServerInfo != null) {
            return secondaryServerInfo.resolveProxyUrl(defaultUrl);
        }
        return defaultUrl;
    }

    public boolean showSecondaryServerSettings() {
        return secondaryServerInfo!= null && secondaryServerInfo.showSecondaryServerSettings();
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Credential that = (Credential) o;

        if (!name.equals(that.name)) return false;
        if (!password.equals(that.password)) return false;
        if (secondaryServerInfo == null && that.secondaryServerInfo != null) return false;
        if (secondaryServerInfo != null && !secondaryServerInfo.equals(that.secondaryServerInfo)) return false;

        return username.equals(that.username);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + username.hashCode();
        result = 31 * result + password.hashCode();
        result = 31 * result + (secondaryServerInfo != null ? secondaryServerInfo.hashCode() : 0);
        return result;
    }

    public static class SecondaryServerInfo {
        public final String secondaryServerUrl;
        public final String secondaryProxyUrl;

        @DataBoundConstructor
        public SecondaryServerInfo(String secondaryServerUrl, String secondaryProxyUrl) {
            this.secondaryServerUrl = secondaryServerUrl;
            this.secondaryProxyUrl = secondaryProxyUrl;
        }

        public boolean showSecondaryServerSettings() {
            return !Strings.isNullOrEmpty(secondaryServerUrl) || !Strings.isNullOrEmpty(secondaryProxyUrl);
        }

        public String resolveServerUrl(String defaultUrl) {
            if (!Strings.isNullOrEmpty(secondaryServerUrl)) {
                return secondaryServerUrl;
            }
            return defaultUrl;
        }

        public String resolveProxyUrl(String defaultUrl) {
            if (!Strings.isNullOrEmpty(secondaryProxyUrl)) {
                return secondaryProxyUrl;
            }
            return defaultUrl;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final SecondaryServerInfo that = (SecondaryServerInfo) o;

            if (secondaryProxyUrl == null && that.secondaryProxyUrl != null) return false;
            if (secondaryProxyUrl != null && !secondaryProxyUrl.equals(that.secondaryProxyUrl)) return false;
            if (secondaryServerUrl == null && that.secondaryServerUrl != null) return false;
            if (secondaryServerUrl != null && !secondaryServerUrl.equals(that.secondaryServerUrl)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = secondaryServerUrl != null ? secondaryServerUrl.hashCode() : 0;
            result = 31 * result + (secondaryProxyUrl != null ? secondaryProxyUrl.hashCode() : 0);
            return result;
        }
    }

    @Extension
    public static final class CredentialDescriptor extends Descriptor<Credential> {
        @Override
        public String getDisplayName() {
            return "Credential";
        }

        private FormValidation validateOptionalUrl(String url) {
            try {
                if (!Strings.isNullOrEmpty(url)) {
                    new URL(url);
                }
            } catch (MalformedURLException e) {
                return error("%s is not a valid URL.",url);
            }
            return ok();

        }

        public FormValidation doCheckSecondaryServerUrl(@QueryParameter String secondaryServerUrl) {
            return validateOptionalUrl(secondaryServerUrl);
        }

        public FormValidation doCheckSecondaryProxyUrl(@QueryParameter String secondaryProxyUrl) {
            return validateOptionalUrl(secondaryProxyUrl);
        }


        public FormValidation doValidate(@QueryParameter String deployitServerUrl, @QueryParameter String deployitClientProxyUrl, @QueryParameter String username,
                                         @QueryParameter Secret password, @QueryParameter String secondaryServerUrl, @QueryParameter String secondaryProxyUrl) throws IOException {
            try {
                String serverUrl = Strings.isNullOrEmpty(secondaryServerUrl) ? deployitServerUrl : secondaryServerUrl;
                String proxyUrl = Strings.isNullOrEmpty(secondaryProxyUrl) ? deployitClientProxyUrl : secondaryProxyUrl;

                DeployitServer deployitServer = DeployitServerFactory.newInstance(serverUrl, proxyUrl, username, password.getPlainText());
                ServerInfo serverInfo = deployitServer.getServerInfo();
                deployitServer.newCommunicator(); // throws IllegalStateException if creds invalid

                final VersionNumber pluginVersion = Jenkins.getInstance().getPlugin(Constants.DEPLOYIT_PLUGIN).getWrapper().getVersionNumber();
                final String serverVersion = serverInfo.getVersion();
                String major = serverVersion.substring(0, serverVersion.indexOf("."));
                if(pluginVersion.isNewerThan(new VersionNumber(major + ".*.*"))) {
                    return FormValidation.error("Plugin version [%s] is not compatible with XL Deploy server version [%s]. Please consider upgrading.", pluginVersion.toString(), serverVersion);
                }

                return FormValidation.ok("Your XL Deploy instance [%s] is alive, and your credentials are valid!", serverInfo.getVersion());
            } catch(IllegalStateException e) {
                return FormValidation.error(e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
                return FormValidation.error("XL Deploy configuration is not valid! %s", e.getMessage());
            }
        }
    }

}
