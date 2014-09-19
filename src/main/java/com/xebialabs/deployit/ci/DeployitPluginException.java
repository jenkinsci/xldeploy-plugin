package com.xebialabs.deployit.ci;

public class DeployitPluginException extends RuntimeException {

    public DeployitPluginException(String msg, Throwable cause) {
        super(new StringBuilder(Constants.DEPLOYIT_PLUGIN).append(": ").append(msg).toString(), cause);
    }

    public DeployitPluginException(String msg) {
        super(new StringBuilder(Constants.DEPLOYIT_PLUGIN).append(": ").append(msg).toString());
    }

}
