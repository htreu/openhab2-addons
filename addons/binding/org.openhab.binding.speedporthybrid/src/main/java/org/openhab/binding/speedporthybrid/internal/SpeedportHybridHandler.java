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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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

/**
 * The {@link SpeedportHybridHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Henning Treu - Initial contribution
 */
@NonNullByDefault
public class SpeedportHybridHandler extends BaseThingHandler implements HandlerCallback {

    // private final Logger logger = LoggerFactory.getLogger(SpeedportHybridHandler.class);

    @Nullable
    private SpeedportHybridConfiguration config;

    private SpeedportHybridClient client;

    @Nullable
    private ScheduledFuture<?> scheduledRefresh;

    public SpeedportHybridHandler(Thing thing, HttpClient http) {
        super(thing);
        client = new SpeedportHybridClient(this, http);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command == RefreshType.REFRESH) {
            client.handleRefresh(channelUID);
            return;
        }

        if (channelUID.getId().equals(CHANNEL_LTE)) {
            if (command instanceof OnOffType) {
                setLTE(channelUID, (OnOffType) command);
            }
        }
    }

    private void setLTE(ChannelUID channelUID, OnOffType onoff) {
        client.setModule("use_lte", onoff == OnOffType.ON ? "1" : "0", new CommandChannelUpdateCallback() {

            @Override
            public void success() {
                updateState(channelUID, onoff);
            }

            @Override
            public void reject() {
                updateState(channelUID, onoff == OnOffType.ON ? OnOffType.OFF : OnOffType.ON);
            }
        });
    }

    private void scheduleRefresh() {
        if (scheduledRefresh != null) {
            scheduledRefresh.cancel(true);
        }

        scheduledRefresh = scheduler.scheduleWithFixedDelay(() -> {
            // updateModels();
            // refreshChannels();
        }, 0, config.refreshInterval, TimeUnit.SECONDS);
    }

    @Override
    public void initialize() {
        config = getConfigAs(SpeedportHybridConfiguration.class);
        client.setConfig(config);
        client.ensureLogin();

        if (config.refreshInterval > 0) {
            scheduleRefresh();
        }
    }

    @Override
    public void dispose() {
        if (scheduledRefresh != null) {
            scheduledRefresh.cancel(true);
        }

        scheduledRefresh = null;
        super.dispose();
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
