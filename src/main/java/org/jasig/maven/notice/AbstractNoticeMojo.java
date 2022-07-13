/**
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.jasig.maven.notice.lookup.ArtifactLicense;
import org.jasig.maven.notice.lookup.LicenseLookup;
import org.jasig.maven.notice.lookup.MappedVersion;
import org.jasig.maven.notice.util.ResourceFinder;
import org.jasig.maven.notice.util.ResourceFinderImpl;

import org.apache.maven.plugins.annotations.Component;

/**
 * Common base mojo for notice related plugins
 *
 * @author Eric Dalquist
 */
public abstract class AbstractNoticeMojo extends AbstractMojo {

    /* DI configuration of Maven components needed for the plugin */

    /**
     * The Maven Project.
     */
    @Parameter( defaultValue = "${project}", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The dependency graph builder to use.
     */
    @Component( hint = "default" )
    private DependencyGraphBuilder dependencyGraphBuilder;

    /**
     * The artifact repository to use.
     */
    @Parameter(required = true, readonly = true, property = "localRepository")
    protected ArtifactRepository localRepository;

    /**
     * The artifact factory to use.
     */
    @Component
    protected ArtifactFactory artifactFactory;

    /**
     * The artifact metadata source to use.
     */
    @Component
    protected ArtifactMetadataSource artifactMetadataSource;

    /**
     * The artifact collector to use.
     */
    @Component
    protected ArtifactCollector artifactCollector;

    /**
     * Maven Project Builder component.
     */
    @Component
    protected MavenProjectBuilder mavenProjectBuilder;

    @Parameter ( defaultValue = "${repositorySystem}" )
    RepositorySystem repoSystem;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private MavenSession session;

    @Parameter( defaultValue = "${repositorySystemSession}" )
    private RepositorySystemSession repoSession;
    /* Mojo Configuration Properties */

    /**
     * Use licenseMapping.
     * @deprecated use licenseMapping
     */
    @Parameter
    @Deprecated protected String[] licenseLookup = new String[0];

    /**
     * Parameter to skip running checks entirely.
     */
    @Parameter( property = "skip.checks")
    private boolean skipChecks = false;

    /**
     * License Mapping XML files / URLs. Lookups are done in-order with files being checked top to
     * bottom for matches.
     *
     */
    @Parameter
    protected String[] licenseMapping = new String[0];

    /**
     * Template for NOTICE file generation.
     */
    @Parameter( defaultValue = "NOTICE.template")
    protected String noticeTemplate = "NOTICE.template";

    /**
     * Placeholder string in the NOTICE template file.
     */
    @Parameter( defaultValue = "#GENERATED_NOTICES#")
    protected String noticeTemplatePlaceholder = "#GENERATED_NOTICES#";

    /**
     * List of scopes, like "compile", "test", etc. If specified, only dependencies with these
     * scopes will be listed in the NOTICE file.
     */
    @Parameter
    protected Set<String> includeScopes = new TreeSet<String>();

    /**
     * List of scopes, like "compile", "test", etc. If specified, dependencies with these scopes
     * will be omitted from the NOTICE file.
     */
    @Parameter
    protected Set<String> excludeScopes = new TreeSet<String>();

    /**
     * Output location for the generated NOTICE file.
     */
    @Parameter( defaultValue = "${basedir}")
    protected String outputDir = "";

    /**
     * Output file name.
     */
    @Parameter( defaultValue = "NOTICE")
    protected String fileName = "NOTICE";

    /**
     * Output file encoding.
     */
    @Parameter( defaultValue = "${project.build.sourceEncoding}")
    protected String encoding = "UTF-8";

    /**
     * Set if the NOTICE file should include all dependencies from all child modules.
     */
    @Parameter( defaultValue = "true")
    protected boolean includeChildDependencies = true;

    /**
     * Set if a NOTICE file should be generated for each child module.
     */
    @Parameter( defaultValue = "true")
    protected boolean generateChildNotices = true;

    /**
     * The {@link MessageFormat} syntax string used to generate each license line in the NOTICE file.
     * <br>
     * {0} - artifact name<br>
     * {1} - license name<br>
     */
    @Parameter( defaultValue = "  {0} under {1}")
    protected String noticeMessage = "  {0} under {1}";

    private MessageFormat parsedNoticeMessage;

    /**
     * ArtifactIds of child modules to exclude.
     */
    @Parameter
    protected Set<String> excludedModules = new LinkedHashSet<String>();

    /**
     * Whether to exclude optional dependencies and any transitive dependencies of those.<br>
     * For example, if your POM declares an optional dependency A with a transitive dependency B:
     *
     * <ul>
     *   <li>A will be excluded as it is directly defined as an optional dependency.
     *   <li>B will be excluded as it is resolved via A, which is optional.
     *   <li>However, if B were to be explicitly declared as a non-optional dependency in your pom,
     *       then it would be included.
     * </ul>
     */
    @Parameter
    protected boolean excludeOptional;

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
            logger.warn(
                    "'licenseLookup' configuration property is deprecated use 'licenseMapping' instead");
            if (licenseMapping != null && licenseMapping.length > 0) {
                throw new MojoFailureException(
                        "Both 'licenseMapping' and 'licenseLookup' configuration properties configured. Only one may be used.");
            }
            licenseMapping = licenseLookup;
        }

