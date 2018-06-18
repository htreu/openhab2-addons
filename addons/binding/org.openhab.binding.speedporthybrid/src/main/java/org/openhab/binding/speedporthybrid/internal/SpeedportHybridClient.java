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

import static org.eclipse.smarthome.core.thing.ThingStatus.OFFLINE;
import static org.eclipse.smarthome.core.thing.ThingStatus.ONLINE;
import static org.eclipse.smarthome.core.thing.ThingStatusDetail.*;
import static org.openhab.binding.speedporthybrid.internal.SpeedportHybridBindingConstants.CHANNEL_LTE;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.codec.DecoderException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.openhab.binding.speedporthybrid.internal.model.JsonModel;
import org.openhab.binding.speedporthybrid.internal.model.JsonModelList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Handle all communication with the SpeedportHybrid router.
 *
 * @author Henning Treu - initial contribution
 *
 */
@NonNullByDefault
public class SpeedportHybridClient {

    private final Logger logger = LoggerFactory.getLogger(SpeedportHybridClient.class);

    private static final int CHALLANGEV_LENGTH = 64;
    private static final String CHALLANGEV = "challenge = \"";

    private static CryptoUtils cryptoUtils = new CryptoUtils();

    private final HandlerCallback callback;

    private final HttpClient http;

    @Nullable
    private String host;

    @Nullable
    private String password;

    @Nullable
    private String challengev;

    @Nullable
    private String csrfToken;

    @Nullable
    private String derivedKey;

    private Gson gson = new Gson();

    public SpeedportHybridClient(HandlerCallback callback, HttpClient http) {
        this.callback = callback;
        this.http = http;
    }

    public void setConfig(SpeedportHybridConfiguration config) {
        this.host = config.host;
        this.password = config.password;
    }

    /**
     * Handle a refresh for the given channel.
     *
     * @param channelUID
     */
    public void handleRefresh(ChannelUID channelUID) {
        JsonModelList models = getLoginModel();
        if (models != null) {
            updateChannel(models, channelUID);
        }
    }

    /**
     * Connect to the router and set the module to the given value.
     *
     * @param module the name of the module to set.
     * @param value  the value to set.
     */
    public void setModule(String module, String value, CommandChannelUpdateCallback channelUpdateCallback) {
        ensureLogin();
        String moduleURL = "http://" + host + "/data/Modules.json";
        String data = "csrf_token=" + csrfToken + "&" + module + "=" + value;
        requestEncrypted(moduleURL, data, channelUpdateCallback);
    }

    public void ensureLogin() {
        if (csrfToken != null && challengev != null && derivedKey != null && isLogin()) {
            return;
        } else {
            csrfToken = null;
            derivedKey = null;
            refreshChallengevOrCSRFToken();
            if (challengev != null && login()) {
                derivedKey = cryptoUtils.calculateDerivedKey(challengev, password);
                refreshCSRFToken();
            }
        }
    }

    private boolean isLogin() {
        String url = "http://" + host + "/data/heartbeat.json?showpw=0&csrf_token=" + csrfToken + "&challengev="
                + challengev + "&password=" + cryptoUtils.getPasswordHash(challengev, password);
        ContentResponse resp = requestURL(url);
        if (resp == null || resp.getContentAsString().isEmpty()) {
            return false;
        }

        String heartbeat = resp.getContentAsString();

        JsonModelList models = gson.fromJson(fixContent(heartbeat), JsonModelList.class);
        JsonModel loginstate = models.getModel("loginstate");
        return loginstate != null && loginstate.varvalue.equals("1");
    }

    private boolean login() {
        String url = "http://" + host + "/data/Login.json";
        Request request = http.POST(url);
        request.header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded");
        request.content(new StringContentProvider("csrf_token=nulltoken&showpw=0&challengev=" + challengev
                + "&password=" + cryptoUtils.getPasswordHash(challengev, password)));

        ContentResponse response;
        try {
            response = request.send();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            callback.updateStatus(OFFLINE, COMMUNICATION_ERROR, "Unbale to connect to router at '" + host + "'.");
            return false;
        }

        JsonModelList models = gson.fromJson(fixContent(response.getContentAsString()), JsonModelList.class);
        JsonModel login = models.getModel("login");

        if (login != null && login.varvalue.equals("success")) {
            callback.updateStatus(ONLINE, NONE, null);
            return true;
        } else {
            callback.updateStatus(OFFLINE, CONFIGURATION_ERROR, "Invalid credentials for router at '" + host + "'.");
            return false;
        }
    }

