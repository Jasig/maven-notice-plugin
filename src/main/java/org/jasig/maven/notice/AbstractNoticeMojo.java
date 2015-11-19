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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
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
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilderException;
import org.apache.maven.shared.dependency.tree.traversal.DependencyNodeVisitor;
import org.jasig.maven.notice.lookup.ArtifactLicense;
import org.jasig.maven.notice.lookup.LicenseLookup;
import org.jasig.maven.notice.lookup.MappedVersion;
import org.jasig.maven.notice.util.ResourceFinder;
import org.jasig.maven.notice.util.ResourceFinderImpl;

/**
 * Common base mojo for notice related plugins
 * 
 * @author Eric Dalquist
 */
public abstract class AbstractNoticeMojo extends AbstractMojo {
    
    /* DI configuration of Maven components needed for the plugin */

    /**
     * The Maven Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The dependency tree builder to use.
     *
     * @component
     * @required
     * @readonly
     */
    protected DependencyTreeBuilder dependencyTreeBuilder;

    /**
     * The artifact repository to use.
     *
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    protected ArtifactRepository localRepository;

    /**
     * The artifact factory to use.
     *
     * @component
     * @required
     * @readonly
     */
    protected ArtifactFactory artifactFactory;

    /**
     * The artifact metadata source to use.
     *
     * @component
     * @required
     * @readonly
     */
    protected ArtifactMetadataSource artifactMetadataSource;

    /**
     * The artifact collector to use.
     *
     * @component
     * @required
     * @readonly
     */
    protected ArtifactCollector artifactCollector;
    
    /**
     * Maven Project Builder component.
     *
     * @component
     * @required
     * @readonly
     */
    protected MavenProjectBuilder mavenProjectBuilder;
    
    /* Mojo Configuration Properties */
    
    /**
     * Use licenseMapping
     *
     * @parameter
     * @deprecated use licenseMapping
     */
    @Deprecated
    protected String[] licenseLookup = new String[0];
    
    /**
     * Parameter to skip running checks entirely.
     *
     * @parameter expression="${skip.checks}"
     */
    private boolean skipChecks = false;
    
    /**
     * License Mapping XML files / URLs. Lookups are done in-order with
     * files being checked top to bottom for matches
     *
     * @parameter
     */
    protected String[] licenseMapping = new String[0];
    
    /**
     * Template for NOTICE file generation
     *
     * @parameter default-value="NOTICE.template"
     */
    protected String noticeTemplate = "NOTICE.template";
    
    /**
     * Placeholder string in the NOTICE template file
     *
     * @parameter default-value="#GENERATED_NOTICES#"
     */
    protected String noticeTemplatePlaceholder = "#GENERATED_NOTICES#";
    
    /**
     * Output location for the generated NOTICE file
     *
     * @parameter default-value="${basedir}"
     */
    protected String outputDir = "";
    
    /**
     * Output file name
     *
     * @parameter default-value="NOTICE"
     */
    protected String fileName = "NOTICE";
    
    /**
     * @parameter default-value="${project.build.sourceEncoding}"
     */
    protected String encoding = "UTF-8";
    
    /**
     * Set if the NOTICE file should include all dependencies from all child modules.
     * 
     * @parameter default-value="true"
     */
    protected boolean includeChildDependencies = true;
    
    /**
     * Set if a NOTICE file should be generated for each child module
     * 
     * @parameter default-value="true"
     */
    protected boolean generateChildNotices = true;
    
    /**
     * The {@link MessageFormat} syntax string used to generate each license line in the NOTICE file<br/>
     * {0} - artifact name<br/>
     * {1} - license name<br/>
     * if the project information is available you will also have the following additional parameters<br/>
     * {2} - groupId<br/>
     * {3} - artifactId<br/>
     * {4} - version<br/>
     * {5} - organization name<br/>
     * {6} - organization url<br/>
     * {7} - copyright message<br/>
     * <br/>
     * To make it easier to generate multi-line notice messages, you may also use \n inside the message
     * and they will be replaced by the appropriate line separator for the platform.
     * 
     * @parameter default-value="  {0} under {1}"
     */
    protected String noticeMessage = "  {0} under {1}";
    
    private MessageFormat parsedNoticeMessage;
    
