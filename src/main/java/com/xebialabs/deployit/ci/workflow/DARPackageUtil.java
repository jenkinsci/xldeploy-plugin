package com.xebialabs.deployit.ci.workflow;

import com.xebialabs.overthere.RuntimeIOException;
import com.xebialabs.overthere.util.OverthereUtils;
import de.schlichtherle.truezip.file.TArchiveDetector;
import de.schlichtherle.truezip.file.TFile;
import de.schlichtherle.truezip.file.TFileOutputStream;
import de.schlichtherle.truezip.file.TVFS;
import hudson.EnvVars;
import hudson.remoting.Callable;
import org.jenkinsci.remoting.RoleChecker;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static java.io.File.separator;

public class DARPackageUtil implements Callable<String, IOException> {

    private static final String DEPLOYIT_MANIFEST_XML = "deployit-manifest.xml";

    private final String artifactsPath;
    private final String manifestPath;
    private final String darPath;
    private final String workspace;
    private final EnvVars envVars;

    public DARPackageUtil(String artifactsPath, String manifestPath, String darPath, EnvVars envVars) {
        this.artifactsPath = artifactsPath;
        this.manifestPath = manifestPath;
        this.darPath = envVars.expand(darPath);
        this.workspace = envVars.get("WORKSPACE");
        this.envVars = envVars;
    }

    public String call() throws IOException {
        String manifestContent = replaceEnvVarInManifest();
        List<String> filePathsToBeAdded = filterFiles(manifestContent);
        String packagePath = outputFilePath();
        try {
            addManifest(packagePath);
            addArtifactsAndFolders(filePathsToBeAdded, packagePath);
        } finally {
            TVFS.umount();
        }
        return packagePath;
    }

    private void addArtifactsAndFolders(List<String> filePathsToBeAdded, String packagePath) throws FileNotFoundException {
        for (String filePath : filePathsToBeAdded) {
            File sourceFile = new File(this.workspace + separator + artifactsPath + separator + filePath);
            if (sourceFile.isDirectory()) {
                TFile targetFolder = new TFile(packagePath + separator + filePath);
                targetFolder.mkdirs();
                copyFolder(sourceFile, targetFolder);
            } else {
                TFile artifactDir = new TFile(packagePath, stripFilePath(filePath), TArchiveDetector.ALL);
                artifactDir.mkdirs();
                copyFile(new FileInputStream(sourceFile), new TFile(artifactDir, sourceFile.getName(), TArchiveDetector.NULL));
            }
        }
    }

    private void addManifest(String packagePath) throws FileNotFoundException {
        TFile entry = new TFile(packagePath + separator + DEPLOYIT_MANIFEST_XML);
        copyFile(new FileInputStream(new File(this.workspace + separator + this.manifestPath)), entry);
    }

    private String stripFilePath(String filePath) {
        return filePath.contains(separator) ? filePath.substring(0, filePath.lastIndexOf(separator)) : "";
    }

    private void copyFolder(File sourceFile, TFile targetFolder) throws FileNotFoundException {
        for (File file : sourceFile.listFiles()) {
            if (file.isFile()) {
                copyFile(new FileInputStream(file), new TFile(targetFolder, file.getName(), TArchiveDetector.NULL));
            } else if (file.isDirectory()) {
                TFile targetFolder1 = new TFile(targetFolder, file.getName());
                targetFolder1.mkdirs();
                copyFolder(file, targetFolder1);
            }
        }
    }

    private void copyFile(final InputStream sourceFile, TFile targetFile) {
        try (InputStream is = sourceFile; OutputStream os = new TFileOutputStream(targetFile)) {
            OverthereUtils.write(is, os);
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    List<String> filterFiles(String manifestContent) {
        final List<String> files = new ArrayList<>();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            DefaultHandler handler = new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    super.startElement(uri, localName, qName, attributes);
                    for (int index = 0; index < attributes.getLength(); index++) {
                        if (attributes.getQName(index).equals("file")) {
                            files.add(attributes.getValue(attributes.getQName(index)));
                        }
                    }
                }
            };
            saxParser.parse(new InputSource(new StringReader(manifestContent)), handler);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new IllegalArgumentException("Exception Occured while parsing deployit-manifest", e);
        }
        return files;
    }

    private String replaceEnvVarInManifest() throws IOException {
        String manifestContent;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Files.copy(Paths.get(workspace, manifestPath), outputStream);
            manifestContent = new String(outputStream.toByteArray());
        }
        manifestContent = envVars.expand(manifestContent);
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(workspace, manifestPath), StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.append(manifestContent);
        }
        return manifestContent;
    }

    private String outputFilePath() {
        return new StringBuilder(this.workspace).append(separator).append(this.darPath).toString();
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {

    }
}