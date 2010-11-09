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

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.stubs.StubArtifactRepository;

public class CheckNoticeMojoTest extends AbstractMojoTestCase {
    private File repoDir;
    private File pluginXmlFile;
    private File generatedNoticeFile;
    

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        
        this.pluginXmlFile = new File( getBasedir(), "src/test/resources/plugin-configs/notice-plugin-config.xml");
        this.repoDir = new File(getBasedir(), "target" + File.separatorChar + "unit-tests" + File.separatorChar + "test-repo" + File.separatorChar + "check");
        
        final File testRepo = new File( getBasedir(), "src/test/resources/test-repo");
        FileUtils.copyDirectory(testRepo, this.repoDir);
        
        this.generatedNoticeFile = new File(this.repoDir, "NOTICE");
        final File noticeTemplate = new File( getBasedir(), "src/main/resources/NOTICE.template");
        FileUtils.copyFile(noticeTemplate, this.generatedNoticeFile);
    }


    public void testReport() throws Exception {
        Mojo mojo = lookupMojo( "check", pluginXmlFile );
        assertNotNull( "Mojo found.", mojo );
        
        setVariableValueToObject( mojo, "localRepository", new StubArtifactRepository( repoDir.getAbsolutePath() ) );
        setVariableValueToObject( mojo, "outputDir", repoDir.getAbsolutePath() );

        try {
            mojo.execute();
        }
        catch (MojoFailureException e) {
            //Expected
        }
    }
}