    /**
     * ArtifactIds of child modules to exclude
     *
     * @parameter
     */
    protected Set<String> excludedModules = new LinkedHashSet<String>();

    /**
     * Allows to specify aliases for license names, as projects may define them under slightly different names,
     * e.g. ASLv2 = The Apache Software License, version 2.0
     *
     * Using the aliases you can guarantee that all the different names of the same "semantic" license will have
     * the same output in the NOTICE file.
     *
     * @parameter
     */
    protected Properties licenseNameAliases = new Properties();

    /**
     * The copyright message has the following parameters :
     * {0} - inception year<br/>
     * {1} - current year<br/>
     * {2} - organization name<br/>
     * {3} - organization url (in parenthesis)<br/>
     *
     * it is only generated if the associated Maven project has a valid inception year and organization name defined.
     * @parameter default-value = "Copyright (C) {0}-{1} {2} {3}"
     */
    protected String copyrightMessage = "Copyright (C) {0}-{1} {2}";

    private MessageFormat parsedCopyrightMessage;

    /**
     * Placeholder string in the NOTICE template file for a license summary.
     *
     * If present in the template a summary list of licenses will be generated
     *
     * @parameter default-value="#GENERATED_LICENSE_SUMMARY#"
     */
    protected String noticeTemplateLicenseSummaryPlaceholder = "#GENERATED_LICENSE_SUMMARY#";

    /**
     * Message format for the license summary lines, available parameters are :
     *
     *
     * @parameter default-value="{0}. {1} ({2} occurences)"
     */
    protected String licenseSummaryMessage = "{0}. {1} ({2} occurences)";

    private MessageFormat parsedLicenseSummaryMessage;

    /* (non-Javadoc)
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public final void execute() throws MojoExecutionException, MojoFailureException {
        final Log logger = this.getLog();
        
        if (this.skipChecks) {
            logger.info("NOTICE file checks are skipped.");
            return;
        }
        
        if (licenseLookup != null && licenseLookup.length > 0) {
            logger.warn("'licenseLookup' configuration property is deprecated use 'licenseMapping' instead");
            if (licenseMapping != null && licenseMapping.length > 0) {
                throw new MojoFailureException("Both 'licenseMapping' and 'licenseLookup' configuration properties configured. Only one may be used.");
            }
            licenseMapping = licenseLookup;
        }
       
        //Check if NOTICE for child modules should be generated
        if (!this.generateChildNotices && !this.project.isExecutionRoot()) {
            return;
        }
        
        final ResourceFinder finder = this.getResourceFinder();
        
        final LicenseLookupHelper licenseLookupHelper = new LicenseLookupHelper(logger, finder, licenseMapping);

        final List<?> remoteArtifactRepositories = project.getRemoteArtifactRepositories();

        final LicenseResolvingNodeVisitor visitor = new LicenseResolvingNodeVisitor(
                logger,
                licenseLookupHelper, remoteArtifactRepositories, 
                this.mavenProjectBuilder, this.localRepository, this.licenseNameAliases);

        this.parseProject(this.project, visitor);
     
        //Check for any unresolved artifacts
        final Set<Artifact> unresolvedArtifacts = visitor.getUnresolvedArtifacts();
        this.checkUnresolved(unresolvedArtifacts);
        
        //Convert the resovled notice data into a String
        final Map<String, String> resolvedLicenses = visitor.getResolvedLicenses();
        final Map<String, Artifact> resolvedArtifacts = visitor.getResolvedArtifacts();
        final String noticeLines = this.generateNoticeLines(resolvedLicenses, resolvedArtifacts);
        final String noticeTemplateContents = this.readNoticeTemplate(finder);
        
        //Replace the template placeholder with the generated notice data
        String noticeContents = noticeTemplateContents.replaceAll(Pattern.quote(this.noticeTemplatePlaceholder), noticeLines);

        if (noticeContents.contains(this.noticeTemplateLicenseSummaryPlaceholder)) {
            final String noticeLicenseSummaryLines = this.generateLicenseSummaryLines(resolvedLicenses);
            noticeContents = noticeContents.replaceAll(Pattern.quote(this.noticeTemplateLicenseSummaryPlaceholder), noticeLicenseSummaryLines);
        }
        
        //Let the subclass deal with the generated NOTICE file
        this.handleNotice(finder, noticeContents);
    }

    /**
     * Called with the expected NOTICE file contents for this project.
     */
    protected abstract void handleNotice(ResourceFinder finder, String noticeContents) throws MojoFailureException;
    