    private String fixContent(String content) {
        return "{jsonModels:" + content + "}";
    }

    private void refreshCSRFToken() {
        String overviewURL = "http://" + host + "/html/content/overview/index.html";
        ContentResponse response = requestURL(overviewURL);

        if (response == null || response.getContentAsString().isEmpty()) {
            return;
        }

        String overview = response.getContentAsString();

        String beginPattern = "csrf_token = \"";
        String endPattern = "\";";
        int beginIndex = overview.indexOf(beginPattern);
        int endIndex = overview.indexOf(endPattern);
        csrfToken = overview.substring(beginIndex + beginPattern.length(), endIndex);
    }

    private @Nullable JsonModelList getLoginModel() {
        ensureLogin();
        String loginURL = "http://" + host + "/data/Login.json?showpw=0&csrf_token=" + csrfToken + "&challengev="
                + challengev + "&password=" + cryptoUtils.getPasswordHash(challengev, password);
        ContentResponse overviewResponse = null;
        try {
            overviewResponse = http.GET(loginURL);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            callback.updateStatus(OFFLINE, COMMUNICATION_ERROR, e.getLocalizedMessage());
            return null;
        }

        if (overviewResponse.getStatus() != HttpStatus.OK_200) {
            callback.updateStatus(OFFLINE, CONFIGURATION_ERROR, "Invalid credentials for router at '" + host + "'");
            return null;
        }

        return gson.fromJson(fixContent(overviewResponse.getContentAsString()), JsonModelList.class);
    }

    private void updateChannel(JsonModelList models, ChannelUID channelUID) {
        if (channelUID.getId().equals(CHANNEL_LTE)) {
            JsonModel use_lte = models.getModel("use_lte");
            if (use_lte != null && use_lte.varvalue.equals("1")) {
                callback.updateState(channelUID, OnOffType.ON);
            } else {
                callback.updateState(channelUID, OnOffType.OFF);
            }
        }
    }

    private void refreshChallengevOrCSRFToken() {
        String overviewURL = "http://" + host + "/html/content/overview/index.html";
        ContentResponse resp = requestURL(overviewURL);

        if (resp == null || resp.getContentAsString().isEmpty()) {
            return;
        }

        String content = resp.getContentAsString();

        if (content.indexOf(CHALLANGEV) > 0) {
            int beginIndex = content.indexOf(CHALLANGEV) + CHALLANGEV.length();
            challengev = content.substring(beginIndex, beginIndex + CHALLANGEV_LENGTH);
            logger.debug("Extracted challengev from SpeedportHybrid router: " + challengev);
        } else {
            String beginPattern = "csrf_token = \"";
            String endPattern = "\";";
            int beginIndex = content.indexOf(beginPattern);
            int endIndex = content.indexOf(endPattern);
            csrfToken = content.substring(beginIndex + beginPattern.length(), endIndex);
        }
    }

    private @Nullable ContentResponse requestURL(String url) {
        ContentResponse response;
        try {
            response = http.GET(url);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            callback.updateStatus(OFFLINE, COMMUNICATION_ERROR, e.getLocalizedMessage());
            return null;
        }

        if (response.getStatus() != HttpStatus.OK_200) {
            callback.updateStatus(OFFLINE, CONFIGURATION_ERROR,
                    "Unable to retrieve URL '" + url + "' from router at '" + host + "'.");
            return null;
        }

        return response;
    }

    private void requestEncrypted(String url, String data, CommandChannelUpdateCallback channelUpdateCallback) {
        byte[] encrypted;
        try {
            encrypted = cryptoUtils.encrypt(challengev, derivedKey, data);
        } catch (IllegalStateException | InvalidCipherTextException | DecoderException e) {
            logger.debug("Failed to encrypt request for router at '" + host + "'.");
            return;
        }

        Request request = http.POST(url);
        request.header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded");
        request.content(new BytesContentProvider(encrypted));

        ContentResponse response = null;
        try {
            response = request.send();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            callback.updateStatus(OFFLINE, COMMUNICATION_ERROR, "Unbale to connect to router at '" + host + "'.");
            return;
        }

        JsonModelList models = gson.fromJson(fixContent(response.getContentAsString()), JsonModelList.class);
        JsonModel status = models.getModel("status");
        if (status != null && status.varvalue.equals("ok")) {
            channelUpdateCallback.success();

            csrfToken = null;
            JsonModel csrfTokenModel = models.getModel("csfr_token");
            if (csrfTokenModel != null) {
                csrfToken = csrfTokenModel.varvalue;
            }
        }
    }
}
