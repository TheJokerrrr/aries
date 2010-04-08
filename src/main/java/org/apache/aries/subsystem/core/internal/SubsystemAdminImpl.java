/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.aries.subsystem.core.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.apache.aries.subsystem.Subsystem;
import org.apache.aries.subsystem.SubsystemAdmin;
import org.apache.aries.subsystem.SubsystemConstants;
import org.apache.aries.subsystem.SubsystemException;
import org.apache.aries.subsystem.spi.Resource;
import org.apache.aries.subsystem.spi.ResourceResolver;
import org.apache.felix.utils.manifest.Clause;
import org.apache.felix.utils.manifest.Parser;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.Version;
import org.osgi.service.composite.CompositeAdmin;
import org.osgi.service.composite.CompositeBundle;
import org.osgi.util.tracker.ServiceTracker;

public class SubsystemAdminImpl implements SubsystemAdmin {

    private static final Version SUBSYSTEM_MANIFEST_VERSION = new Version("1.0");

    final Semaphore lock = new Semaphore(1);
    final BundleContext context;
    final Map<Long, Subsystem> subsystems = new HashMap<Long, Subsystem>();
    final ServiceTracker compositeAdminTracker;
    final ServiceTracker resourceResolverTracker;
    final SubsystemEventDispatcher eventDispatcher;

    public SubsystemAdminImpl(BundleContext context, SubsystemEventDispatcher eventDispatcher) {
        this.context = context;
        this.eventDispatcher = eventDispatcher;
        this.compositeAdminTracker = new ServiceTracker(context, CompositeAdmin.class.getName(), null);
        this.compositeAdminTracker.open();
        this.resourceResolverTracker = new ServiceTracker(context, ResourceResolver.class.getName(), null);
        this.resourceResolverTracker.open();
        // Track subsystems
        synchronized (subsystems) {
            this.context.addBundleListener(new SynchronousBundleListener() {
                public void bundleChanged(BundleEvent event) {
                    SubsystemAdminImpl.this.bundleChanged(event);
                }
            });
            loadSubsystems();
        }
    }

    public void dispose() {
        compositeAdminTracker.close();
        resourceResolverTracker.close();
    }

    public void bundleChanged(BundleEvent event) {
        synchronized (subsystems) {
            if (event.getType() == BundleEvent.UPDATED || event.getType() == BundleEvent.UNINSTALLED) {
                subsystems.remove(event.getBundle().getBundleId());
            }
            if (event.getType() == BundleEvent.INSTALLED || event.getType() == BundleEvent.UPDATED) {
                Subsystem s = isSubsystem(event.getBundle());
                if (s != null) {
                    subsystems.put(s.getSubsystemId(), s);
                }
            }
        }
    }

    protected void loadSubsystems() {
        synchronized (subsystems) {
            subsystems.clear();
            for (Bundle bundle : context.getBundles()) {
                Subsystem s = isSubsystem(bundle);
                if (s != null) {
                    subsystems.put(s.getSubsystemId(), s);
                }
            }
        }
    }

    protected Subsystem isSubsystem(Bundle bundle) {
        if (bundle instanceof CompositeBundle) {
            String bsn = (String) bundle.getHeaders().get(Constants.BUNDLE_SYMBOLICNAME);
            Clause[] bsnClauses = Parser.parseHeader(bsn);
            if ("true".equals(bsnClauses[0].getDirective(SubsystemConstants.SUBSYSTEM_DIRECTIVE))) {
                return new SubsystemImpl(this, (CompositeBundle) bundle);
            }
        }
        return null;
    }

    public Subsystem getSubsystem(String scope) {
        synchronized (subsystems) {
            for (Subsystem s : subsystems.values()) {
                if (s.getScope().equals(scope)) {
                    return s;
                }
            }
            return null;
        }
    }

    public Map<Long, Subsystem> getSubsystems() {
        synchronized (subsystems) {
            return Collections.unmodifiableMap(subsystems);
        }
    }

    public Subsystem install(String url) {
        return install(url, null);
    }

    public synchronized Subsystem install(String url, final InputStream is) throws SubsystemException {
        Resource subsystemResource = new ResourceImpl(null, null, SubsystemConstants.RESOURCE_TYPE_SUBSYSTEM, url) {
            @Override
            public InputStream open() throws IOException {
                if (is != null) {
                    return is;
                }
                return super.open();
            }
        };
        SubsystemResourceProcessor processor = new SubsystemResourceProcessor();
        SubsystemResourceProcessor.SubsystemSession session = processor.createSession(context);
        boolean success = false;
        try {
            session.process(subsystemResource);
            session.prepare();
            session.commit();
            success = true;
        } finally {
            if (!success) {
                session.rollback();
            }
        }
        for (Subsystem ss : getSubsystems().values()) {
            if (url.equals(ss.getLocation())) {
                return ss;
            }
        }
        throw new IllegalStateException();
    }

    public void update(Subsystem subsystem) {
        update(subsystem, null);
    }

    public synchronized void update(Subsystem subsystem, InputStream content) {
        // TODO: update
    }

    public synchronized void uninstall(Subsystem ss) {
        if (!(ss instanceof SubsystemImpl)) {
            throw new IllegalArgumentException("The given subsystem is not managed by the SubsystemAdmin instance");
        }
        SubsystemImpl subsystem = (SubsystemImpl) ss;
        try {
            subsystem.composite.uninstall();
            this.subsystems.remove(subsystem.id);
        } catch (BundleException e) {
            // TODO: Rollback
            throw new SubsystemException("Error while uninstalling the subsystem", e);
        }
    }

    public void uninstallForced(Subsystem ss) {
        if (!(ss instanceof SubsystemImpl)) {
            throw new IllegalArgumentException("The given subsystem is not managed by the SubsystemAdmin instance");
        }
        SubsystemImpl subsystem = (SubsystemImpl) ss;
        try {
            subsystem.composite.uninstall();
        } catch (BundleException e) {
            // Ignore
        } finally {
            this.subsystems.remove(subsystem.id);
        }
    }

    public boolean cancel() {
        // TODO
        return false;
    }

}