    /**
     * Loads the dependency tree for the project via {@link #loadDependencyTree(MavenProject)} and then uses
     * the {@link DependencyNodeVisitor} to load the license data. If {@link #aggregating} is enabled the method
     * recurses on each child module.
     */
    @SuppressWarnings("unchecked")
    protected void parseProject(MavenProject project, DependencyNodeVisitor visitor) throws MojoExecutionException, MojoFailureException {
        final Log logger = this.getLog();
        logger.info("Parsing Dependencies for: " + project.getName());
        
        //Load and parse immediate dependencies
        final DependencyNode tree = this.loadDependencyTree(project);
        tree.accept(visitor);
        
        //If not including child deps don't recurse on modules
        if (!this.includeChildDependencies) {
            return;
        }
        
        //No child modules, return
        final List<MavenProject> collectedProjects = project.getCollectedProjects();
        if (collectedProjects == null) {
            return;
        }
        
        //Find all sub-modules for the project
        for (final MavenProject moduleProject : collectedProjects) {
            if (this.isExcluded(moduleProject, project.getArtifactId())) {
                continue;
            }
            
            this.parseProject(moduleProject, visitor);
        }
    }
    
    /**
     * Check if a project is excluded based on its artifactId or a parent's artifactId
     */
    protected boolean isExcluded(MavenProject mavenProject, String rootArtifactId) {
        final Log logger = this.getLog();
        
        final String artifactId = mavenProject.getArtifactId();
        if (this.excludedModules.contains(artifactId)) {
            logger.info("Skipping aggregation of child module " +  mavenProject.getName() + " with excluded artifactId: " + artifactId);
            return true;
        }
        
        MavenProject parentProject = mavenProject.getParent();
        while (parentProject != null && !rootArtifactId.equals(parentProject.getArtifactId())) {
            final String parentArtifactId = parentProject.getArtifactId();
            if (this.excludedModules.contains(parentArtifactId)) {
                logger.info("Skipping aggregation of child module " +  mavenProject.getName() + " with excluded parent artifactId: " + parentArtifactId);
                return true;
            }
            parentProject = parentProject.getParent();
        }
        
        return false;
    }

    /**
     * Check if there are any unresolved artifacts in the Set. If there are print a helpful error
     * message and then throw a {@link MojoFailureException}
     */
    protected void checkUnresolved(Set<Artifact> unresolvedArtifacts) throws MojoFailureException {
        final Log logger = this.getLog();
        
        if (unresolvedArtifacts.isEmpty()) {
            return;
        }
        
        final LicenseLookup licenseLookup = new LicenseLookup();
        final List<ArtifactLicense> artifacts = licenseLookup.getArtifact();
        
        logger.error("Failed to find Licenses for the following dependencies: ");
        for (final Artifact unresolvedArtifact : unresolvedArtifacts) {
            logger.error("\t" + unresolvedArtifact);
            
            //Build LicenseLookup data model for artifacts that failed resolution
            final ArtifactLicense artifactLicense = new ArtifactLicense();
            artifactLicense.setGroupId(unresolvedArtifact.getGroupId());
            artifactLicense.setArtifactId(unresolvedArtifact.getArtifactId());
            
            final List<MappedVersion> mappedVersions = artifactLicense.getVersion();
            final MappedVersion mappedVersion = new MappedVersion();
            mappedVersion.setValue(unresolvedArtifact.getVersion());
            mappedVersions.add(mappedVersion);
            
            artifacts.add(artifactLicense);
        }
        logger.error("Try adding them to a 'licenseMapping' file.");
        
        final File buildDir = new File(project.getBuild().getDirectory());
        final File mappingsfile = new File(buildDir, "license-mappings.xml");
        
        //Make sure the target directory exists
        try {
            FileUtils.forceMkdir(buildDir);
        }
        catch (IOException e) {
            logger.warn("Failed to write stub license-mappings.xml file to: " + mappingsfile, e);
        }

        //Write the example mappings file
        final Marshaller marshaller = LicenseLookupContext.getMarshaller();
        try {
            marshaller.marshal(licenseLookup, mappingsfile);
            logger.error("A stub license-mapping.xml file containing the unresolved dependencies has been written to: " + mappingsfile);
        }
        catch (JAXBException e) {
            logger.warn("Failed to write stub license-mappings.xml file to: " + mappingsfile, e);
        }
        
        throw new MojoFailureException("Failed to find Licenses for " + unresolvedArtifacts.size() + " artifacts");
    }
    
