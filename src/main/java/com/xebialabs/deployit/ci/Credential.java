/**
 * Copyright (c) 2014, XebiaLabs B.V., All rights reserved.
 * <p/>
 * <p/>
 * The XL Deploy plugin for Jenkins is licensed under the terms of the GPLv2
 * <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most XebiaLabs Libraries.
 * There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
 * this software, see the FLOSS License Exception
 * <https://github.com/jenkinsci/deployit-plugin/blob/master/LICENSE>.
 * <p/>
 * This program is free software; you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation; version 2
 * of the License.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
 * Floor, Boston, MA 02110-1301  USA
 */

package com.xebialabs.deployit.ci;

import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;
import static hudson.util.FormValidation.error;
import static hudson.util.FormValidation.ok;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.xebialabs.deployit.ci.server.DeployitServer;
import com.xebialabs.deployit.ci.server.DeployitServerFactory;
import com.xebialabs.deployit.engine.api.dto.ServerInfo;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.Project;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;

public class Credential extends AbstractDescribableImpl<Credential> {

    public static final Function<Credential, String> CREDENTIAL_INDEX = new Function<Credential, String>() {
        @Override
        public String apply(Credential input) {
            return input.getName();
        }
    };
    private final String name;
    private final String username;
    private final Secret password;
    private final String credentialsId;
    private final boolean useGlobalCredential;
    private final SecondaryServerInfo secondaryServerInfo;

    private static final SchemeRequirement HTTP_SCHEME = new SchemeRequirement("http");
    private static final SchemeRequirement HTTPS_SCHEME = new SchemeRequirement("https");

    private static final Logger LOGGER = Logger.getLogger(Credential.class.getName());

    @DataBoundConstructor
    public Credential(String name, String username, Secret password, String credentialsId, SecondaryServerInfo secondaryServerInfo, boolean useGlobalCredential) {
        this.name = name;
        this.username = username;
        this.password = password;
        this.credentialsId = credentialsId;
        this.secondaryServerInfo = secondaryServerInfo;
        this.useGlobalCredential = useGlobalCredential;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getKey() {
        return username + ":" + password.getPlainText() + "@" + name + ":" + credentialsId + ":";
    }

    public String getName() {
        return name;
    }

    public String getUsername() {
        return username;
    }

    public Secret getPassword() {
        return password;
    }

    public boolean isUseGlobalCredential() {
        return useGlobalCredential;
    }

    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Project context) {
        // TODO: also add requirement on host derived from URL ?
        List<StandardUsernamePasswordCredentials> creds = lookupCredentials(StandardUsernamePasswordCredentials.class, context,
                ACL.SYSTEM,
                HTTP_SCHEME, HTTPS_SCHEME);

        return new StandardUsernameListBoxModel().withAll(creds);
    }

    public String getSecondaryServerUrl() {
        if (secondaryServerInfo != null) {
            return secondaryServerInfo.secondaryServerUrl;
        }
        return null;
    }

    public String getSecondaryProxyUrl() {
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
        return secondaryServerInfo != null && secondaryServerInfo.showSecondaryServerSettings();
    }

    public boolean showGolbalCredentials() {
        return useGlobalCredential;
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
        if (useGlobalCredential && that.useGlobalCredential && !credentialsId.equals(that.credentialsId)) return false;
        return username.equals(that.username);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        result = 31 * result + (useGlobalCredential && credentialsId != null ? credentialsId.hashCode() : 0);
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

    public static StandardUsernamePasswordCredentials lookupSystemCredentials(String credentialsId, ItemGroup<?> item) {
        StandardUsernamePasswordCredentials result = null;

        List<StandardUsernamePasswordCredentials> creds = lookupCredentials(StandardUsernamePasswordCredentials.class, item, ACL.SYSTEM, HTTP_SCHEME, HTTPS_SCHEME);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(String.format("[XLD] lookup credentials for '%s' in context '%s'. Found '%s'", credentialsId, item.getFullName(), creds.isEmpty() ? "nothing" : Integer.toString(creds.size()) + " items"));
            for (StandardUsernamePasswordCredentials cred : creds) {
                LOGGER.fine(String.format("[XLD]  >> id:%s, name:%s", cred.getId(), cred.getUsername()));
            }
            LOGGER.fine("[XLD] ------------------ end creds list");
        }
        if (creds.size() > 0) {
            result = CredentialsMatchers.firstOrNull(creds, CredentialsMatchers.withId(credentialsId));
            LOGGER.fine(String.format("[XLD] using credentails '%s'", result.getId()));
        }

        return result;
    }

    @Extension
    public static final class CredentialDescriptor extends Descriptor<Credential> {
        @Override
        public String getDisplayName() {
            return "Credential";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Project context) {
            // TODO: also add requirement on host derived from URL ?
            List<StandardUsernamePasswordCredentials> creds = lookupCredentials(StandardUsernamePasswordCredentials.class, context,
                    ACL.SYSTEM,
                    HTTP_SCHEME, HTTPS_SCHEME);

            return new StandardUsernameListBoxModel().withAll(creds);
        }

