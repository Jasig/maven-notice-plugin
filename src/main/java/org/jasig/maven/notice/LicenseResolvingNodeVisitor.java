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

import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.traversal.DependencyNodeVisitor;
import org.jasig.maven.notice.lookup.ArtifactLicense;

class LicenseResolvingNodeVisitor implements DependencyNodeVisitor {
    private final LicenseLookupHelper licenseLookupHelper;
    private final List<?> remoteArtifactRepositories;
    private final MavenProjectBuilder mavenProjectBuilder;
    private final ArtifactRepository localRepository;

    LicenseResolvingNodeVisitor(
            LicenseLookupHelper licenseLookupHelper, 
            List<?> remoteArtifactRepositories,
            MavenProjectBuilder mavenProjectBuilder,
            ArtifactRepository localRepository) {
        
        this.licenseLookupHelper = licenseLookupHelper;
        this.remoteArtifactRepositories = remoteArtifactRepositories;
        this.mavenProjectBuilder = mavenProjectBuilder;
        this.localRepository = localRepository;
    }

    public boolean visit(DependencyNode node) {
        if (DependencyNode.INCLUDED == node.getState()) {
            final Artifact artifact = node.getArtifact();
            
            final String groupId = artifact.getGroupId();
            final String artifactId = artifact.getArtifactId();
            final DefaultArtifactVersion version = new DefaultArtifactVersion(artifact.getVersion());
            final ArtifactLicense licenseMapping = licenseLookupHelper.lookupLicenseMapping(groupId, artifactId, version);
            
            String name = null;
            String licenseName = null;
            
            if (licenseMapping != null) {
                name = licenseMapping.getName();
                licenseName = licenseMapping.getLicense();
            }
            
            if (name == null || licenseName == null) {
                final MavenProject artifactProject;
                try {
                    artifactProject = mavenProjectBuilder.buildFromRepository(artifact, remoteArtifactRepositories, localRepository, false);
                }
                catch (ProjectBuildingException e) {
                    System.err.println("Failed to find license info for: " + artifact);
                    return true;
                }
                
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
                        for (final License license : licenses) {
                            licenseNameBuilder.append(license.getName()).append("\t");
                        }
                        licenseName = licenseNameBuilder.toString();
                    }
                }
            }
            
            if (name == null || licenseName == null) {
                throw new RuntimeException("Either name or license missing for: " + artifact);
            }
            
            System.out.println("\t" + name + " under the " + licenseName);
        }
        return true;
    }

    public boolean endVisit(DependencyNode node) {
        return true;
    }
}