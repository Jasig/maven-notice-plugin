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
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilderException;
import org.apache.maven.shared.dependency.tree.traversal.DependencyNodeVisitor;

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
    protected MavenProject project;

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
    
    /* (non-Javadoc)
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        final DependencyNode tree;
        try {
            tree = this.dependencyTreeBuilder.buildDependencyTree(this.project, this.localRepository, 
                    this.artifactFactory, this.artifactMetadataSource, null, this.artifactCollector);
        }
        catch (DependencyTreeBuilderException e) {
            throw new MojoExecutionException( "Cannot build project dependency tree", e );
        }

        final List<?> remoteArtifactRepositories = project.getRemoteArtifactRepositories();

        tree.accept(new DependencyNodeVisitor() {
            public boolean visit(DependencyNode node) {
                if (DependencyNode.INCLUDED == node.getState()) {
                    final Artifact artifact = node.getArtifact();
                    System.out.println(artifact);
                    
                    final MavenProject artifactProject;
                    try {
                        artifactProject = mavenProjectBuilder.buildFromRepository(artifact, remoteArtifactRepositories, localRepository, false);
                    }
                    catch (ProjectBuildingException e) {
                        System.err.println("Failed to find license info for: " + artifact);
                        return true;
                    }
                    
                    final Model model = artifactProject.getModel();
                    final List<License> licenses = model.getLicenses();
                    for (final License license : licenses) {
                        System.out.println("\t" + artifactProject.getName() + " under the " + license.getName());
                    }
                }
                return true;
            }
            
            public boolean endVisit(DependencyNode node) {
                return true;
            }
        });
    }
}
