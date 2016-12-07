package com.xebialabs.deployit.ci.workflow;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.apache.commons.lang.StringUtils.isNotBlank;

public class DARPackageUtil {
    private final List<Resource> artifacts;
    private final Resource manifest;
    private final String packageName;
    private final String packageVersion;
    private final String workspace;

    public DARPackageUtil(List<Resource> artifacts, Resource manifest, String packageName, String packageVersion, String workspace) {

        this.artifacts = artifacts;
        this.manifest = manifest;
        this.packageName = packageName;
        this.packageVersion = packageVersion;
        this.workspace = workspace;
    }

    public String createPackage() throws IOException {
        String packagePath = outputFilePath();
        FileOutputStream fileOutputStream = new FileOutputStream(packagePath);
        ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
        addResourceToPackage(this.manifest, zipOutputStream);
        for (Resource artifact : this.artifacts) {
            addResourceToPackage(artifact, zipOutputStream);
        }
        zipOutputStream.closeEntry();
        zipOutputStream.close();
        fileOutputStream.close();
        return packagePath;
    }

    private void addResourceToPackage(Resource resource, ZipOutputStream zipOutputStream) throws IOException {

        ZipEntry zipEntry;
        InputStream inputStream;

        if (resource.isURLResource()) {
            URL url = new URL(resource.path);
            URLConnection urlConnection = url.openConnection();
            setAuthData(urlConnection, resource);
            inputStream = urlConnection.getInputStream();
            zipEntry = new ZipEntry(FilenameUtils.getName(url.getPath()));
        } else {
            File resourceFile = new File(getGeneratedFilePath(resource.path));
            zipEntry = new ZipEntry(resourceFile.getName());
            inputStream = new FileInputStream(resourceFile);
        }

        zipOutputStream.putNextEntry(zipEntry);
        byte[] buf = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buf)) > 0) {
            zipOutputStream.write(buf, 0, bytesRead);
        }
    }

    private void setAuthData(URLConnection urlConnection, Resource resource) {
        if (isNotBlank(resource.username) && isNotBlank(resource.password)) {
            urlConnection.setRequestProperty("Authorization", "Basic " + Base64.encodeBase64String((resource.username + ":" + resource.password).getBytes()));
        }
    }

    private String getGeneratedFilePath(String filePath) {
        return getWorkspace().append(filePath).toString();
    }

    private String outputFilePath() {
        return getWorkspace().append(this.packageName).append("-").append(this.packageVersion).append(".dar").toString();
    }

    private StringBuilder getWorkspace() {
        return new StringBuilder(this.workspace).append(File.separator);
    }

}