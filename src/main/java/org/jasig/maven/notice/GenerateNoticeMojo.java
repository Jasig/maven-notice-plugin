/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.maven.notice;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilderException;
import org.jasig.maven.notice.util.ResourceFinder;

/**
 * @author Eric Dalquist
 * @version $Revision$
 * @goal generate
 * @threadSafe true
 * @requiresDependencyResolution test
 */
public class GenerateNoticeMojo extends AbstractMojo {

    /**
     * The Maven Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The dependency tree builder to use.
     *
     * @component
     * @required
     * @readonly
     */
    private DependencyTreeBuilder dependencyTreeBuilder;

    /**
     * The artifact repository to use.
     *
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository localRepository;

    /**
     * The artifact factory to use.
     *
     * @component
     * @required
     * @readonly
     */
    private ArtifactFactory artifactFactory;

    /**
     * The artifact metadata source to use.
     *
     * @component
     * @required
     * @readonly
     */
    private ArtifactMetadataSource artifactMetadataSource;

    /**
     * The artifact collector to use.
     *
     * @component
     * @required
     * @readonly
     */
    private ArtifactCollector artifactCollector;
    
    /**
     * Maven Project Builder component.
     *
     * @component
     * @required
     * @readonly
     */
    private MavenProjectBuilder mavenProjectBuilder;
    
    /**
     * License Lookup XML files / URLs.
     *
     * @parameter
     */
    private String[] licenseLookup;
    
    /**
     * Template for NOTICE file generation
     *
     * @parameter default-value="NOTICE.template"
     */
    private String noticeTemplate = "NOTICE.template";
    
    /**
     * Placeholder string in the NOTICE template file
     *
     * @parameter default-value="#GENERATED_NOTICES#"
     */
    private String noticeTemplatePlaceholder = "#GENERATED_NOTICES#";
    
    /**
     * Output location for the generated NOTICE file
     *
     * @parameter
     */
    private String outputDir = "";
    
    /**
     * Output file name
     *
     * @parameter default-value="NOTICE"
     */
    private String fileName = "NOTICE";
    
    /**
     * Level of indentation to use when generating notice lines
     *
     * @parameter default-value="2"
     */
    private int indent = 2;
    
    /**
     * @parameter expression="${encoding}" default-value="${project.build.sourceEncoding}"
     */
    private String encoding = "UTF-8";
    
    
    /* (non-Javadoc)
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Log logger = this.getLog();
        
        final DependencyNode tree = this.loadDependencyTree();
        
        final ResourceFinder finder = this.getResourceFinder();
        
        final LicenseLookupHelper licenseLookupHelper = new LicenseLookupHelper(logger, finder, licenseLookup);

        final List<?> remoteArtifactRepositories = project.getRemoteArtifactRepositories();

        final LicenseResolvingNodeVisitor visitor = new LicenseResolvingNodeVisitor(
                logger,
                licenseLookupHelper, remoteArtifactRepositories, 
                this.mavenProjectBuilder, this.localRepository);

        tree.accept(visitor);
     
        final Set<Artifact> unresolvedArtifacts = visitor.getUnresolvedArtifacts();
        if (!unresolvedArtifacts.isEmpty()) {
            logger.error("Failed to find Licenses for the following dependencies: ");
            for (final Artifact unresolvedArtifact : unresolvedArtifacts) {
                logger.error("\t" + unresolvedArtifact);
            }
            logger.error("Try adding them to a 'licenseLookup' file.");
            
            throw new MojoFailureException("Failed to find Licenses for " + unresolvedArtifacts.size() + " artifacts");
        }
        
        final Map<String, String> resolvedLicenses = visitor.getResolvedLicenses();
        final String noticeLines = this.generateNoticeLines(resolvedLicenses);
        this.writeNotice(finder, noticeLines);
    }
    
    protected String generateNoticeLines(Map<String, String> resolvedLicenses) {
        final StringBuilder builder = new StringBuilder();
        
        final String indent = StringUtils.repeat(" ", this.indent);
        
        for (final Map.Entry<String, String> resolvedEntry : resolvedLicenses.entrySet()) {
            builder.append(indent).append(resolvedEntry.getKey()).append(" under ").append(resolvedEntry.getValue()).append("\n");
        }
        
        return builder.toString();
    }
    
    protected void writeNotice(ResourceFinder finder, String noticeLines) throws MojoFailureException {
        final String noticeTemplateContents = this.readNoticeTemplate(finder);
        
        //Replace the template placeholder with the generated notice data
        final String noticeContents = noticeTemplateContents.replaceAll(Pattern.quote(this.noticeTemplatePlaceholder), noticeLines);
        
        //Write out the generated notice file
        final File outputFile = getNoticeOutputFile();
        try {
            FileUtils.writeStringToFile(outputFile, noticeContents, this.encoding);
        }
        catch (IOException e) {
            throw new MojoFailureException("Failed to write NOTICE File to: " + outputFile, e);
        }
    }

    protected String readNoticeTemplate(ResourceFinder finder) throws MojoFailureException {
        final URL inputFile = finder.findResource(this.noticeTemplate);

        final String noticeTemplateContents;
        InputStream inputStream = null;
        try {
            inputStream = inputFile.openStream();
            noticeTemplateContents = IOUtils.toString(inputStream, this.encoding);
        }
        catch (IOException e) {
            throw new MojoFailureException("Failed to open NOTICE Template File '" + this.noticeTemplate + "' from: " + inputFile, e);
        }
        finally {
            IOUtils.closeQuietly(inputStream);
        }
        return noticeTemplateContents;
    }
    
    protected File getNoticeOutputFile() {
        if (this.outputDir == null) {
            this.outputDir = "";
        }
        
        File outputPath = new File(this.outputDir);
        if (!outputPath.isAbsolute()) {
            outputPath = new File(project.getBasedir(), this.outputDir);
        }
        return new File(outputPath, this.fileName);
    }

    @SuppressWarnings("unchecked")
    protected ResourceFinder getResourceFinder() throws MojoExecutionException {
        final ResourceFinder finder = new ResourceFinder(project.getBasedir());
        try {
            final List<String> classpathElements = project.getCompileClasspathElements();
            finder.setCompileClassPath(classpathElements);
        }
        catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        finder.setPluginClassPath(getClass().getClassLoader());
        return finder;
    }


    protected DependencyNode loadDependencyTree() throws MojoExecutionException {
        try {
            return this.dependencyTreeBuilder.buildDependencyTree(
                    this.project, this.localRepository, 
                    this.artifactFactory, this.artifactMetadataSource, 
                    null, this.artifactCollector);
        }
        catch (DependencyTreeBuilderException e) {
            throw new MojoExecutionException( "Cannot build project dependency tree", e );
        }
    }
}
