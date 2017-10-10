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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.jasig.maven.notice.lookup.LicenseLookup;

/**
 * Utility for loading the LicenseLookup JAXBContext as a singleton
 *
 * @author Eric Dalquist
 * @version $Revision$
 */
public final class LicenseLookupContext {
    private static final Package LICENSE_LOOKUP_PACKAGE = LicenseLookup.class.getPackage();
    private static JAXBContext JAXB_CONTEXT;

    private static synchronized JAXBContext getJaxbContext() {
        if (JAXB_CONTEXT == null) {
            try {
                JAXB_CONTEXT = JAXBContext.newInstance(LICENSE_LOOKUP_PACKAGE.getName());
            } catch (JAXBException e) {
                throw new IllegalStateException(
                        "Failed to load JAXBContext for package: " + LICENSE_LOOKUP_PACKAGE, e);
            }
        }

        return JAXB_CONTEXT;
    }

    public static Unmarshaller getUnmarshaller() {
        try {
            final JAXBContext jaxbContext = getJaxbContext();
            return jaxbContext.createUnmarshaller();
        } catch (JAXBException e) {
            throw new IllegalStateException(
                    "Failed to load JAXBContext for package: " + LICENSE_LOOKUP_PACKAGE, e);
        }
    }

    public static Marshaller getMarshaller() {
        try {
            final JAXBContext jaxbContext = getJaxbContext();
            final Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(
                    "jaxb.schemaLocation",
                    "https://source.jasig.org/schemas/maven-notice-plugin/license-lookup https://source.jasig.org/schemas/maven-notice-plugin/license-lookup/license-lookup-v1.0.xsd");
            marshaller.setProperty("jaxb.formatted.output", true);
            return marshaller;
        } catch (JAXBException e) {
            throw new IllegalStateException(
                    "Failed to load JAXBContext for package: " + LICENSE_LOOKUP_PACKAGE, e);
        }
    }

    private LicenseLookupContext() {}
}
