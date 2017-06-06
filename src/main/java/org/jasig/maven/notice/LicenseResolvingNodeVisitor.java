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

import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.traversal.DependencyNodeVisitor;
import org.jasig.maven.notice.lookup.ArtifactLicense;

class LicenseResolvingNodeVisitor implements DependencyNodeVisitor {
    private final Map<String, String> resolvedLicenses = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
    private final Set<Artifact> unresolvedArtifacts = new TreeSet<Artifact>();
    private final Set<Artifact> visitedArtifacts = new HashSet<Artifact>();
    private final Map<String, Artifact> resolvedArtifacts = new TreeMap<String,Artifact>(String.CASE_INSENSITIVE_ORDER);
    
    private final Log logger;
    private final LicenseLookupHelper licenseLookupHelper;
    private final List<?> remoteArtifactRepositories;
    private final MavenProjectBuilder mavenProjectBuilder;
    private final ArtifactRepository localRepository;
    private final Properties licenseNameAliases;
    
    LicenseResolvingNodeVisitor(Log logger,
            LicenseLookupHelper licenseLookupHelper, 
            List<?> remoteArtifactRepositories,
            MavenProjectBuilder mavenProjectBuilder,
            ArtifactRepository localRepository,
            Properties licenseNameAliases) {
        
        this.logger = logger;
        this.licenseLookupHelper = licenseLookupHelper;
        this.remoteArtifactRepositories = remoteArtifactRepositories;
        this.mavenProjectBuilder = mavenProjectBuilder;
        this.localRepository = localRepository;
        this.licenseNameAliases = licenseNameAliases;
    }
    
    public Map<String, String> getResolvedLicenses() {
        return resolvedLicenses;
    }

    public Set<Artifact> getUnresolvedArtifacts() {
        return unresolvedArtifacts;
    }

    public Map<String, Artifact> getResolvedArtifacts() {
        return resolvedArtifacts;
    }

    public boolean visit(DependencyNode node) {
        if (DependencyNode.INCLUDED == node.getState()) {
            final Artifact artifact = node.getArtifact();
            
            //Only resolve an artifact once, if already visited just skip it
            if (!visitedArtifacts.add(artifact)) {
                return true;
            }
            
            String name = null;
            String licenseName = null;
            
            //Look for a matching mapping first
            final ResolvedLicense resolvedLicense = this.loadLicenseMapping(artifact);
            if (resolvedLicense != null && resolvedLicense.getVersionType() != null) {
                final ArtifactLicense artifactLicense = resolvedLicense.getArtifactLicense();
                name = StringUtils.trimToNull(artifactLicense.getName());
                licenseName = StringUtils.trimToNull(artifactLicense.getLicense());
            }

            //If name or license are still null try loading from the project
            if (name == null || licenseName == null) {
                final MavenProject artifactProject = this.loadProject(artifact);
                if (artifactProject != null) {
                    if (name == null) {
                        name = artifactProject.getName();
                    }
                    
                    if (licenseName == null) {
                        final Model model = artifactProject.getModel();
                        final List<License> licenses = model.getLicenses();
                        
                        if (licenses.size() == 1) {
                            licenseName = licenses.get(0).getName();
                        }
                        else if (licenses.size() > 1) {
                            final StringBuilder licenseNameBuilder = new StringBuilder();
                            for (final Iterator<License> licenseItr = licenses.iterator(); licenseItr.hasNext(); ) {
                                final License license = licenseItr.next();
                                licenseNameBuilder.append(license.getName());
                                if (licenseItr.hasNext()) {
                                    licenseNameBuilder.append(" or ");
                                }
                            }
                            licenseName = licenseNameBuilder.toString();
                        }
                    }
                }
            }
            
            //Try fall-back match for name & license, hitting this implies the resolved license was an all-versions match
            if (resolvedLicense != null && (licenseName == null || name == null)) {
                final ArtifactLicense artifactLicense = resolvedLicense.getArtifactLicense();
                if (name == null) {
                    name = StringUtils.trimToNull(artifactLicense.getName());
                }
                if (licenseName == null) {
		    if (artifactLicense != null) {
                    	licenseName = StringUtils.trimToNull(artifactLicense.getLicense());
		    }
                }
            }
            
            //If no name is found fall back to groupId:artifactId
            if (name == null) {
                name = artifact.getGroupId() + ":" + artifact.getArtifactId();
            }

            // do we have an alias for the resolved license name ?
            if (licenseNameAliases != null && licenseNameAliases.size() > 0 && licenseNameAliases.getProperty(licenseName) != null) {
                // yes, we use the alias.
                licenseName = licenseNameAliases.getProperty(licenseName);
            }
            
            //Record the artifact resolution outcome
            if (licenseName == null) {
                this.unresolvedArtifacts.add(artifact);
            }
            else {
                this.resolvedLicenses.put(name, licenseName);
                this.resolvedArtifacts.put(name, artifact);
            }
        }
        return true;
    }

    protected ResolvedLicense loadLicenseMapping(final Artifact artifact) {
        final String groupId = artifact.getGroupId();
        final String artifactId = artifact.getArtifactId();
        final DefaultArtifactVersion version = new DefaultArtifactVersion(artifact.getVersion());
        final ResolvedLicense resolvedLicense = licenseLookupHelper.lookupLicenseMapping(groupId, artifactId, version);
        return resolvedLicense;
    }

    protected MavenProject loadProject(final Artifact artifact) {
        try {
            return mavenProjectBuilder.buildFromRepository(artifact, remoteArtifactRepositories, localRepository, false);
        }
        catch (ProjectBuildingException e) {
            this.logger.warn(String.format("Failed to find license info for: %s; cause: %s", artifact, e.getMessage()));
            this.logger.debug(String.format("Failed to find license info for: %s", artifact), e);
        }
        return null;
    }

    public boolean endVisit(DependencyNode node) {
        return true;
    }
}
