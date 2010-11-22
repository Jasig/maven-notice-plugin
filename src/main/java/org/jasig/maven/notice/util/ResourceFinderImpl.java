/**
 * Copyright (C) 2008 http://code.google.com/p/maven-license-plugin/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jasig.maven.notice.util;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * <b>Date:</b> 26-Feb-2008<br>
 * <b>Author:</b> Mathieu Carbou (mathieu.carbou@gmail.com)
 */
public final class ResourceFinderImpl implements ResourceFinder {
    private final MavenProject project;
    private CustomClassLoader compileClassPath;
    private CustomClassLoader pluginClassPath;

    public ResourceFinderImpl(MavenProject project) {
        this.project = project;
    }

    /* (non-Javadoc)
     * @see org.jasig.maven.notice.util.ResourceFinder#setCompileClassPath(java.util.List)
     */
    public void setCompileClassPath(List<String> classpath) {
        compileClassPath = new CustomClassLoader();
        if (classpath != null) {
            for (String absolutePath : classpath) {
                compileClassPath.addFolder(absolutePath);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.jasig.maven.notice.util.ResourceFinder#setPluginClassPath(java.lang.ClassLoader)
     */
    public void setPluginClassPath(ClassLoader classLoader) {
        pluginClassPath = new CustomClassLoader(classLoader);
    }

    /* (non-Javadoc)
     * @see org.jasig.maven.notice.util.ResourceFinder#findResource(java.lang.String)
     */
    public URL findResource(String resource) throws MojoFailureException {
        // first search relatively to the base directory
        URL res = this.searchProjectTree(project, resource);
        if (res != null) {
            return res;
        }

        // if not found, search for absolute location on file system, or relative to execution dir
        res = toURL(new File(resource));
        if (res != null) {
            return res;
        }

        // if not found, try the classpaths
        String cpResource = resource.startsWith("/") ? resource.substring(1) : resource;

        // tries compile claspath of projet
        res = compileClassPath.getResource(cpResource);
        if (res != null) {
            return res;
        }

        // tries this plugin classpath
        res = pluginClassPath.getResource(cpResource);
        if (res != null) {
            return res;
        }

        // otherwise, tries to return a valid URL
        try {
            res = new URL(resource);
            res.openStream().close();
            return res;
        }
        catch (Exception e) {
        }

        throw new MojoFailureException("Resource not found in file system, classpath or URL: " + resource);
    }
    
    private URL searchProjectTree(MavenProject project, String resource) {
        // first search relatively to the base directory
        URL res = toURL(new File(project.getBasedir(), resource));
        if (res != null) {
            return res;
        }
        
        //Look up the project tree to try and find a match as well.
        final MavenProject parent = project.getParent();
        if (!project.isExecutionRoot() && parent != null && parent.getBasedir() != null) {
            return this.searchProjectTree(parent, resource);
        }
        
        return null;
    }

    private URL toURL(File file) {
        if (file.exists() && file.canRead()) {
            try {
                return file.toURI().toURL();
            }
            catch (MalformedURLException e) {
            }
        }
        return null;
    }

}
