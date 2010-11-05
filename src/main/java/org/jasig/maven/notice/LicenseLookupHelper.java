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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.io.IOUtils;
import org.jasig.maven.notice.lookup.LicenseLookup;

/**
 * @author Eric Dalquist
 * @version $Revision$
 */
public class LicenseLookupHelper {
    private static final Package LICENSE_LOOKUP_PACKAGE = LicenseLookup.class.getPackage();
//    private final Map<String, Map<String, Map<>>>

    public LicenseLookupHelper(URL[] licenseLookup) {
        final Unmarshaller unmarshaller;
        try {
            final JAXBContext jaxbContext = JAXBContext.newInstance(LICENSE_LOOKUP_PACKAGE.getName());
            unmarshaller = jaxbContext.createUnmarshaller();
        }
        catch (JAXBException e) {
            throw new IllegalStateException("Failed to load JAXBContext for package: " + LICENSE_LOOKUP_PACKAGE, e);
        }
        
        if (licenseLookup == null) {
            licenseLookup = new URL[0];
        }
        
        for (final URL licenseLookupUrl : licenseLookup) {
            InputStream lookupStream = null;
            try {
                lookupStream = licenseLookupUrl.openStream();
                final Object unmarshal = unmarshaller.unmarshal(lookupStream);
                System.out.println(unmarshal);
            }
            catch (IOException e) {
                //TODO handle exception with decent log message
            }
            catch (JAXBException e) {
                //TODO handle exception with decent log message
            }
            finally {
                IOUtils.closeQuietly(lookupStream);
            }
        }
    }

//    public ArtifactLicense getLicenseInfo(Artifact artifact) {
//        
//    }
}
