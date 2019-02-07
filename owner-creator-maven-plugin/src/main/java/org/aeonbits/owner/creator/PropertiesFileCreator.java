/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.creator;

import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.Key;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static java.lang.String.format;

/**
 * PropertiesFileCreator helps you to automate the process of properties creation.
 * 
 * @author Luca Taddeo
 */
public class PropertiesFileCreator {
    public String header = "# Properties file created for: '%s', '%s' \n\n";
    public String footer = "\n# Properties file autogenerated by OWNER :: PropertyCreator\n"
            + "# Created [%s] in %s ms\n";

    public long lastExecutionTime = 0;
    
    /**
     * Method to parse the class and write file in the choosen output.
     *
     * @param clazz class to parse
     * @param output output file path
     * @param headerName
     * @param projectName
     *
     */
    public void parse(Class clazz, PrintWriter output, String headerName, String projectName) throws Exception {
        long startTime = System.currentTimeMillis();
        
        Group[] groups = parseMethods(clazz);
        long finishTime = System.currentTimeMillis();

        lastExecutionTime = finishTime - startTime;
        
        String result = toPropertiesString(groups, headerName, projectName);

        writeProperties(output, result);
    }

    /**
     * Method to get group array with subgroups and properties.
     *
     * @param clazz class to parse
     * @return array of groups
     */
    private Group[] parseMethods(Class clazz) {
        List<Group> groups = new ArrayList();
        Group unknownGroup = new Group();
        groups.add(unknownGroup);
        String[] groupsOrder = new String[0];

        for (Method method : clazz.getMethods()) {
            Property prop = new Property();

            prop.deprecated = method.isAnnotationPresent(Deprecated.class);

            if (method.isAnnotationPresent(Key.class)) {
                Key val = method.getAnnotation(Key.class);
                prop.name = val.value();
            } else {
                prop.name = method.getName();
            }

            if (method.isAnnotationPresent(DefaultValue.class)) {
                DefaultValue val = method.getAnnotation(DefaultValue.class);
                prop.defaultValue = val.value();
            }

            unknownGroup.properties.add(prop);
        }

        return orderGroup(groups, groupsOrder);
    }


    /**
     * Order groups based on passed order.
     *
     * @param groups groups to order
     * @param groupsOrder order to follow
     * @return ordered groups
     */
    private Group[] orderGroup(List<Group> groups, String[] groupsOrder) {
        LinkedList<Group> groupsOrdered = new LinkedList();

        List<Group> remained = new ArrayList(groups);

        for (String order : groupsOrder) {
            for (Group remain : remained) {
                if (remain.title.equals(order)) {
                    groupsOrdered.add(remain);
                    remained.remove(remain);
                    break;
                }
            }
        }

        groupsOrdered.addAll(remained);

        return groupsOrdered.toArray(new Group[groupsOrdered.size()]);
    }

    /**
     * Convert groups list into string.
     */
    private String toPropertiesString(Group[] groups, String headerName, String projectName) {
        StringBuilder result = new StringBuilder();

        result.append(format(header, headerName, projectName));

        for (Group group : groups) {
            result.append(group.toString());
        }

        result.append(generateFileFooter());
        
        return result.toString();
    }

    private String generateFileFooter() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	Date date = new Date();
        String dateString = dateFormat.format(date);
        return format(footer, dateString, lastExecutionTime);
    }
    
    private void writeProperties(PrintWriter output, String propertiesString) {
        output.println(propertiesString);
    }

}

class Group {

    public String title = "";
    public List<Property> properties = new ArrayList();

    public String toString(boolean subHeader) {
        StringBuilder group = new StringBuilder();

        for (Property property : properties) {
                group.append(property.toString());
        }

        return group.toString();
    }

    @Override
    public String toString() {
        return toString(false);
    }
}

class Property {

    public String name = "";
    public String defaultValue = "";
    public String valorizedAs = "";
    public boolean deprecated = false;

    @Override
    public String toString() {
        StringBuilder property = new StringBuilder();

        if (deprecated)
            property.append("# DEPRECATED PROPERTY\n");

        property.append("# Default(\"").append(defaultValue).append("\")\n");

        if (valorizedAs != null && !valorizedAs.isEmpty()) {
            property.append(name).append("=").append(valorizedAs);
        } else {
            property.append("#").append(name).append("=").append(defaultValue);
        }

        property.append("\n");

        return property.toString();
    }
}
