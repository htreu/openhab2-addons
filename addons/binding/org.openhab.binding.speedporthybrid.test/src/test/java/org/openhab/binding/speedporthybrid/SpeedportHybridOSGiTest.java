/**
 * Copyright (c) 2014,2018 by the respective copyright holders.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.speedporthybrid;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import org.eclipse.smarthome.core.thing.ManagedThingProvider;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingProvider;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.test.java.JavaOSGiTest;
import org.eclipse.smarthome.test.storage.VolatileStorageService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openhab.binding.speedporthybrid.internal.handler.SpeedportHybridHandler;

/**
 * Test cases for {@link SpeedportHybridHandler}.
 *
 * @author Henning Treu - Initial contribution
 */
public class SpeedportHybridOSGiTest extends JavaOSGiTest {

    private static final ThingTypeUID SPEEDPORT_HYBRID_THING_TYPE_UID = new ThingTypeUID("speedporthybrid",
            "speedporthybrid");

    private ManagedThingProvider managedThingProvider;
    private final VolatileStorageService volatileStorageService = new VolatileStorageService();
    private Thing thing;

    @Before
    public void setUp() {
        registerService(volatileStorageService);
        managedThingProvider = getService(ThingProvider.class, ManagedThingProvider.class);
        thing = ThingBuilder.create(SPEEDPORT_HYBRID_THING_TYPE_UID, "router").withLabel("My Speedport").build();
    }

    @After
    public void tearDown() {
        managedThingProvider.remove(thing.getUID());
        unregisterService(volatileStorageService);
    }

    @Test
    public void creationOfSpeedportHybridHandler() {
        assertThat(thing.getHandler(), is(nullValue()));
        managedThingProvider.add(thing);
        waitForAssert(() -> assertThat(thing.getHandler(), is(notNullValue())));
    }
}
