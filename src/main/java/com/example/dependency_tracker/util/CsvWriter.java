package com.example.dependency_tracker.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class CsvWriter {
public static String generateAppToVersionReport(Map<String, String> appToVersionMap) throws JSONException {
        StringBuilder sb = new StringBuilder();

        sb.append("Repo");
        sb.append(",");
        sb.append("Assembly");
        sb.append(",");
        sb.append("Version");
        sb.append(",");
        sb.append("Path");
        sb.append("\n");

        for (Map.Entry<String, String> entry : appToVersionMap.entrySet()) {

            JSONArray components = new JSONArray(entry.getValue());

            for (int i = 0, size = components.length(); i < size; i++) {
                JSONObject component = components.getJSONObject(i);
                String assembly = null;
                String version = null;
                String path = null;
                if (component.has("assembly")) {
                    assembly = component.getString("assembly");
                }
                if (component.has("name")) {
                    version = component.getString("name");
                }
                if (component.has("path")) {
                    path = component.getString("path");
                }

                sb.append(entry.getKey());
                sb.append(",");
                sb.append(assembly);
                sb.append(",");
                sb.append(version);
                sb.append(",");
                sb.append(path);
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public static String generateDependencyReportFromJson(JSONArray dependencies) throws JSONException {


        StringBuilder sb = new StringBuilder();

        sb.append("Dependency Artifact Id");
        sb.append(",");
        sb.append("Dependency Group Id");
        sb.append(",");
        sb.append("Dependency Version");
        sb.append(",");
        sb.append("Type");
        sb.append("\n");


        int size = dependencies.length();
        for (int i = 0;  i < size; i++) {
            JSONObject dep = dependencies.getJSONObject(i);

            sb.append(dep.getString("artifactId"));
            sb.append(",");
            sb.append(dep.getString("groupId"));
            sb.append(",");
            sb.append(dep.getString("version"));
            sb.append(",");
            sb.append("Dependency");
            sb.append("\n");

        }
        return sb.toString();
       
    }
}
