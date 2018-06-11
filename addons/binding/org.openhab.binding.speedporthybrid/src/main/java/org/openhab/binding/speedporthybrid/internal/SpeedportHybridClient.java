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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.codec.binary.Hex;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
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
    private static String CHALLANGEV = "challenge = \"";

    private HandlerCallback callback;

    @Nullable
    private HttpClient http;

    @Nullable
    private String host;

    @Nullable
    private String password;

    @Nullable
    private String csrfToken;

    private Gson gson = new Gson();

    public SpeedportHybridClient(HandlerCallback callback, @Nullable HttpClient http) {
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
        if (http == null) {
            return;
        }
        JsonModelList models = getLoginModel();
        if (models != null) {
            updateChannel(models, channelUID);
        }

    }

    public void login() {
        if (isLogin() && csrfToken != null) {
            return;
        } else {
            csrfToken = null;
            refreshCSRFToken();
        }
    }

    private boolean isLogin() {
        String heartbeat = requestURL("http://192.168.0.1/data/heartbeat.json");
        if (heartbeat == null || heartbeat.isEmpty()) {
            return false;
        }

        JsonModelList models = gson.fromJson("{jsonModels:" + heartbeat + "}", JsonModelList.class);
        JsonModel loginstate = models.getModel("loginstate");
        return loginstate != null && loginstate.varvalue.equals("1");

    }

    private @Nullable String requestURL(String url) {
        ContentResponse response;
        try {
            response = http.GET(url);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            callback.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getLocalizedMessage());
            return null;
        }

        if (response.getStatus() != HttpStatus.OK_200) {
            callback.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Unable to retrieve URL '" + url + "' from router at '" + host + "'");
            return null;
        } else {
            callback.updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE, null);
        }

        return response.getContentAsString();
    }

    private void refreshCSRFToken() {
        String overviewURL = "http://" + host + "/html/content/overview/index.html";
        String content = requestURL(overviewURL);

        String challangev = null;
        if (content != null) {
            challangev = getChallengev(content);
        }

        if (challangev == null || challangev.isEmpty()) {
            logger.debug("'challangev' value could not be retrieved from SpeedportHybrid device.");
            return;
        }

        String passwordHash = getPassword(challangev);

        String url = "http://" + host + "/data/Login.json";
        Request request = http.POST(url);
        request.header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded");
        request.content(new StringContentProvider(
                "csrf_token=nulltoken&showpw=0&challengev=" + challangev + "&password=" + passwordHash));

        ContentResponse response;
        try {
            response = request.send();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            callback.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Unbale to connect to router at '" + host + "'.");
            return;
        }

        JsonModelList models = gson.fromJson(response.getContentAsString(), JsonModelList.class);
        JsonModel login = models.getModel("login");

        if (login != null && login.varvalue.equals("success")) {
            callback.updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE, null);
            updateCSRFToken();
        } else {
            callback.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid credentials for router at '" + host + "'");

        }
    }

    private void updateCSRFToken() {
        String overviewURL = "http://" + host + "/html/content/overview/index.html";
        String response = requestURL(overviewURL);

        if (response == null) {
            return;
        }

        String beginPattern = "csrf_token = \"";
        String endPattern = "\";";
        int beginIndex = response.indexOf(beginPattern);
        int endIndex = response.indexOf(endPattern) - beginIndex - beginPattern.length();
        csrfToken = response.substring(beginIndex + beginPattern.length(), endIndex);
    }

    private @Nullable JsonModelList getLoginModel() {
        String loginURL = "http://" + host + "/data/Login.json";
        ContentResponse overviewResponse = null;
        try {
            overviewResponse = http.GET(loginURL);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            callback.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getLocalizedMessage());
            return null;
        }

        if (overviewResponse.getStatus() != HttpStatus.OK_200) {
            callback.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid credentials for router at '" + host + "'");
            return null;
        } else {
            callback.updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE, null);
        }

        String res = new String(overviewResponse.getContent());
        return gson.fromJson("{jsonModels:" + res + "}", JsonModelList.class);
    }

    private void updateChannel(JsonModelList models, ChannelUID channelUID) {
        if (channelUID.getId().equals("lte")) {
            JsonModel use_lte = models.getModel("use_lte");
            if (use_lte != null && use_lte.varvalue.equals("1")) {
                callback.updateState(channelUID, OnOffType.ON);
            } else {
                callback.updateState(channelUID, OnOffType.OFF);
            }
        }
    }

    private String getPassword(String challengev) {
        String pass = challengev + ":" + password;
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(pass.getBytes());

            return Hex.encodeHexString(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            logger.warn("Error creating SHA256 hash.", e);
        }

        return "";
    }

    private String getChallengev(String content) {
        int beginIndex = content.indexOf(CHALLANGEV) + CHALLANGEV.length();
        String challengev = content.substring(beginIndex, beginIndex + CHALLANGEV_LENGTH);
        logger.debug("Extracted challengev from SpeedportHybrid router: " + challengev);
        return challengev;
    }

    public void setModule(String module, String value) {
        String moduleURL = "http://" + host + "/data/Modules.json?" + module + "=" + value + "&csrf_token=" + csrfToken;

    }
}
