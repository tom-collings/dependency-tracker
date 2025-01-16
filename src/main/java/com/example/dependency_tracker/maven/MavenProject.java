package com.example.dependency_tracker.maven;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.example.dependency_tracker.model.Dependency;
import com.example.dependency_tracker.model.Project;

import jodd.io.StreamGobbler;

public class MavenProject {

    private static final String LIST_START_STRING = "The following files have been resolved";

    private String name;
    private String type;
    private String source;
    private List<Project> projects = new ArrayList<>();

    public MavenProject(String name) {
        this.name = name;
        this.type = "Java";
        this.source = "Maven";
    }

    public List<Project> getProjects() {
        System.out.println("in get projects, projects size is " + projects.size());
        return projects;
    }

    public Project parse(File file) throws IOException, FileNotFoundException, XmlPullParserException {
        Project project = new Project("Java");

        MavenXpp3Reader reader = new MavenXpp3Reader();

        Model model = reader.read(new FileReader(file));
        project.setVersion(model.getVersion());
        project.setArtifactId(model.getArtifactId());
        project.setGroupId(model.getGroupId());
        project.setPackaging(model.getPackaging());
        project.setProperties(model.getProperties());
        if (model.getName() != null) {
            project.setName(model.getName());
        } else {
            project.setName(model.getArtifactId());
        }
        project.setParent(model.getParent());
        if (model.getBuild() != null && model.getBuild().getPluginManagement() != null) {
            project.setManagementPlugins(model.getBuild().getPluginManagement().getPlugins());
        }
        if (model.getBuild() != null) project.setPlugins(model.getBuild().getPlugins());
        project.setModules(model.getModules());

        project.setDependencies(getDependencies(file));

        return project;
    }

