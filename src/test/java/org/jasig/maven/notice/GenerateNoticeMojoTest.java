package org.jasig.maven.notice;

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

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.stubs.StubArtifactRepository;

/**
 * @author Edwin Punzalan
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @version $Id$
 */
public class GenerateNoticeMojoTest extends AbstractMojoTestCase {
    private File repoDir;
    private File pluginXmlFile;
    

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        
        this.pluginXmlFile = new File( getBasedir(), "src/test/resources/plugin-configs/notice-plugin-config.xml");
        this.repoDir = new File(getBasedir(), "target" + File.separatorChar + "unit-tests" + File.separatorChar + "notice" + File.separatorChar);
    }

    public void testReport() throws Exception {
        Mojo mojo = lookupMojo( "generate", pluginXmlFile );
        assertNotNull( "Mojo found.", mojo );
        
        setVariableValueToObject( mojo, "localRepository", new StubArtifactRepository( repoDir.getAbsolutePath() ) );

        mojo.execute();
    }
}
