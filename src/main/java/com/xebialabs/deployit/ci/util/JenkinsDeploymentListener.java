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

package com.xebialabs.deployit.ci.util;

import java.io.Serializable;

import org.jvnet.localizer.Localizable;

import hudson.model.BuildListener;

public class JenkinsDeploymentListener implements Serializable {

    private final BuildListener listener;
    private final boolean debug;

    public JenkinsDeploymentListener(BuildListener listener, boolean debug) {
        this.listener = listener;
        this.debug = debug;
    }

    public void info(Localizable localizable) {
        info(String.valueOf(localizable));
    }

    public void error(Localizable localizable) {
        error(String.valueOf(localizable));
    }

    public void debug(String message) {
        if (debug)
            listener.getLogger().println("Debug: " + message);
    }

    public void info(String message) {
        listener.getLogger().println("Info: " + message);
    }

    public void trace(String message) {
        listener.getLogger().println("Trace: " + message);
    }

    public void error(String message) {
        listener.error(message);
    }
}
