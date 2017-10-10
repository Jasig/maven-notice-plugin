/**
 * Licensed to Jasig under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Jasig
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at:
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jasig.maven.notice;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.jasig.maven.notice.lookup.VersionType;
import org.jasig.maven.notice.util.ResourceFinder;
import org.junit.Test;

/**
 * @author Eric Dalquist
 * @version $Revision$
 */
public class LicenseLookupHelperTest {
    private final Log log = new SystemStreamLog();

    @Test
    public void testLicenseLookup() throws Exception {
        final ResourceFinder resourceFinder = createMock(ResourceFinder.class);

        expect(resourceFinder.findResource("license-lookup-fallbacks.xml"))
                .andReturn(this.getClass().getResource("/license-lookup-fallbacks.xml"));
        expect(resourceFinder.findResource("license-lookup.xml"))
                .andReturn(this.getClass().getResource("/license-lookup.xml"));

        replay(resourceFinder);

        final LicenseLookupHelper licenseLookupHelper =
                new LicenseLookupHelper(
                        log,
                        resourceFinder,
                        new String[] {"license-lookup-fallbacks.xml", "license-lookup.xml"});

        final ResolvedLicense resolvedLicense1 =
                licenseLookupHelper.lookupLicenseMapping(
                        "org.codehaus.plexus",
                        "plexus-container-default",
                        new DefaultArtifactVersion("1.0.0"));
        assertNotNull(resolvedLicense1);
        assertEquals(VersionType.REGEX, resolvedLicense1.getVersionType());
        assertEquals(
                "Apache Software License 2.0", resolvedLicense1.getArtifactLicense().getLicense());

        final ResolvedLicense resolvedLicense2 =
                licenseLookupHelper.lookupLicenseMapping(
                        "classworlds", "classworlds", new DefaultArtifactVersion("1.1.0"));
        assertNotNull(resolvedLicense2);
        assertEquals(VersionType.REGEX, resolvedLicense2.getVersionType());
        assertEquals(
                "Apache Software License 2.0", resolvedLicense2.getArtifactLicense().getLicense());

        verify(resourceFinder);
    }
}
