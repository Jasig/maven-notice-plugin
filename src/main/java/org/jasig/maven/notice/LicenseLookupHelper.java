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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.jasig.maven.notice.lookup.ArtifactLicense;
import org.jasig.maven.notice.lookup.ArtifactVersion;
import org.jasig.maven.notice.lookup.LicenseLookup;
import org.jasig.maven.notice.util.ResourceFinder;

/**
 * @author Eric Dalquist
 * @version $Revision$
 */
public class LicenseLookupHelper {
    private static final Package LICENSE_LOOKUP_PACKAGE = LicenseLookup.class.getPackage();
    
    private final Map<String, Map<List<ArtifactVersion>, ArtifactLicense>> lookupCache = new LinkedHashMap<String, Map<List<ArtifactVersion>,ArtifactLicense>>();
    private final Log logger;
    private final ResourceFinder resourceFinder;

    public LicenseLookupHelper(Log logger, ResourceFinder resourceFinder, String[] licenseLookupFiles) throws MojoFailureException {
        this.logger = logger;
        this.resourceFinder = resourceFinder;
        
        final Unmarshaller unmarshaller = this.getUnmarshaller();
        
        if (licenseLookupFiles == null) {
            licenseLookupFiles = new String[0];
        }
        
        for (final String licenseLookupFile : licenseLookupFiles) {
            final LicenseLookup licenseLookup = this.loadLicenseLookup(unmarshaller, licenseLookupFile);
            
            for (final ArtifactLicense artifactLicense : licenseLookup.getArtifact()) {
                final String artifactKey = artifactLicense.getGroupId() + ":" + artifactLicense.getArtifactId();
                
                Map<List<ArtifactVersion>, ArtifactLicense> artifactVersions = this.lookupCache.get(artifactKey);
                if (artifactVersions == null) {
                    artifactVersions = new LinkedHashMap<List<ArtifactVersion>, ArtifactLicense>();
                    this.lookupCache.put(artifactKey, artifactVersions);
                }
                
                final List<ArtifactVersion> version = artifactLicense.getVersion();
                artifactVersions.put(version, artifactLicense);
            }
        }
    }

    protected Unmarshaller getUnmarshaller() {
        try {
            final JAXBContext jaxbContext = JAXBContext.newInstance(LICENSE_LOOKUP_PACKAGE.getName());
            return jaxbContext.createUnmarshaller();
        }
        catch (JAXBException e) {
            throw new IllegalStateException("Failed to load JAXBContext for package: " + LICENSE_LOOKUP_PACKAGE, e);
        }
    }

    protected LicenseLookup loadLicenseLookup(Unmarshaller unmarshaller, String licenseLookupFile) throws MojoFailureException {
        final URL licenseLookupUrl = resourceFinder.findResource(licenseLookupFile);
        
        logger.debug("Loading '" + licenseLookupFile + "' from '" + licenseLookupUrl + "'");

        InputStream lookupStream = null;
        try {
            lookupStream = licenseLookupUrl.openStream();
            return (LicenseLookup)unmarshaller.unmarshal(lookupStream);
        }
        catch (IOException e) {
            new MojoFailureException("Failed to read '" + licenseLookupFile + "' from '" + licenseLookupUrl + "'", e);
        }
        catch (JAXBException e) {
            new MojoFailureException("Failed to parse '" + licenseLookupFile + "' from '" + licenseLookupUrl + "'", e);
        }
        finally {
            IOUtils.closeQuietly(lookupStream);
        }
        
        throw new MojoFailureException("Failed to parse '" + licenseLookupFile + "' from '" + licenseLookupUrl + "'");
    }

//    public ArtifactLicense getLicenseInfo(Artifact artifact) {
//        
//    }
    
}
