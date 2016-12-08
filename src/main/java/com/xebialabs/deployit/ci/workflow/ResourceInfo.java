package com.xebialabs.deployit.ci.workflow;

import java.io.InputStream;
import java.util.zip.ZipEntry;

public class ResourceInfo {

    private final ZipEntry zipEntry;
    private final InputStream inputStream;

    public ResourceInfo(ZipEntry zipEntry, InputStream inputStream) {
        this.zipEntry = zipEntry;
        this.inputStream = inputStream;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public ZipEntry getZipEntry() {
        return zipEntry;
    }
}