        private FormValidation validateOptionalUrl(String url) {
            try {
                if (!Strings.isNullOrEmpty(url)) {
                    new URL(url);
                }
            } catch (MalformedURLException e) {
                return error("%s is not a valid URL.", url);
            }
            return ok();
        }

        public FormValidation doCheckSecondaryServerUrl(@QueryParameter String secondaryServerUrl) {
            return validateOptionalUrl(secondaryServerUrl);
        }

        public FormValidation doCheckSecondaryProxyUrl(@QueryParameter String secondaryProxyUrl) {
            return validateOptionalUrl(secondaryProxyUrl);
        }

        public static Credential fromStapler(@QueryParameter String name, @QueryParameter String username, @QueryParameter Secret password,
                                             @QueryParameter String deployitServerUrl, @QueryParameter String deployitClientProxyUrl,
                                             @QueryParameter String secondaryServerUrl, @QueryParameter String secondaryProxyUrl, @QueryParameter String credentialsId, @QueryParameter boolean useGlobalCredential) {

            return new Credential(name, username, password, credentialsId, new SecondaryServerInfo(secondaryServerUrl, secondaryProxyUrl), useGlobalCredential);
        }

        public FormValidation doValidateUserNamePassword(@QueryParameter String deployitServerUrl, @QueryParameter String deployitClientProxyUrl, @QueryParameter String username,
                                                         @QueryParameter Secret password, @QueryParameter String secondaryServerUrl, @QueryParameter String secondaryProxyUrl) throws IOException {
            try {
                String serverUrl = Strings.isNullOrEmpty(secondaryServerUrl) ? deployitServerUrl : secondaryServerUrl;
                String proxyUrl = Strings.isNullOrEmpty(secondaryProxyUrl) ? deployitClientProxyUrl : secondaryProxyUrl;

                if (Strings.isNullOrEmpty(serverUrl)) {
                    return FormValidation.error("No server URL specified");
                }

                return validateConnection(serverUrl, proxyUrl, username, password.getPlainText());
            } catch (IllegalStateException e) {
                return FormValidation.error(e.getMessage());
            } catch (Exception e) {
                return FormValidation.error("XL Deploy configuration is not valid! %s", e.getMessage());
            }
        }

        public FormValidation doValidateCredential(@QueryParameter String deployitServerUrl, @QueryParameter String deployitClientProxyUrl, @QueryParameter String secondaryServerUrl, @QueryParameter String secondaryProxyUrl, @QueryParameter String credentialsId) throws IOException {
            Jenkins.getInstance().checkPermission(Permission.CREATE);
            try {

                String serverUrl = Strings.isNullOrEmpty(secondaryServerUrl) ? deployitServerUrl : secondaryServerUrl;
                String proxyUrl = Strings.isNullOrEmpty(secondaryProxyUrl) ? deployitClientProxyUrl : secondaryProxyUrl;

                if (Strings.isNullOrEmpty(credentialsId)) {
                    return FormValidation.error("No credentials specified");
                }
                StandardUsernamePasswordCredentials credentials = lookupSystemCredentials(credentialsId);

                if (credentials == null) {
                    return FormValidation.error(String.format("Could not find credential with id '%s'", credentialsId));
                }
                if (Strings.isNullOrEmpty(serverUrl)) {
                    return FormValidation.error("No server URL specified");
                }
                return validateConnection(serverUrl, proxyUrl, credentials.getUsername(), credentials.getPassword().getPlainText());
            } catch (IllegalStateException e) {
                return FormValidation.error(e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
                return FormValidation.error("! %s", e.getMessage());
            }
        }

        public static StandardUsernamePasswordCredentials lookupSystemCredentials(String credentialsId) {
            if (credentialsId == null) {
                return null;
            }

            return CredentialsMatchers.firstOrNull(
                    lookupCredentials(StandardUsernamePasswordCredentials.class,
                            Jenkins.getInstance(),
                            ACL.SYSTEM,
                            HTTP_SCHEME,
                            HTTPS_SCHEME),
                    CredentialsMatchers.withId(credentialsId)
            );
        }

        private FormValidation validateConnection(String serverUrl, String proxyUrl, String username, String password) throws Exception {
            DeployitServer deployitServer = DeployitServerFactory.newInstance(serverUrl, proxyUrl, username, password, 10, DeployitServer.DEFAULT_SOCKET_TIMEOUT);
            ServerInfo serverInfo = deployitServer.getServerInfo();
            deployitServer.newCommunicator();
            return FormValidation.ok("Your XL Deploy instance [%s] is alive, and your credentials are valid!", serverInfo.getVersion());
        }
    }

}
