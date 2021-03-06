/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.test.osgi.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.osgi.resolver.MavenCoordinates;
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.XRequirementBuilder;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.repository.Repository;
import org.osgi.service.repository.RepositoryContent;

/**
 * Test simple OSGi bundle deployment
 *
 * @author thomas.diesler@jboss.com
 */
@RunWith(Arquillian.class)
public class RepositoryBundleTestCase {

    @Inject
    public BundleContext context;

    @Deployment
    public static JavaArchive createdeployment() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "example-bundle");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(BundleActivator.class, Repository.class, Resource.class);
                builder.addImportPackages(Repository.class, XCapability.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Test
    public void testRepositoryService() throws Exception {

        // Get the service reference
        ServiceReference sref = context.getServiceReference(Repository.class.getName());
        Repository repo = (Repository) context.getService(sref);
        assertNotNull("Repository not null", repo);

        MavenCoordinates mavenid = MavenCoordinates.parse("org.apache.felix:org.apache.felix.configadmin:1.2.8");
        Requirement req = XRequirementBuilder.createArtifactRequirement(mavenid);
        assertNotNull("Requirement not null", req);

        Collection<Capability> caps = repo.findProviders(Collections.singleton(req)).get(req);
        assertEquals("Capability not null", 1, caps.size());

        XIdentityCapability xcap = (XIdentityCapability) caps.iterator().next();
        assertEquals("org.apache.felix.configadmin", xcap.getSymbolicName());
        RepositoryContent content = (RepositoryContent) xcap.getResource();
        InputStream input = content.getContent();
        try {
            Bundle bundle = context.installBundle(xcap.getSymbolicName(), input);
            try {
                bundle.start();
                assertEquals(Bundle.ACTIVE, bundle.getState());
            } finally {
                bundle.uninstall();
            }
        } finally {
            input.close();
        }
    }
}
