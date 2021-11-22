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
package org.jasig.maven.notice.stubs;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Build;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.codehaus.plexus.util.ReaderFactory;

/**
 * @author Edwin Punzalan
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @author Nick Stolwijk
 * @version $Id$
 */
public abstract class BasicProjectStub extends MavenProjectStub {
    private Model model;

    private Build build;

    /** Default constructor */
    public BasicProjectStub() {
        MavenXpp3Reader pomReader = new MavenXpp3Reader();
        try {
            model = pomReader.read(ReaderFactory.newXmlReader(new File(getBasedir(), getPOM())));
            setModel(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        setGroupId(model.getGroupId());
        setArtifactId(model.getArtifactId());
        setVersion(model.getVersion());
        setName(model.getName());
        setUrl(model.getUrl());
        setPackaging(model.getPackaging());

        Artifact artifact =
                new PluginArtifactStub(getGroupId(), getArtifactId(), getVersion(), getPackaging());
        artifact.setArtifactHandler(new DefaultArtifactHandlerStub());
        setArtifact(artifact);

        Build build = new Build();
        build.setFinalName(model.getArtifactId());
        build.setDirectory(
                super.getBasedir() + "/target/test/unit/" + model.getArtifactId() + "/target");
        build.setSourceDirectory(getBasedir() + "/src/main/java");
        build.setOutputDirectory(
                super.getBasedir()
                        + "/target/test/unit/"
                        + model.getArtifactId()
                        + "/target/classes");
        build.setTestSourceDirectory(getBasedir() + "/src/test/java");
        build.setTestOutputDirectory(
                super.getBasedir()
                        + "/target/test/unit/"
                        + model.getArtifactId()
                        + "/target/test-classes");
        setBuild(build);

        List<String> compileSourceRoots = new ArrayList<String>();
        compileSourceRoots.add(getBasedir() + "/src/main/java");
        setCompileSourceRoots(compileSourceRoots);

        List<String> testCompileSourceRoots = new ArrayList<String>();
        testCompileSourceRoots.add(getBasedir() + "/src/test/java");
        setTestCompileSourceRoots(testCompileSourceRoots);
    }

    /** @return the POM file name */
    protected abstract String getPOM();

    /** {@inheritDoc} */
    @Override
    public Model getModel() {
        return model;
    }

    /** {@inheritDoc} */
    @Override
    public Build getBuild() {
        return build;
    }

    /** {@inheritDoc} */
    @Override
    public void setBuild(Build build) {
        this.build = build;
    }

    /** {@inheritDoc} */
    @Override
    public File getBasedir() {
        return new File(super.getBasedir() + "/src/test/resources/plugin-configs/");
    }

    /** {@inheritDoc} */
    @Override
    public Set<Artifact> getArtifacts() {
        return Collections.emptySet();
    }

    /** {@inheritDoc} */
    @Override
    public List<ArtifactRepository> getRemoteArtifactRepositories() {
        ArtifactRepository repository =
                new DefaultArtifactRepository(
                        "central", "https://repo1.maven.org/maven2", new DefaultRepositoryLayout());

        return Collections.singletonList(repository);
    }

    /** {@inheritDoc} */
    @Override
    public Set<Artifact> getDependencyArtifacts() {
        Artifact artifact1 =
                new DefaultArtifact(
                        "junit",
                        "junit",
                        VersionRange.createFromVersion("3.8.1"),
                        Artifact.SCOPE_TEST,
                        "jar",
                        null,
                        new DefaultArtifactHandler("jar"),
                        false);

        Artifact artifact2 =
                new DefaultArtifact(
                        "org.apache.maven",
                        "maven-project",
                        VersionRange.createFromVersion("2.2.0"),
                        Artifact.SCOPE_COMPILE,
                        "jar",
                        null,
                        new DefaultArtifactHandler("jar"),
                        false);

        final List<Artifact> artifacts = Arrays.asList(artifact1, artifact2);
        return Collections.unmodifiableSet(new LinkedHashSet<Artifact>(artifacts));
    }

    /** {@inheritDoc} */
    @Override
    public DependencyManagement getDependencyManagement() {
        return model.getDependencyManagement();
    }

    /** {@inheritDoc} */
    @Override
    public PluginManagement getPluginManagement() {
        PluginManagement pluginMgmt = null;

        Build build = model.getBuild();
        if (build != null) {
            pluginMgmt = build.getPluginManagement();
        }

        return pluginMgmt;
    }

    @Override
    public boolean isExecutionRoot() {
        return true;
    }
}
