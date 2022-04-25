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

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.jasig.maven.notice.lookup.ArtifactLicense;

class LicenseResolvingNodeVisitor implements DependencyNodeVisitor {
    private final Set<ArtifactLicenseInfo> resolvedLicenses =
            new TreeSet<ArtifactLicenseInfo>(
                    new Comparator<ArtifactLicenseInfo>() {
                        public int compare(ArtifactLicenseInfo i1, ArtifactLicenseInfo i2) {
                            return String.CASE_INSENSITIVE_ORDER.compare(
                                    i1.getArtifactName(), i2.getArtifactName());
                        }
                    });
    private final Set<Artifact> unresolvedArtifacts = new TreeSet<Artifact>();
    private final Set<Artifact> visitedArtifacts = new HashSet<Artifact>();

    private final Log logger;
    private final LicenseLookupHelper licenseLookupHelper;
    private final List<ArtifactRepository> remoteArtifactRepositories;
    private final MavenProjectBuilder mavenProjectBuilder;
    private final ArtifactRepository localRepository;

    LicenseResolvingNodeVisitor(
            Log logger,
            LicenseLookupHelper licenseLookupHelper,
            List<ArtifactRepository> remoteArtifactRepositories,
            MavenProjectBuilder mavenProjectBuilder,
            ArtifactRepository localRepository) {

        this.logger = logger;
        this.licenseLookupHelper = licenseLookupHelper;
        this.remoteArtifactRepositories = remoteArtifactRepositories;
        this.mavenProjectBuilder = mavenProjectBuilder;
        this.localRepository = localRepository;
    }

    public Set<ArtifactLicenseInfo> getResolvedLicenses() {
        return resolvedLicenses;
    }

    public Set<Artifact> getUnresolvedArtifacts() {
        return unresolvedArtifacts;
    }

    public boolean visit(DependencyNode node) {
        final Artifact artifact = node.getArtifact();

        // Only resolve an artifact once, if already visited just skip it
        if (!visitedArtifacts.add(artifact)) {
            return true;
        }

        String name = null;
        String licenseName = null;

        // Look for a matching mapping first
        final ResolvedLicense resolvedLicense = this.loadLicenseMapping(artifact);
        if (resolvedLicense != null && resolvedLicense.getVersionType() != null) {
            final ArtifactLicense artifactLicense = resolvedLicense.getArtifactLicense();
            name = StringUtils.trimToNull(artifactLicense.getName());
            licenseName = StringUtils.trimToNull(artifactLicense.getLicense());
        }

        // If name or license are still null try loading from the project
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
                    } else if (licenses.size() > 1) {
                        final StringBuilder licenseNameBuilder = new StringBuilder();
                        for (final Iterator<License> licenseItr = licenses.iterator();
                                licenseItr.hasNext(); ) {
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

        // Try fall-back match for name & license, hitting this implies the resolved license was
        // an all-versions match
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

        // If no name is found fall back to groupId:artifactId
        if (name == null) {
            name = artifact.getGroupId() + ":" + artifact.getArtifactId();
        }

        // Record the artifact resolution outcome
        if (licenseName == null) {
            this.unresolvedArtifacts.add(artifact);
        } else {
            this.resolvedLicenses.add(
                    new ArtifactLicenseInfo(
                            name,
                            licenseName,
                            node.getArtifact().getScope(),
                            hasOptionalLicense(node)));
        }
        return true;
    }

    /**
     * Check if the given node or any of its parents are optional. i.e. if your POM declares an
     * optional dependency A with a transitive dependency B:
     *
     * <ul>
     *   <li>A will return true because it is optional
     *   <li>B will have A as its parent, and so it will also be optional
     *   <li>If B were to be directly declared as a non-optional dependency, then this method would
     *       return false.
     * </ul>
     */
    private boolean hasOptionalLicense(DependencyNode node) {
        while (node != null && node.getArtifact() != null) {
            if (node.getArtifact().isOptional()) {
                return true;
            }
            node = node.getParent();
        }

        return false;
    }

    protected ResolvedLicense loadLicenseMapping(final Artifact artifact) {
        final String groupId = artifact.getGroupId();
        final String artifactId = artifact.getArtifactId();
        final DefaultArtifactVersion version = new DefaultArtifactVersion(artifact.getVersion());
        final ResolvedLicense resolvedLicense =
                licenseLookupHelper.lookupLicenseMapping(groupId, artifactId, version);
        return resolvedLicense;
    }

    protected MavenProject loadProject(final Artifact artifact) {
        try {
            return mavenProjectBuilder.buildFromRepository(
                    artifact, remoteArtifactRepositories, localRepository, false);
        } catch (ProjectBuildingException e) {
            this.logger.warn(
                    String.format(
                            "Failed to find license info for: %s; cause: %s",
                            artifact, e.getMessage()));
            this.logger.debug(String.format("Failed to find license info for: %s", artifact), e);
        }
        return null;
    }

    public boolean endVisit(DependencyNode node) {
        return true;
    }
}
