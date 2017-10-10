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

public class ArtifactLicenseInfo {

    private final String artifactName;
    private final String licenseName;
    private final String scope;
    private final boolean optional;

    public ArtifactLicenseInfo(String artifactName, String licenseName, String scope, boolean optional) {
        this.artifactName = artifactName;
        this.licenseName = licenseName;
        this.scope = scope;
        this.optional = optional;
    }

    public String getArtifactName() {
        return artifactName;
    }

    public String getLicenseName() {
        return licenseName;
    }

    public String getScope() {
        return scope;
    }

    public boolean isOptional() {
        return optional;
    }
}