    private List<Dependency> getDependencies (File pomFile) {
        String[] commands = new String[]{"mvn", "dependency:list"};
        List<Dependency> dependencies = new ArrayList<Dependency>();
        try {
            runCmdMaven(commands, new File(pomFile.getParent()), dependencies);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return dependencies;
    }

    public static void runCmdMaven(String[] cmds, File contextDir, List<Dependency> dependencies) throws IOException, InterruptedException {

        StringBuilder command = new StringBuilder();
        for (String s: cmds) {
            command.append(s).append(" ");
        }

        System.out.println("trying to run the command " + command.toString());

        ProcessBuilder builder = new ProcessBuilder();
        builder.command(cmds);
        builder.directory(contextDir);
        Process process = builder.start();

        ByteArrayOutputStream processStdOut = new ByteArrayOutputStream();
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();

        StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), processStdOut, "");
        StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), errorOutput, "");
        outputGobbler.start();
        errorGobbler.start();
        process.waitFor();
        outputGobbler.waitFor();
        errorGobbler.waitFor();

        //System.out.println("output is: " + processStdOut.toString());
        
        populateDependenciesFromList(processStdOut, dependencies);

        // If it fails for any reasons
        if (process.exitValue() != 0) {
            throw new RuntimeException("Command => ["+command.toString()+"] failed! \n\nStdOut =>\n" + processStdOut.toString() + "\nStdErr =>\n"+errorOutput.toString());
        }

    }

    public static void populateDependenciesFromList (ByteArrayOutputStream processStdOut, List<Dependency> dependencies) throws IOException {
        ByteArrayInputStream inStream = new ByteArrayInputStream(processStdOut.toByteArray());
        InputStreamReader reader = new InputStreamReader(inStream);
        BufferedReader buffReader = new BufferedReader(reader);

        String line;
        String trimmedLine = "";
        String[] values;
        boolean shouldBeSplitting = false;

        while ((line = buffReader.readLine()) != null) {
            trimmedLine = line.replace("[INFO]", "");
            if (shouldBeSplitting) {
                if (trimmedLine.contains("--------------") || trimmedLine.isBlank() || trimmedLine.isEmpty() ) {
                    shouldBeSplitting = false;
                }
                else {
                    values = trimmedLine.split(":");
                    String group = values[0].trim();
                    String artifact = values[1].trim();
                    String version = values[3].trim();
                    //System.out.println("gav = " + group + ":" + artifact + ":" + version);
                    Dependency dep = new Dependency();
                    dep.setArtifactId(artifact);
                    dep.setGroupId(group);
                    dep.setVersion(version);
                    dependencies.add(dep);
                }
            }

            if (trimmedLine.contains(LIST_START_STRING)) {
                shouldBeSplitting = true;
            }

        }


    }

    public JSONObject toJson() throws JSONException {
        JSONObject mavenProject = new JSONObject();
        mavenProject.put("name", name);
        mavenProject.put("type", "Java");
        mavenProject.put("source", "Maven");

        JSONArray projectArray = new JSONArray();

        for (Project project : projects) {
            JSONObject projectJson = new JSONObject();

            projectJson.put("name", project.getName());
            projectJson.put("type", project.getType());
            projectJson.put("artifactId", project.getArtifactId());
            projectJson.put("groupId", project.getGroupId());
            projectJson.put("version", project.getVersion());
            projectJson.put("javaVersion", project.getJavaVersion());
            projectJson.put("javaSource", project.getJavaSource());
            projectJson.put("javaRelease", project.getJavaRelease());
            projectJson.put("javaTarget", project.getJavaTarget());
            projectJson.put("packaging", project.getPackaging());

            if (project.getParent() != null) {
                JSONObject parentJson = new JSONObject();
                parentJson.put("artifactId", project.getParent().getArtifactId());
                parentJson.put("groupId", project.getParent().getGroupId());
                parentJson.put("version", project.getParent().getVersion());

                projectJson.put("parent", parentJson);
            }

            JSONArray dependencyArray = new JSONArray();
            for (Dependency d : project.getDependencies()) {
                JSONObject depJson = new JSONObject();
                depJson.put("artifactId", d.getArtifactId());
                depJson.put("groupId", d.getGroupId());
                depJson.put("version", d.getVersion());
                dependencyArray.put(depJson);
            }
            projectJson.put("dependencies", dependencyArray);

            JSONArray pluginArray = new JSONArray();

            List<Plugin> plugins = project.getPlugins();
            plugins.addAll(project.getManagementPlugins());

            for (Plugin p : project.getPlugins()) {
                JSONObject plugJson = new JSONObject();
                plugJson.put("artifactId", p.getArtifactId());
                plugJson.put("groupId", p.getGroupId());
                plugJson.put("version", p.getVersion());
                pluginArray.put(plugJson);
            }
            projectJson.put("plugins", pluginArray);

            JSONArray moduleArray = new JSONArray();
            for (String m : project.getModules()) {
                moduleArray.put(m);
            }
            projectJson.put("modules", moduleArray);

            JSONArray propertyArray = new JSONArray();
            for (String key : project.getProperties().stringPropertyNames()) {
                String value = project.getProperties().getProperty(key);
                JSONObject propJson = new JSONObject();
                propJson.put("name", key);
                propJson.put("value", value);
                propertyArray.put(propJson);
            }

            projectJson.put("properties", propertyArray);

            projectArray.put(projectJson);

        }

        mavenProject.put("projects", projectArray);

        return mavenProject;
    }

    public Project findParent(Parent parent) {
        if (parent != null) {
            for (Project p : projects) {
                if (p.getArtifactId().equals(parent.getArtifactId())) {
                    return p;
                }
            }
        }
        return null;
    }

    public void reconcile() {
        for (Project p : projects) {
            Project parent = findParent(p.getParent());

            for (Dependency d : p.getDependencies()) {
                if (d.getVersion() != null) {
                    if (d.getVersion().startsWith("${")) {
                        String versionResolved = null;
                        versionResolved = findPropertyInParent(p, d.getVersion().replace("${", "").replace("}", ""));
                        if (parent != null && versionResolved == null) {
                            versionResolved = findPropertyInParent(parent, d.getVersion().replace("${", "").replace("}", ""));
                        }
                        if (versionResolved != null) {
                            d.setVersion(versionResolved);
                        }
                    }
                }
            }
            for (Plugin plugin : p.getPlugins()) {
                if (plugin.getVersion() != null) {
                    if (plugin.getVersion().startsWith("${")) {

                        String versionResolved = null;
                        versionResolved = findPropertyInParent(p, plugin.getVersion().replace("${", "").replace("}", ""));
                        if (parent != null && versionResolved == null) {
                            versionResolved = findPropertyInParent(parent, plugin.getVersion().replace("${", "").replace("}", ""));
                        }
                        if (versionResolved != null) {
                            plugin.setVersion(versionResolved);
                        }
                    }
                }
            }
            for (Plugin plugin : p.getManagementPlugins()) {
                if (plugin.getVersion() != null) {
                    if (plugin.getVersion().startsWith("${")) {
                        String versionResolved = null;
                        versionResolved = findPropertyInParent(p, plugin.getVersion().replace("${", "").replace("}", ""));
                        if (parent != null && versionResolved == null) {
                            versionResolved = findPropertyInParent(parent, plugin.getVersion().replace("${", "").replace("}", ""));
                        }
                        if (versionResolved != null) {
                            plugin.setVersion(versionResolved);
                        }
                    }
                }
            }

        }
    }

    public void findJavaVersions() {
        for (Project p : projects) {
            if (p.getProperties() != null) {
                p.setJavaVersion(p.getProperties().getProperty("java.version"));
                p.setJavaSource(p.getProperties().getProperty("maven.compiler.source"));
                p.setJavaTarget(p.getProperties().getProperty("maven.compiler.target"));
                p.setJavaRelease(p.getProperties().getProperty("maven.compiler.release"));
            }

            if (p.getPlugins() != null) findJavaVersionsInPlugins(p, p.getPlugins());
            if (p.getManagementPlugins() != null) findJavaVersionsInPlugins(p, p.getManagementPlugins());

        }
    }

    private void findJavaVersionsInPlugins (Project p, List<Plugin> plugins) {
        for (Plugin plugin : plugins) {
            if (plugin.getArtifactId() != null && plugin.getArtifactId().equals("maven-compiler-plugin")) {
                if (plugin.getConfiguration() != null) {
                    if (plugin.getConfiguration() instanceof Properties) {
                        Properties props = (Properties) plugin.getConfiguration();
                        p.setJavaSource(props.getProperty("source"));
                        p.setJavaTarget(props.getProperty("target"));
                        p.setJavaRelease(props.getProperty("release"));
                    } else if (plugin.getConfiguration() instanceof Xpp3Dom) {
                        Xpp3Dom props = (Xpp3Dom) plugin.getConfiguration();
                        for (Xpp3Dom child: props.getChildren()) {
                            if (child.getName().equals("target")) {
                                p.setJavaTarget(child.getValue());
                            }
                            if (child.getName().equals("release")) {
                                p.setJavaRelease(child.getValue());
                            }
                            if (child.getName().equals("source")) {
                                p.setJavaSource(child.getValue());
                            }
                        }
                    }
                }
            }
        }
    }

    public String findPropertyInParent(Project project, String propertyName) {
        if (project.getProperties() != null) {
            if (project.getProperties().getProperty(propertyName) != null) {
                return project.getProperties().getProperty(propertyName);
            }
        }
        return null;
    }
    
}
