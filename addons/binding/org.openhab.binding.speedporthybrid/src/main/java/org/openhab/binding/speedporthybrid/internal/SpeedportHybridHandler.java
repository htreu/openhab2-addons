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
package org.openhab.binding.speedporthybrid.internal;

import static org.openhab.binding.speedporthybrid.internal.SpeedportHybridBindingConstants.CHANNEL_LTE;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SpeedportHybridHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Henning Treu - Initial contribution
 */
@NonNullByDefault
public class SpeedportHybridHandler extends BaseThingHandler implements HandlerCallback {

    private final Logger logger = LoggerFactory.getLogger(SpeedportHybridHandler.class);

    @Nullable
    private SpeedportHybridConfiguration config;

    private SpeedportHybridClient client;

    public SpeedportHybridHandler(Thing thing, @Nullable HttpClient http) {
        super(thing);
        client = new SpeedportHybridClient(this, http);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (channelUID.getId().equals(CHANNEL_LTE)) {
            if (command == RefreshType.REFRESH) {
                handleRefreshCommand(channelUID);
            }
            if (command instanceof OnOffType) {
                setLTE(channelUID, (OnOffType) command);
            }
            // TODO: handle command

            // Note: if communication with thing fails for some reason,
            // indicate that by setting the status with detail information
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
            // "Could not control device at IP address x.x.x.x");
        }
    }

    private void setLTE(ChannelUID channelUID, OnOffType onoff) {
        client.setModule("use_lte", onoff == OnOffType.ON ? "1" : "0");
    }

    private void handleRefreshCommand(ChannelUID channelUID) {
        client.handleRefresh(channelUID);
    }

    @Override
    public void initialize() {
        config = getConfigAs(SpeedportHybridConfiguration.class);
        client.setConfig(config);

        // TODO: Initialize the thing. If done set status to ONLINE to indicate proper working.
        // Long running initialization should be done asynchronously in background.
        updateStatus(ThingStatus.ONLINE);

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work
        // as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    @Override
    public void updateState(ChannelUID channelUID, State state) {
        super.updateState(channelUID, state);
    }

    @Override
    public void updateStatus(ThingStatus status, ThingStatusDetail detail, @Nullable String description) {
        super.updateStatus(status, detail, description);
    }

}
