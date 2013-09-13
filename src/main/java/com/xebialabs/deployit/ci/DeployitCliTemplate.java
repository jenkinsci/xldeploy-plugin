/**
 * Copyright (c) 2013, XebiaLabs B.V., All rights reserved.
 *
 *
 * The Deployit plugin for Jenkins is licensed under the terms of the GPLv2
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

import com.xebialabs.deployit.client.ConnectionOptions;
import com.xebialabs.deployit.client.DeployitCli;
import com.xebialabs.deployit.client.Descriptors;

import hudson.PluginManager;
import hudson.model.Hudson;
import hudson.util.Secret;
import jenkins.model.Jenkins;

/**
 * Manage the Classloader when using the resteasy stack, else ClassNotFoundException.
 */
public class DeployitCliTemplate {

    private String deployitServerUrl;
    private String deployitClientProxyUrl;

    private Credential credential;

    public DeployitCliTemplate(String deployitServerUrl, String deployitClientProxyUrl, Credential credential) {
        this.deployitServerUrl = deployitServerUrl;
        this.deployitClientProxyUrl = deployitClientProxyUrl;
        this.credential = credential;
    }

    public DeployitCliTemplate(String deployitServerUrl, String deployitClientProxyUrl, String username, Secret password) {
        this(deployitServerUrl, deployitClientProxyUrl, new Credential(username, username, password));
    }

    public <T> T perform(DeployitCliCallback<T> callback) {
        final Thread currentThread = Thread.currentThread();
        final ClassLoader origClassLoader = currentThread.getContextClassLoader();

        try {
            final ClassLoader pluginClassLoader = Jenkins.getInstance().getPluginManager().getPlugin("deployit-plugin").classLoader;
            currentThread.setContextClassLoader(pluginClassLoader);
            return callback.call(getCli());
        } finally {
            currentThread.setContextClassLoader(origClassLoader);
        }
    }

    private synchronized DeployitCli getCli() {
        return new DeployitCli(new ConnectionOptions(deployitServerUrl, deployitClientProxyUrl, credential.username, credential.password.getPlainText()));
    }

    public void setCredential(Credential credential) {
        this.credential = credential;
    }

    public String getDeployitServerUrl() {
        return deployitServerUrl;
    }

    public String getDeployitClientProxyUrl() {
        return deployitClientProxyUrl;
    }

    public Credential getCredential() {
        return credential;
    }

    public Descriptors getDescriptors() {
        return perform(new DeployitCliCallback<Descriptors>() {
                public Descriptors call(DeployitCli cli) {
                        return cli.getDescriptors();
                }
            });
    }
}