    /**
     * Create the generated part of the NOTICE file based on the resolved license data
     */
    protected String generateNoticeLines(Map<String, String> resolvedLicenses, Map<String,Artifact> resolvedArtifacts) {
        final StringBuilder builder = new StringBuilder();
        
        final MessageFormat messageFormat = getNoticeMessageFormat();
        
        for (final Map.Entry<String, String> resolvedEntry : resolvedLicenses.entrySet()) {
            final Artifact artifact = resolvedArtifacts.get(resolvedEntry.getKey());
            final MavenProject mavenProject = loadProject(artifact);
            String line = null;
            if (mavenProject != null) {
                String copyright = "";
                if (mavenProject.getOrganization() != null &&
                        mavenProject.getOrganization().getName() != null &&
                        mavenProject.getInceptionYear() != null) {
                    final MessageFormat copyrightMessageFormat = getCopyrightMessageFormat();
                    final Calendar calendar = Calendar.getInstance();
                    copyright = copyrightMessageFormat.format(new Object[] {
                        mavenProject.getInceptionYear(),
                            Integer.toString(calendar.get(Calendar.YEAR)),
                            mavenProject.getOrganization().getName(),
                            (mavenProject.getOrganization().getUrl() != null ? "(" + mavenProject.getOrganization().getUrl() + ")" : "")
                    });
                }
                line = messageFormat.format(new Object[] { resolvedEntry.getKey(),
                        resolvedEntry.getValue(),
                        (mavenProject.getGroupId() != null ? mavenProject.getGroupId() : ""),
                        (mavenProject.getArtifactId() != null ? mavenProject.getArtifactId() : ""),
                        (mavenProject.getVersion() != null ? mavenProject.getVersion() : ""),
                        (mavenProject.getOrganization() != null ? (mavenProject.getOrganization().getName() != null ? mavenProject.getOrganization().getName() : "") : ""),
                        (mavenProject.getOrganization() != null ? (mavenProject.getOrganization().getUrl() != null ? mavenProject.getOrganization().getUrl() : "") : ""),
                        (copyright != null ? copyright : "")
                });
            } else {
                line = messageFormat.format(new Object[]{resolvedEntry.getKey(), resolvedEntry.getValue()});
            }
            builder.append(line).append(IOUtils.LINE_SEPARATOR);
        }
        
        return builder.toString();
    }

    /**
     * Get the {@link MessageFormat} of the configured {@link #noticeMessage}
     */
    protected final MessageFormat getNoticeMessageFormat() {
        final MessageFormat messageFormat;
        synchronized (this) {
            if (this.parsedNoticeMessage == null || !this.noticeMessage.equals(this.parsedNoticeMessage.toPattern())) {
                if (this.noticeMessage.contains("\\n")) {
                    this.noticeMessage = this.noticeMessage.replaceAll("\\\\n", "\n");
                }
                this.parsedNoticeMessage = new MessageFormat(this.noticeMessage);
            }
            messageFormat = this.parsedNoticeMessage;
        }
        return messageFormat;
    }

    /**
     * Read the template notice file into a string, converting the line ending to the current OS line endings
     */
    protected String readNoticeTemplate(ResourceFinder finder) throws MojoFailureException {
        final URL inputFile = finder.findResource(this.noticeTemplate);

        final StringBuilder noticeTemplateContents = new StringBuilder();
        InputStream inputStream = null;
        try {
            inputStream = inputFile.openStream();
            for (final LineIterator lineIterator = IOUtils.lineIterator(new BufferedInputStream(inputStream), this.encoding);
                    lineIterator.hasNext();) {
                final String line = lineIterator.next();
                noticeTemplateContents.append(line).append(IOUtils.LINE_SEPARATOR);
            }
        }
        catch (IOException e) {
            throw new MojoFailureException("Failed to open NOTICE Template File '" + this.noticeTemplate + "' from: " + inputFile, e);
        }
        finally {
            IOUtils.closeQuietly(inputStream);
        }
        
        return noticeTemplateContents.toString();
    }
    
