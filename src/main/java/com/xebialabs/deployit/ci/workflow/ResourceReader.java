package com.xebialabs.deployit.ci.workflow;

import java.io.IOException;

public interface ResourceReader {

    ResourceInfo readResource() throws IOException;
}
