package com.xebialabs.deployit.ci;

import hudson.model.Descriptor;
import jenkins.model.Jenkins;


public abstract class DeployableViewDescriptor extends Descriptor<DeployableView> {

    protected DeployableViewDescriptor() {
    }

    protected DeployableViewDescriptor(Class<? extends DeployableView> clazz) {
        super(clazz);
    }

    protected DeployitNotifier.DeployitDescriptor getDeployitDescriptor() {
        return (DeployitNotifier.DeployitDescriptor) Jenkins.getInstance().getDescriptorOrDie(DeployitNotifier.class);
    }
}