        // Check if NOTICE for child modules should be generated
        if (!this.generateChildNotices && !this.project.isExecutionRoot()) {
            return;
        }

        final ResourceFinder finder = this.getResourceFinder();

        final LicenseLookupHelper licenseLookupHelper =
                new LicenseLookupHelper(logger, finder, licenseMapping);

        final List<ArtifactRepository> remoteArtifactRepositories = project.getRemoteArtifactRepositories();

        final LicenseResolvingNodeVisitor visitor =
                new LicenseResolvingNodeVisitor(
                        logger,
                        licenseLookupHelper,
                        remoteArtifactRepositories,
                        this.mavenProjectBuilder,
                        this.localRepository);

        this.parseProject(this.project, visitor);

        // Check for any unresolved artifacts
        final Set<Artifact> unresolvedArtifacts = visitor.getUnresolvedArtifacts();
        this.checkUnresolved(unresolvedArtifacts);

        // Convert the resovled notice data into a String
        final Set<ArtifactLicenseInfo> resolvedLicenses = visitor.getResolvedLicenses();
        final String noticeLines = this.generateNoticeLines(resolvedLicenses);
        final String noticeTemplateContents = this.readNoticeTemplate(finder);

        // Replace the template placeholder with the generated notice data
        final String noticeContents =
                noticeTemplateContents.replace(this.noticeTemplatePlaceholder, noticeLines);