    /**
     * Resolve the {@link File} to write the generated NOTICE file to
     */
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

    /**
     * Create the {@link ResourceFinderImpl} for the project
     */
    @SuppressWarnings("unchecked")
    protected ResourceFinder getResourceFinder() throws MojoExecutionException {
        final ResourceFinder finder = new ResourceFinderImpl(this.project);
        try {
            final List<String> classpathElements = this.project.getCompileClasspathElements();
            finder.setCompileClassPath(classpathElements);
        }
        catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        finder.setPluginClassPath(getClass().getClassLoader());
        return finder;
    }

    /**
     * Load the dependency tree for the specified project
     */
    protected DependencyNode loadDependencyTree(MavenProject project) throws MojoExecutionException {
        try {
            return this.dependencyTreeBuilder.buildDependencyTree(
                    project, this.localRepository, 
                    this.artifactFactory, this.artifactMetadataSource, 
                    null, this.artifactCollector);
        }
        catch (DependencyTreeBuilderException e) {
            throw new MojoExecutionException("Cannot build project dependency tree for project: " + project, e );
        }
    }

    protected MavenProject loadProject(final Artifact artifact) {
        final Log logger = this.getLog();
        if (artifact == null) {
            return null;
        }
        try {
            return mavenProjectBuilder.buildFromRepository(artifact, project.getRemoteArtifactRepositories(), localRepository, false);
        }
        catch (ProjectBuildingException e) {
            logger.warn("Failed to find license info for: " + artifact);
        }
        return null;
    }

    /**
     * Get the {@link MessageFormat} of the configured {@link #copyrightMessage}
     */
    protected final MessageFormat getCopyrightMessageFormat() {
        final MessageFormat messageFormat;
        synchronized (this) {
            if (this.parsedCopyrightMessage == null || !this.copyrightMessage.equals(this.parsedCopyrightMessage.toPattern())) {
                if (this.copyrightMessage.contains("\\n")) {
                    this.copyrightMessage = this.copyrightMessage.replaceAll("\\\\n", "\n");
                }
                this.parsedCopyrightMessage = new MessageFormat(this.copyrightMessage);
            }
            messageFormat = this.parsedCopyrightMessage;
        }
        return messageFormat;
    }

    /**
     * Get the {@link MessageFormat} of the configured {@link #licenseSummaryMessage}
     */
    protected final MessageFormat getLicenseSummaryMessageFormat() {
        final MessageFormat messageFormat;
        synchronized (this) {
            if (this.parsedLicenseSummaryMessage == null || !this.licenseSummaryMessage.equals(this.parsedLicenseSummaryMessage.toPattern())) {
                if (this.licenseSummaryMessage.contains("\\n")) {
                    this.licenseSummaryMessage = this.licenseSummaryMessage.replaceAll("\\\\n", "\n");
                }
                this.parsedLicenseSummaryMessage = new MessageFormat(this.licenseSummaryMessage);
            }
            messageFormat = this.parsedLicenseSummaryMessage;
        }
        return messageFormat;
    }

    /**
     * Create the generated part of the NOTICE file based on the summary of resolved licenses
     */
    protected String generateLicenseSummaryLines(Map<String, String> resolvedLicenses) {
        final StringBuilder builder = new StringBuilder();

        Map<String, Integer> licenseSummary = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);

        for (final Map.Entry<String, String> resolvedEntry : resolvedLicenses.entrySet()) {
            Integer licenseCount = licenseSummary.get(resolvedEntry.getValue());
            if (licenseCount == null) {
                licenseCount = 0;
            }
            licenseCount++;
            licenseSummary.put(resolvedEntry.getValue(), licenseCount);
        }

        final MessageFormat licenseSummaryMessageFormat = getLicenseSummaryMessageFormat();

        int index = 1;
        for (final Map.Entry<String, Integer> licenseSummaryEntry : licenseSummary.entrySet()) {
            final String line = parsedLicenseSummaryMessage.format(new Object[] { index, licenseSummaryEntry.getKey(), licenseSummaryEntry.getValue()});
            index++;
            builder.append(line).append(IOUtils.LINE_SEPARATOR);
        }

        return builder.toString();
    }

}
