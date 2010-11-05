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
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.jasig.maven.notice.util.ResourceFinder;

import difflib.DiffUtils;
import difflib.Patch;

/**
 * Checks the NOTICE file
 * 
 * @author Eric Dalquist
 * @version $Revision$
 * @goal check
 * @threadSafe true
 * @requiresDependencyResolution test
 */
public class CheckNoticeMojo extends AbstractNoticeMojo {
    
    @Override
    protected void handleNotice(Log logger, ResourceFinder finder, String noticeContents) throws MojoFailureException {
        //Write out the generated notice file
        final File outputFile = getNoticeOutputFile();

        if (!outputFile.exists()) {
            throw new MojoFailureException("No NOTICE file exists at: " + outputFile);
        }
        
        final String existingNoticeContents;
        try {
            existingNoticeContents = FileUtils.readFileToString(outputFile, this.encoding);
        }
        catch (IOException e) {
            throw new MojoFailureException("Failed to read existing NOTICE File from: " + outputFile, e);
        }
        
        //Check if the notice files match
        if (!noticeContents.equals(existingNoticeContents)) {
            String diffStr = "";
            try {
                final List expectedLines = IOUtils.readLines(new StringReader(noticeContents));
                final List existingLines = IOUtils.readLines(new StringReader(existingNoticeContents));
                final Patch diff = DiffUtils.diff(expectedLines, existingLines);
                diffStr = diff.getDeltas().toString();
            }
            catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            
                
            final String buildDir = project.getBuild().getDirectory();
            final File expectedNoticeFile = new File(new File(buildDir), "NOTICE.expected");
            try {
                FileUtils.writeStringToFile(expectedNoticeFile, noticeContents, this.encoding);
            }
            catch (IOException e) {
                logger.warn("Failed to write expected NOTICE File to: " + expectedNoticeFile, e);
            }
            
            final String msg = "Existing NOTICE file '" + outputFile + "' doesn't match expected NOTICE file: " + expectedNoticeFile;
            logger.error(msg);
            logger.error(diffStr);
            throw new MojoFailureException(msg);
        }
        
        logger.info("NOTICE file is up to date");
    }
}
