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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.collections.map.LRUMap;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.jasig.maven.notice.lookup.ArtifactLicense;
import org.jasig.maven.notice.lookup.LicenseLookup;
import org.jasig.maven.notice.lookup.MappedVersion;
import org.jasig.maven.notice.lookup.VersionType;
import org.jasig.maven.notice.util.ResourceFinder;

/**
 * Utility to load license-lookup XML files and search through the loaded results for matches on a specified
 * artifact.
 * 
 * @author Eric Dalquist
 * @version $Revision$
 */
public class LicenseLookupHelper {
    /**
     * Cache parse results for the 20 most recently used LicenseLookup files
     */
    @SuppressWarnings("unchecked")
    private static final Map<String, LicenseLookup> LICENSE_LOOKUP_CACHE = new LRUMap(20);
    private static final ReadWriteLock LICENSE_LOOKUP_CACHE_LOCK = new ReentrantReadWriteLock();
    
    private final Map<String, Map<List<MappedVersion>, ArtifactLicense>> mergedLicenseLookup = new LinkedHashMap<String, Map<List<MappedVersion>,ArtifactLicense>>();
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
                
                Map<List<MappedVersion>, ArtifactLicense> artifactVersions = this.mergedLicenseLookup.get(artifactKey);
                if (artifactVersions == null) {
                    artifactVersions = new LinkedHashMap<List<MappedVersion>, ArtifactLicense>();
                    this.mergedLicenseLookup.put(artifactKey, artifactVersions);
                }
                
                final List<MappedVersion> version = artifactLicense.getVersion();
                artifactVersions.put(version, artifactLicense);
                this.logger.debug("Mapped " + artifactLicense + " from: " + licenseLookupFile);
            }
        }
    }
    
    public ResolvedLicense lookupLicenseMapping(String groupId, String artifactId, ArtifactVersion artifactVersion) {
        //Find license info mapped to the group/artifact
        final String artifactKey = getArtifactKey(groupId, artifactId);
        final Map<List<MappedVersion>, ArtifactLicense> artifactVersions = this.mergedLicenseLookup.get(artifactKey);
        if (artifactVersions == null) {
            return null;
        }
        
        VersionType matchType = null;
        ArtifactLicense artifactLicense = null;
        
        //Search the mapped versions lists for a matching version
        for (final Entry<List<MappedVersion>, ArtifactLicense> versionEntry : artifactVersions.entrySet()) {
            final List<MappedVersion> versions = versionEntry.getKey();
            
            //If no versions are specified for an artifact all versions match 
            if (matchType == null && versions.size() == 0) {
                artifactLicense = versionEntry.getValue();
            }
            else {
                for (final MappedVersion version : versions) {
                    //Use the first REGEX match found, skip checking any subsequent REGEX versions
                    if (VersionType.REGEX == matchType && VersionType.REGEX == version.getType()) {
                        continue;
                    }
                    
                    final boolean matches = this.compareVersions(version, artifactVersion);
                    if (matches) {
                        matchType = version.getType();
                        artifactLicense = versionEntry.getValue();
                        
                        //If a STRING match is found break the loop. First exact match is always used
                        if (VersionType.STRING == matchType) {
                            break;
                        }
                    }
                }
            }
            
            //If a STRING match is found break the loop. First exact match is always used
            if (VersionType.STRING == matchType) {
                break;
            }
        }
            
        this.logger.debug("Found " + artifactLicense + " with match " + matchType + " for: " + groupId + ":" + artifactId + ":" + artifactVersion);
        return new ResolvedLicense(matchType, artifactLicense);
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

        //Try loading the LicenseLookup from cache
        final Lock readLock = LICENSE_LOOKUP_CACHE_LOCK.readLock();
        LicenseLookup licenseLookup = this.loadLicenseLookup(unmarshaller, licenseLookupFile, licenseLookupUrl, readLock, false);
        if (licenseLookup != null) {
            return licenseLookup;
        }
        
        //Must not have been in the cache, grab the write lock and check again
        final Lock writeLock = LICENSE_LOOKUP_CACHE_LOCK.writeLock();
        return loadLicenseLookup(unmarshaller, licenseLookupFile, licenseLookupUrl, writeLock, true);
    }

    protected LicenseLookup loadLicenseLookup(Unmarshaller unmarshaller, String licenseLookupFile, URL licenseLookupUrl, Lock lock, boolean create) throws MojoFailureException {
        final String licenseLookupKey = licenseLookupUrl.toString();
        
        lock.lock();
        try {
            //Look in the cache to see if the lookup file has already been parsed
            LicenseLookup licenseLookup = LICENSE_LOOKUP_CACHE.get(licenseLookupKey);
            if (licenseLookup != null) {
                logger.info("Loading license lookup mappings from '" + licenseLookupUrl + "' (cached)");
                return licenseLookup;
            }
            
            //Cache miss, check if we should parse the file, return null if not
            if (!create) {
                return null;
            }
        
            logger.info("Loading license lookup mappings from '" + licenseLookupUrl + "'");
            InputStream lookupStream = null;
            try {
                lookupStream = licenseLookupUrl.openStream();
                licenseLookup = (LicenseLookup)unmarshaller.unmarshal(lookupStream);
                LICENSE_LOOKUP_CACHE.put(licenseLookupKey, licenseLookup);
                return licenseLookup;
            }
            catch (IOException e) {
                throw new MojoFailureException("Failed to read '" + licenseLookupFile + "' from '" + licenseLookupUrl + "'", e);
            }
            catch (JAXBException e) {
                throw new MojoFailureException("Failed to parse '" + licenseLookupFile + "' from '" + licenseLookupUrl + "'", e);
            }
            finally {
                IOUtils.closeQuietly(lookupStream);
            }
        }
        finally {
            lock.unlock();
        }
    }
}
