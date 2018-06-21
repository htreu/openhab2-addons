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
import org.openhab.binding.speedporthybrid.internal.model.AuthParameters;
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
    private static final String CSRF_TOKEN = "csrf_token";
    private static final String NULLTOKEN = "nulltoken";

    private static CryptoUtils cryptoUtils = new CryptoUtils();

    private final HandlerCallback callback;

    private final HttpClient http;

    @Nullable
    private String host;

    @Nullable
    private String password;

    private AuthParameters authParameters;

    private Gson gson = new Gson();

    public SpeedportHybridClient(HandlerCallback callback, HttpClient http) {
        this.callback = callback;
        this.http = http;
        this.authParameters = new AuthParameters();
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
        String data = module + "=" + value;
        requestEncrypted("/data/Modules.json", data, channelUpdateCallback);
    }

    public void ensureLogin() {
        if (authParameters.isValid() && isLogin()) {
            return;
        } else {
            authParameters.reset();
            refreshChallengev();
            if (authParameters.getChallengev() != null && login()) {
                refreshCSRFToken();
            } else {
                logger.warn("No login at '{}', challangev: '{}'", host, authParameters.getChallengev());
            }
        }
    }

    private boolean isLogin() {
        String heartbeat = request("/data/heartbeat.json", true);
        if (heartbeat == null || heartbeat.isEmpty()) {
            return false;
        }

        JsonModelList models = gson.fromJson(fixContent(heartbeat), JsonModelList.class);
        JsonModel loginstate = models.getModel("loginstate");
        boolean hasLogin = loginstate != null && loginstate.varvalue.equals("1");
        logger.debug("Login status at '{}' is {}", host, hasLogin);

        return hasLogin;
    }

    private boolean login() {
        String url = "http://" + host + "/data/Login.json";
        Request request = http.POST(url);
        request.header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded");
        request.content(new StringContentProvider(authParameters.getAuthData()));

        ContentResponse response;
        try {
            response = request.send();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.debug("Unable to connect to router at '{}': {}", host, e.getLocalizedMessage());
            callback.updateStatus(OFFLINE, COMMUNICATION_ERROR, "Unbale to connect to router at '" + host + "'.");
            return false;
        }

        JsonModelList models = gson.fromJson(fixContent(response.getContentAsString()), JsonModelList.class);
        JsonModel login = models.getModel("login");

        if (login != null && login.varvalue.equals("success")) {
            logger.debug("Successful login at '{}'.", host);
            callback.updateStatus(ONLINE, NONE, null);
            return true;
        } else {
            logger.debug("Login failed at '{}'.", host);
            callback.updateStatus(OFFLINE, CONFIGURATION_ERROR, "Invalid credentials for router at '" + host + "'.");
            return false;
        }
    }

    private String fixContent(String content) {
        return "{jsonModels:" + content + "}";
    }

    private void refreshCSRFToken() {
        String overview = request("/html/content/overview/index.html", true);

        if (overview == null || overview.isEmpty()) {
            return;
        }

        String beginPattern = CSRF_TOKEN + " = \"";
        String endPattern = "\";";
        int beginIndex = overview.indexOf(beginPattern);
        int endIndex = overview.indexOf(endPattern);
        authParameters.updateCSRFToken(overview.substring(beginIndex + beginPattern.length(), endIndex));
    }

    private @Nullable JsonModelList getLoginModel() {
        ensureLogin();
        String login = request("/data/Login.json", true);

        if (login == null || login.isEmpty()) {
            return null;
        }

        return gson.fromJson(fixContent(login), JsonModelList.class);
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

    private void refreshChallengev() {
        String content = request("", false);

        if (content == null || content.isEmpty()) {
            return;
        }

        if (content.indexOf(CHALLANGEV) > 0) {
            int beginIndex = content.indexOf(CHALLANGEV) + CHALLANGEV.length();
            String challengev = content.substring(beginIndex, beginIndex + CHALLANGEV_LENGTH);
            logger.debug("Extracted challengev '{}' from router at {}. ", challengev, host);

            authParameters.updateChallengev(challengev, password);
        } else {
            logger.debug("Challengev not extracted from router at {}. ", host);
        }
    }

    private @Nullable String request(String path, boolean attachAuthParams) {
        String finalURL = "http://" + host + path;

        if (attachAuthParams) {
            finalURL += authParameters.getAuthData();
        }
        ContentResponse response;
        try {
            response = http.GET(finalURL);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            callback.updateStatus(OFFLINE, COMMUNICATION_ERROR, e.getLocalizedMessage());
            return null;
        }

        if (response.getStatus() != HttpStatus.OK_200) {
            logger.warn("Failed to connect to router at '{}', got status '{}' with message '{}'.", host,
                    response.getStatus(), response.getContentAsString());
            callback.updateStatus(OFFLINE, CONFIGURATION_ERROR,
                    "Unable to retrieve URL '" + path + "' from router at '" + host + "'.");
            return null;
        }

        return response.getContentAsString();
    }

    private void requestEncrypted(String path, String data, CommandChannelUpdateCallback channelUpdateCallback) {
        byte[] encrypted;
        try {
            String fullData = CSRF_TOKEN + "=" + authParameters.getCSRFToken() + "&" + data;
            encrypted = cryptoUtils.encrypt(authParameters.getChallengev(), authParameters.getDerivedKey(), fullData);
        } catch (IllegalStateException | InvalidCipherTextException | DecoderException e) {
            logger.debug("Failed to encrypt request for router at '" + host + "'.");
            return;
        }

        Request request = http.POST("http://" + host + path);
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
        } else {
            logger.warn("Failed to update data '{}' on router at '{}'", data, host);
        }

        authParameters.updateCSRFToken(NULLTOKEN);
        JsonModel csrfTokenModel = models.getModel(CSRF_TOKEN);
        if (csrfTokenModel != null) {
            authParameters.updateCSRFToken(csrfTokenModel.varvalue);
        }
    }
}
