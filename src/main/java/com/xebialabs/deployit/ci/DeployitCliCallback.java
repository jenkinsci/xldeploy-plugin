package com.xebialabs.deployit.ci;

import com.xebialabs.deployit.client.DeployitCli;

public interface DeployitCliCallback<T> {

    T call(DeployitCli cli);
}
