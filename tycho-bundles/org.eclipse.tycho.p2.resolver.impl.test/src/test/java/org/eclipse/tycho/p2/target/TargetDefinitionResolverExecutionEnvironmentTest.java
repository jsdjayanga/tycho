/*******************************************************************************
 * Copyright (c) 2012 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.p2.target;

import static org.eclipse.tycho.p2.target.TargetDefinitionResolverTest.defaultEnvironments;
import static org.eclipse.tycho.p2.target.TargetDefinitionResolverTest.definitionWith;
import static org.eclipse.tycho.p2.test.matcher.InstallableUnitMatchers.unit;
import static org.eclipse.tycho.p2.test.matcher.InstallableUnitMatchers.unitWithId;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.hasItem;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.VersionedId;
import org.eclipse.tycho.p2.impl.test.MavenLoggerStub;
import org.eclipse.tycho.p2.target.TargetDefinitionResolverIncludeModeTests.PlannerLocationStub;
import org.eclipse.tycho.p2.target.TargetDefinitionResolverTest.LocationStub;
import org.eclipse.tycho.p2.target.TargetDefinitionResolverTest.RepositoryStub;
import org.eclipse.tycho.p2.target.ee.StandardEEResolutionHints;
import org.eclipse.tycho.p2.target.facade.TargetDefinition;
import org.eclipse.tycho.p2.target.facade.TargetDefinition.IncludeMode;
import org.eclipse.tycho.p2.target.facade.TargetDefinition.Repository;
import org.eclipse.tycho.test.util.P2Context;
import org.junit.Rule;
import org.junit.Test;

public class TargetDefinitionResolverExecutionEnvironmentTest {

    @Rule
    public P2Context p2Context = new P2Context();

    private MavenLoggerStub logger = new MavenLoggerStub();
    private TargetDefinitionResolver subject;

    private TargetDefinitionResolver targetResolverForEE(String executionEnvironment) throws ProvisionException {
        return new TargetDefinitionResolver(defaultEnvironments(), new StandardEEResolutionHints(executionEnvironment),
                p2Context.getAgent(), logger);
    }

    @Test
    public void testRestrictedExecutionEnvironment() throws Exception {
        subject = targetResolverForEE("CDC-1.0/Foundation-1.0");

        TargetDefinition definition = definitionWith(new AlternatePackageProviderLocationStub());
        Collection<IInstallableUnit> units = subject.resolveContent(definition).getUnits();

        // expect that resolver included a bundle providing org.w3c.dom (here javax.xml)...
        assertThat(units, hasItem(unit("javax.xml", "0.0.1.SNAPSHOT")));
        // ... and did not match the import against the "a.jre" IU also in the repository
        assertThat(units, not(hasItem(unitWithId("a.jre"))));
    }

    @Test
    public void testAutoGeneratedExecutionEnvironment() throws Exception {
        subject = targetResolverForEE("JavaSE-1.7");

        TargetDefinition definition = definitionWith(new AlternatePackageProviderLocationStub());
        Collection<IInstallableUnit> units = subject.resolveContent(definition).getUnits();

        // expect that resolver did not included a bundle providing org.w3c.dom...
        assertThat(units, not(hasItem(unit("javax.xml", "0.0.1.SNAPSHOT"))));
        // ... but instead included the configured 'a.jre' IU (which is not contained in the repository)
        assertThat(units, hasItem(unit("a.jre.javase", "1.7.0")));

        // other "a.jre" IUs from the repository shall be filtered out
        assertThat(units, not(hasItem(unitWithId("a.jre"))));
    }

    @Test
    public void testPlannerResolutionOfProduct() throws Exception {
        subject = targetResolverForEE("J2SE-1.5");

        Collection<IInstallableUnit> units = subject.resolveContent(
                definitionWith(new ProductLocationStub(IncludeMode.PLANNER))).getUnits();

        // expect that the resolutions succeeds (bug 370502), but that the wrong EE IUs are not in the result
        assertThat(units, hasItem(unit("sdk", "1.0.0")));
        assertThat(units, not(hasItem(unit("a.jre.javase", "1.6.0"))));
        assertThat(units, not(hasItem(unit("config.a.jre.javase", "1.6.0"))));
    }

    @Test
    public void testSlicerResolutionOfProduct() throws Exception {
        subject = targetResolverForEE("J2SE-1.5");

        Collection<IInstallableUnit> units = subject.resolveContent(
                definitionWith(new ProductLocationStub(IncludeMode.SLICER))).getUnits();

        assertThat(units, hasItem(unit("sdk", "1.0.0")));
        assertThat(units, not(hasItem(unit("a.jre.javase", "1.6.0"))));
        assertThat(units, not(hasItem(unit("config.a.jre.javase", "1.6.0"))));
    }

    /**
     * Location with a seed that requires the package org.w3c.dom. In the repository, there is both
     * a bundle and a fake 'a.jre' IU that could match that import.
     */
    static class AlternatePackageProviderLocationStub extends PlannerLocationStub {

        public AlternatePackageProviderLocationStub() {
            super(null, new VersionedId("dom-client", "0.0.1.SNAPSHOT"));
        }

        @Override
        public List<? extends Repository> getRepositories() {
            return Collections.singletonList(new RepositoryStub("repositories/", "javax.xml"));
        }

    }

    /**
     * Location with a product IU seed that has a hard requirement on the a.jre.javase 1.6.0 IU.
     */
    static class ProductLocationStub extends LocationStub {

        private final IncludeMode includeMode;

        ProductLocationStub(IncludeMode includeMode) {
            super(new VersionedId("sdk", "1.0.0"));
            this.includeMode = includeMode;
        }

        @Override
        public List<? extends Repository> getRepositories() {
            return Collections.singletonList(new RepositoryStub("repositories/", "requirejreius"));
        }

        @Override
        public IncludeMode getIncludeMode() {
            return includeMode;
        }
    }
}
