package com.xebialabs.deployit.ci.workflow;

public class ResourceReaderFactory {

    public ResourceReader getReader(Resource resource, String workspace) {
        if (resource.isURLResource()) {
            return new URLResourceReader(resource);
        } else {
            return new FileResourceReader(resource, workspace);
        }
    }
}