        // Let the subclass deal with the generated NOTICE file
        this.handleNotice(finder, noticeContents);
    }

    /*
     * Called with the expected NOTICE file contents for this project.
     * 
     * @param finder
     * 
     * @param noticeContents
     * 
     * @throws MojoFailureException
     */
    protected abstract void handleNotice(ResourceFinder finder, String noticeContents)
            throws MojoFailureException;

    /**
     * Loads the dependency tree for the project via
     * {@link #loadDependencyTree(MavenProject)} and
     * then uses the {@link DependencyNodeVisitor} to load the license data. If
     * {@link #includeChildDependencies}
     * is enabled the method recurses on each child module.
     * 
     * @param project MavenProject
     * @param visitor DependencyNodeVisitor
     * 
     * @throws MojoExecutionException exception
     * @throws MojoFailureException exception
     */
    @SuppressWarnings("unchecked")
    protected void parseProject(MavenProject project, DependencyNodeVisitor visitor)
            throws MojoExecutionException, MojoFailureException {
        final Log logger = this.getLog();
        logger.info("Parsing Dependencies for: " + project.getName());

        // Load and parse immediate dependencies
        final DependencyNode tree = this.loadDependencyTree(project);
        tree.accept(visitor);

        // If not including child deps don't recurse on modules
        if (!this.includeChildDependencies) {
            return;
        }

        // No child modules, return
        final List<MavenProject> collectedProjects = project.getCollectedProjects();
        if (collectedProjects == null) {
            return;
        }

        // Find all sub-modules for the project
        for (final MavenProject moduleProject : collectedProjects) {
            if (this.isExcluded(moduleProject, project.getArtifactId())) {
                continue;
            }

            this.parseProject(moduleProject, visitor);
        }
    }

    /*
     * Check if a project is excluded based on its artifactId or a parent's
     * artifactId
     * 
     * @param mavenProject MavenProject
     * 
     * @param rootArtifactId String
     */
    protected boolean isExcluded(MavenProject mavenProject, String rootArtifactId) {
        final Log logger = this.getLog();

        final String artifactId = mavenProject.getArtifactId();
        if (this.excludedModules.contains(artifactId)) {
            logger.info(
                    "Skipping aggregation of child module "
                            + mavenProject.getName()
                            + " with excluded artifactId: "
                            + artifactId);
            return true;
        }

        MavenProject parentProject = mavenProject.getParent();
        while (parentProject != null && !rootArtifactId.equals(parentProject.getArtifactId())) {
            final String parentArtifactId = parentProject.getArtifactId();
            if (this.excludedModules.contains(parentArtifactId)) {
                logger.info(
                        "Skipping aggregation of child module "
                                + mavenProject.getName()
                                + " with excluded parent artifactId: "
                                + parentArtifactId);
                return true;
            }
            parentProject = parentProject.getParent();
        }

        return false;
    }

    /**
     * Check if there are any unresolved artifacts in the Set. If there are print a
     * helpful error
     * message and then throw a {@link MojoFailureException}
     * 
     * @param unresolvedArtifacts Set of Artifact
     * 
     * @throws MojoFailureException exception
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

            // Build LicenseLookup data model for artifacts that failed resolution
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

        // Make sure the target directory exists
        try {
            FileUtils.forceMkdir(buildDir);
        } catch (IOException e) {
            logger.warn("Failed to write stub license-mappings.xml file to: " + mappingsfile, e);
        }

        // Write the example mappings file
        final Marshaller marshaller = LicenseLookupContext.getMarshaller();
        try {
            marshaller.marshal(licenseLookup, mappingsfile);
            logger.error(
                    "A stub license-mapping.xml file containing the unresolved dependencies has been written to: "
                            + mappingsfile);
        } catch (JAXBException e) {
            logger.warn("Failed to write stub license-mappings.xml file to: " + mappingsfile, e);
        }

        throw new MojoFailureException(
                "Failed to find Licenses for " + unresolvedArtifacts.size() + " artifacts");
    }

    /**
     * Create the generated part of the NOTICE file based on the resolved license
     * data
     * 
     * @param resolvedLicenses Set of ArtifactLicenseInfo
     * @return String the generated notice lines
     */
    protected String generateNoticeLines(Set<ArtifactLicenseInfo> resolvedLicenses) {
        final StringBuilder builder = new StringBuilder();

        final MessageFormat messageFormat = getNoticeMessageFormat();

        for (final ArtifactLicenseInfo resolvedLicense : resolvedLicenses) {
            if (!includeScopes.isEmpty()) {
                if (resolvedLicense.getScope() == null
                        || !includeScopes.contains(resolvedLicense.getScope())) {
                    continue;
                }
            }
            if (!excludeScopes.isEmpty()) {
                if (resolvedLicense.getScope() != null
                        && excludeScopes.contains(resolvedLicense.getScope())) {
                    continue;
                }
            }
            if (excludeOptional && resolvedLicense.isOptional()) {
                continue;
            }
            final String line =
                    messageFormat.format(
                            new Object[] {
                                resolvedLicense.getArtifactName(), resolvedLicense.getLicenseName()
                            });
            builder.append(line).append(IOUtils.LINE_SEPARATOR);
        }

        return builder.toString();
    }

    /**
     * Get the {@link MessageFormat} of the configured {@link #noticeMessage}
     * 
     * @return MessageFormat the Notice Message Format
     */
    protected final MessageFormat getNoticeMessageFormat() {
        final MessageFormat messageFormat;
        synchronized (this) {
            if (this.parsedNoticeMessage == null
                    || !this.noticeMessage.equals(this.parsedNoticeMessage.toPattern())) {
                this.parsedNoticeMessage = new MessageFormat(this.noticeMessage);
            }
            messageFormat = this.parsedNoticeMessage;
        }
        return messageFormat;
    }

    /**
     * Read the template notice file into a string, converting the line ending to
     * the current OS
     * line endings
     * 
     * @param finder ResourceFinder
     * @throws MojoFailureException exception
     * @return String the Notice Template Content
     */
    protected String readNoticeTemplate(ResourceFinder finder) throws MojoFailureException {
        final URL inputFile = finder.findResource(this.noticeTemplate);


        final StringBuilder noticeTemplateContents = new StringBuilder();
        InputStream inputStream = null;
        try {
            inputStream = inputFile.openStream();
            for (final LineIterator lineIterator =
                            IOUtils.lineIterator(
                                    new BufferedInputStream(inputStream), this.encoding);
                    lineIterator.hasNext(); ) {
                final String line = lineIterator.next();
                noticeTemplateContents.append(line).append(IOUtils.LINE_SEPARATOR);
            }
        } catch (IOException e) {
            throw new MojoFailureException(
                    "Failed to open NOTICE Template File '"
                            + this.noticeTemplate
                            + "' from: "
                            + inputFile,
                    e);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }

        return noticeTemplateContents.toString();
    }

    /**
     * Resolve the {@link File} to write the generated NOTICE file to
     * 
     * @return File the generated NOTICE file
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
     *
     * @throws MojoExecutionException exception
     * 
     * @return ResourceFinder The project ressources
     */
    @SuppressWarnings("unchecked")
    protected ResourceFinder getResourceFinder() throws MojoExecutionException {
        final ResourceFinder finder = new ResourceFinderImpl(this.project);
        try {
            final List<String> classpathElements = this.project.getCompileClasspathElements();
            finder.setCompileClassPath(classpathElements);
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        finder.setPluginClassPath(getClass().getClassLoader());
        return finder;
    }

    /**
     * Load the dependency tree for the specified project
     * 
     * @param project MavenProject
     * 
     * @throws MojoExecutionException exception
     * 
     * @return DependencyNode the dependency tree for the specified project
     */
    protected DependencyNode loadDependencyTree(MavenProject project)
            throws MojoExecutionException {
        try {

            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );
            buildingRequest.setProject(project);

            return this.dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null);
        } catch (DependencyGraphBuilderException e) {
            throw new MojoExecutionException(
                    "Cannot build project dependency tree for project: " + project, e);
        }
    }
}
