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
import org.openhab.binding.speedporthybrid.internal.model.AuthParameters;
import org.openhab.binding.speedporthybrid.internal.model.JsonModel;
import org.openhab.binding.speedporthybrid.internal.model.JsonModelList;
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

    private static final String MODULE_USE_LTE = "use_lte";

    private SpeedportHybridConfiguration config;

    @Nullable
    private ScheduledFuture<?> scheduledRefresh;

    private SpeedportHybridClient client;

    private AuthParameters authParameters;

    public SpeedportHybridHandler(Thing thing, HttpClient http) {
        super(thing);
        this.authParameters = new AuthParameters();
        this.client = new SpeedportHybridClient(this, http);
        this.config = new SpeedportHybridConfiguration();
    }

    @Override
    public void initialize() {
        config = getConfigAs(SpeedportHybridConfiguration.class);
        client.setConfig(config);
        client.login(authParameters);

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
    public void updateStatus(ThingStatus status, ThingStatusDetail detail, @Nullable String description) {
        super.updateStatus(status, detail, description);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command == RefreshType.REFRESH) {
            handleRefresh(channelUID);
            return;
        }

        if (channelUID.getId().equals(CHANNEL_LTE)) {
            if (command instanceof OnOffType) {
                setLTE(channelUID, (OnOffType) command);
            }
        }
    }

    private void handleRefresh(ChannelUID channelUID) {
        JsonModelList models = client.getLoginModel(authParameters);
        if (models != null) {
            updateChannel(models, channelUID);
        }
    }

    private void updateChannel(JsonModelList models, ChannelUID channelUID) {
        if (channelUID.getId().equals(CHANNEL_LTE)) {
            JsonModel use_lte = models.getModel(MODULE_USE_LTE);
            if (use_lte != null && use_lte.hasValue("1")) {
                updateState(channelUID, OnOffType.ON);
            } else {
                updateState(channelUID, OnOffType.OFF);
            }
        }
    }

    private void setLTE(ChannelUID channelUID, OnOffType onoff) {
        boolean success = setModule(MODULE_USE_LTE, onoff == OnOffType.ON ? "1" : "0");
        if (success) {
            updateState(channelUID, onoff);
        }
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

    /**
     * Connect to the router and set the module to the given value.
     *
     * @param module the name of the module to set.
     * @param value  the value to set.
     * @return
     */
    private boolean setModule(String module, String value) {
        client.login(authParameters);
        String data = module + "=" + value;

        JsonModelList models = client.setModule(data, authParameters);
        JsonModel status = models.getModel("status");

        if (status != null && status.hasValue("ok")) {
            return true;
        } else {
            logger.warn("Failed to update data '{}' on router at '{}'", data, config.host);
            return false;
        }
    }

}
