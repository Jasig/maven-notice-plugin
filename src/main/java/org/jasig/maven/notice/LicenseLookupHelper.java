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
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.jasig.maven.notice.lookup.ArtifactLicense;
import org.jasig.maven.notice.lookup.LicenseLookup;
import org.jasig.maven.notice.lookup.MappedVersion;
import org.jasig.maven.notice.util.ResourceFinder;

/**
 * Utility to load license-lookup XML files and search through the loaded results for matches on a specified
 * artifact.
 * 
 * @author Eric Dalquist
 * @version $Revision$
 */
public class LicenseLookupHelper {
    private final Map<String, Map<List<MappedVersion>, ArtifactLicense>> lookupCache = new LinkedHashMap<String, Map<List<MappedVersion>,ArtifactLicense>>();
    private final Log logger;
    private final ResourceFinder resourceFinder;

    public LicenseLookupHelper(Log logger, ResourceFinder resourceFinder, String[] licenseLookupFiles) throws MojoFailureException {
        this.logger = logger;
        this.resourceFinder = resourceFinder;
        
        final Unmarshaller unmarshaller = LicenseLookupContext.getUnmarshaller();
        
        if (licenseLookupFiles == null) {
            licenseLookupFiles = new String[0];
        }
        
        for (final String licenseLookupFile : licenseLookupFiles) {
            final LicenseLookup licenseLookup = this.loadLicenseLookup(unmarshaller, licenseLookupFile);
            
            for (final ArtifactLicense artifactLicense : licenseLookup.getArtifact()) {
                final String groupId = artifactLicense.getGroupId();
                final String artifactId = artifactLicense.getArtifactId();
                final String artifactKey = getArtifactKey(groupId, artifactId);
                
                Map<List<MappedVersion>, ArtifactLicense> artifactVersions = this.lookupCache.get(artifactKey);
                if (artifactVersions == null) {
                    artifactVersions = new LinkedHashMap<List<MappedVersion>, ArtifactLicense>();
                    this.lookupCache.put(artifactKey, artifactVersions);
                }
                
                final List<MappedVersion> version = artifactLicense.getVersion();
                artifactVersions.put(version, artifactLicense);
                this.logger.debug("Mapped " + artifactLicense + " from: " + licenseLookupFile);
            }
        }
    }
    
    public ArtifactLicense lookupLicenseMapping(String groupId, String artifactId, ArtifactVersion artifactVersion) {
        //Find license info mapped to the group/artifact
        final String artifactKey = getArtifactKey(groupId, artifactId);
        final Map<List<MappedVersion>, ArtifactLicense> artifactVersions = this.lookupCache.get(artifactKey);
        if (artifactVersions == null) {
            return null;
        }
        
        //Search the mapped versions lists for a matching version
        for (final Entry<List<MappedVersion>, ArtifactLicense> versionEntry : artifactVersions.entrySet()) {
            final List<MappedVersion> versions = versionEntry.getKey();
            for (final MappedVersion version :versions) {
                final boolean matches = this.compareVersions(version, artifactVersion);
                if (matches) {
                    final ArtifactLicense artifactLicense = versionEntry.getValue();
                    this.logger.debug("Found " + artifactLicense + " for: " + groupId + ":" + artifactId + ":" + artifactVersion);
                    return artifactLicense;
                }
            }
        }
        
        return null;
    }

    protected boolean compareVersions(final MappedVersion version, ArtifactVersion artifactVersion) {
        switch (version.getType()) {
            case REGEX: {
                final Pattern versionPattern = Pattern.compile(version.getValue());
                return versionPattern.matcher(artifactVersion.toString()).matches();
            }
            default: {
                final ArtifactVersion mappedVersion = new DefaultArtifactVersion(version.getValue());
                return mappedVersion.equals(artifactVersion);
            }
        }
    }
    
    protected String getArtifactKey(String groupId, String artifactId) {
        return groupId + ":" + artifactId;
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
}
