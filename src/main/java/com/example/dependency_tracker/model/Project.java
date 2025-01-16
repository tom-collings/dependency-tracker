package com.example.dependency_tracker.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;

import org.apache.maven.model.Parent;

public class Project {


    private String name;
    private String type;
    private String javaSource;
    private String javaTarget;
    private String javaVersion;
    private String javaRelease;
    private String groupId;
    private String artifactId;
    private String version;
    private String packaging;
    private Properties properties;
    private Parent parent;
    private List<Dependency> dependencies = new ArrayList<>();
    private List<Plugin> plugins = new ArrayList<>();
    private List<Plugin> managementPlugins = new ArrayList<>();
    private List<String> modules = new ArrayList<>();

    public void setParent(Parent parent) {
        this.parent = parent;
    }

    public void setPlugins(List<Plugin> plugins) {
        this.plugins = plugins;
    }

    public void setManagementPlugins(List<Plugin> managementPlugins) {
        this.managementPlugins = managementPlugins;
    }

    public void setModules(List<String> modules) {
        this.modules = modules;
    }

    public Parent getParent() {
        return parent;
    }

    public List<Plugin> getPlugins() {
        return plugins;
    }

    public List<Plugin> getManagementPlugins() {
        return managementPlugins;
    }

    public List<String> getModules() {
        return modules;
    }

    private String fileContent;

    public Project (String type) {
        this.type = type;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<Dependency> dependencies) {
        this.dependencies = dependencies;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJavaSource() {
        return javaSource;
    }

    public void setJavaSource(String javaSource) {
        this.javaSource = javaSource;
    }

    public String getJavaTarget() {
        return javaTarget;
    }

    public void setJavaTarget(String javaTarget) {
        this.javaTarget = javaTarget;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    public String getJavaRelease() {
        return javaRelease;
    }

    public void setJavaRelease(String javaRelease) {
        this.javaRelease = javaRelease;
    }

    public String getFileContent() {
        return fileContent;
    }

    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }
}
