package org.jasig.maven.notice;

import java.io.File;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.stubs.StubArtifactRepository;

/**
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
