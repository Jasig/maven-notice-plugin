package org.jasig.maven.notice;

import java.io.File;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.stubs.StubArtifactRepository;

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
        setVariableValueToObject( mojo, "outputDir", repoDir.getAbsolutePath() );

        mojo.execute();
         
        final InputStream expectedNoticeStream = this.getClass().getResourceAsStream("/NOTICE.expected");
        final String expectedNotice = IOUtils.toString(expectedNoticeStream);
        
        final File generatedNoticeFile = new File(this.repoDir, "NOTICE");
        final String generatedNotice = FileUtils.readFileToString(generatedNoticeFile);
        
        assertEquals(expectedNotice, generatedNotice);
    }
}
