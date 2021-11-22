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

import difflib.ChangeDelta;
import difflib.Chunk;
import difflib.DeleteDelta;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.InsertDelta;
import difflib.Patch;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jasig.maven.notice.util.ResourceFinder;

/**
 * Checks the NOTICE file to make sure it matches the expected output
 *
 * @author Eric Dalquist
 * @version $Revision$
 */
@Mojo(name = "check", threadSafe = true, requiresDependencyCollection = ResolutionScope.TEST)
public class CheckNoticeMojo extends AbstractNoticeMojo {

    @Override
    protected void handleNotice(ResourceFinder finder, String noticeContents)
            throws MojoFailureException {
        final Log logger = this.getLog();

        // Write out the generated notice file
        final File outputFile = getNoticeOutputFile();

        // Make sure the existing NOTICE file exists
        if (!outputFile.exists()) {
            throw new MojoFailureException("No NOTICE file exists at: " + outputFile);
        }

        // Load up the existing NOTICE file
        final Reader existingNoticeContents;
        try {
            final FileInputStream outputFileInputStream = new FileInputStream(outputFile);
            existingNoticeContents =
                    new InputStreamReader(
                            new BufferedInputStream(outputFileInputStream), this.encoding);
        } catch (IOException e) {
            throw new MojoFailureException(
                    "Failed to read existing NOTICE File from: " + outputFile, e);
        }

        // Check if the notice files match
        final String diffText =
                this.generateDiff(logger, new StringReader(noticeContents), existingNoticeContents);
        if (diffText.length() != 0) {
            final String buildDir = project.getBuild().getDirectory();
            final File expectedNoticeFile = new File(new File(buildDir), "NOTICE.expected");
            try {
                FileUtils.writeStringToFile(expectedNoticeFile, noticeContents, this.encoding);
            } catch (IOException e) {
                logger.warn("Failed to write expected NOTICE File to: " + expectedNoticeFile, e);
            }

            final String msg =
                    "Existing NOTICE file '"
                            + outputFile
                            + "' doesn't match expected NOTICE file: "
                            + expectedNoticeFile;
            logger.error(msg + "\n" + diffText);
            throw new MojoFailureException(msg);
        }

        logger.info("NOTICE file is up to date");
    }

    protected String generateDiff(
            Log logger, Reader noticeContents, Reader existingNoticeContents) {
        final StringBuilder diffText = new StringBuilder();
        try {
            final List<String> expectedLines = IOUtils.readLines(noticeContents);
            final List<String> existingLines = IOUtils.readLines(existingNoticeContents);
            final Patch<String> diff = DiffUtils.diff(expectedLines, existingLines);

            for (final Delta<String> delta : diff.getDeltas()) {
                final Chunk original = delta.getOriginal();
                final Chunk revised = delta.getRevised();

                final char changeType;
                if (delta instanceof DeleteDelta) {
                    changeType = 'd';
                } else if (delta instanceof InsertDelta) {
                    changeType = 'a';
                } else if (delta instanceof ChangeDelta) {
                    changeType = 'c';
                } else {
                    changeType = '?';
                }

                // Write out the diff line info
                diffText.append(original.getPosition() + 1);
                if (original.getLines().size() > 1) {
                    diffText.append(",")
                            .append(original.getPosition() + original.getLines().size());
                }
                diffText.append(changeType);
                diffText.append(revised.getPosition() + 1);
                if (revised.getLines().size() > 1) {
                    diffText.append(",").append(revised.getPosition() + revised.getLines().size());
                }
                diffText.append("\n");

                // Write out the incoming and outgoing changes
                for (final Object line : original.getLines()) {
                    diffText.append("< ").append(line).append("\n");
                }
                diffText.append("---\n");
                for (final Object line : revised.getLines()) {
                    diffText.append("> ").append(line).append("\n");
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to generate diff between existing and expected NOTICE files", e);
        }
        return diffText.toString();
    }
}
