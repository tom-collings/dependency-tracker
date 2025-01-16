package com.example.dependency_tracker.model;

public class Dependency {

    private String artifactId;
    private String groupId;
    private String version;
    
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    public void setVersion(String version) {
        this.version = version;
    }
    public String getArtifactId() {
        return artifactId;
    }
    public String getGroupId() {
        return groupId;
    }
    public String getVersion() {
        return version;
    }

}
