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

import org.jasig.maven.notice.lookup.ArtifactLicense;
import org.jasig.maven.notice.lookup.VersionType;

/**
 * Information about a resolved license.
 *
 * @author Eric Dalquist
 * @version $Revision$
 */
public class ResolvedLicense {
    private final VersionType versionType;
    private final ArtifactLicense artifactLicense;

    public ResolvedLicense(VersionType versionType, ArtifactLicense artifactLicense) {
        this.versionType = versionType;
        this.artifactLicense = artifactLicense;
    }

    /**
     * @return The type of matching used when the version was found. Null implies an 'all versions'
     *     match.
     */
    public VersionType getVersionType() {
        return this.versionType;
    }

    /** @return The resolved license */
    public ArtifactLicense getArtifactLicense() {
        return this.artifactLicense;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime * result
                        + ((this.artifactLicense == null) ? 0 : this.artifactLicense.hashCode());
        result = prime * result + ((this.versionType == null) ? 0 : this.versionType.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        ResolvedLicense other = (ResolvedLicense) obj;
        if (this.artifactLicense == null) {
            if (other.artifactLicense != null) return false;
        } else if (!this.artifactLicense.equals(other.artifactLicense)) return false;
        if (this.versionType != other.versionType) return false;
        return true;
    }

    @Override
    public String toString() {
        return "ResolvedLicense [versionType="
                + this.versionType
                + ", artifactLicense="
                + this.artifactLicense
                + "]";
    }
}
