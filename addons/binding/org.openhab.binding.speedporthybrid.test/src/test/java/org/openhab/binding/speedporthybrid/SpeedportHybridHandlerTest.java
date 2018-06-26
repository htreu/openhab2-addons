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
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerCallback;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.openhab.binding.speedporthybrid.internal.SpeedportHybridHandler;

/**
 * Test cases for {@link SpeedportHybridHandler}. The tests provide mocks for supporting entities using Mockito.
 *
 * @author Henning Treu - Initial contribution
 */
public class SpeedportHybridHandlerTest {

    private static final String CHALLENGEV = "fFgzzJQwLgirK2woHxdoD3e7dVsdjJUiFg3tXk8umoxUyCh7PVyiJgijX63C6XqG";

    private static final String CSRF_TOKEN = "Z26Z4KAQHGSY634SZDDNP79DKM8R6KRTLEWUNW5PU93D669Y";

    private ThingHandler handler;

    @Mock
    private ThingHandlerCallback callback;

    @Mock
    private Thing thing;

    @Mock
    private HttpClient httpClient;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        handler = new SpeedportHybridHandler(thing, httpClient);
        handler.setCallback(callback);

        Map<String, Object> properties = new HashMap<>();
        properties.put("host", "localhost");
        properties.put("password", "19472990");
        when(thing.getConfiguration()).thenReturn(new Configuration(properties));

        mockSpeedportHttpBackend();

    }

    private void mockSpeedportHttpBackend() throws Exception {
        ContentResponse challengevResponse = mockChallengevResponse();
        when(httpClient.GET("http://localhost")).thenReturn(challengevResponse);

        Request loginRequest = mock(Request.class);
        when(httpClient.POST("http://localhost/data/Login.json")).thenReturn(loginRequest);
        ContentResponse loginResponse = mockLoginResponse();
        when(loginRequest.send()).thenReturn(loginResponse);

        ContentResponse csrfResponse = mockCSRFResponse();
        when(httpClient
                .GET("http://localhost/html/content/overview/index.html?showpw=0&csrf_token=nulltoken&challengev="
                        + CHALLENGEV + "&password=41ff7e983d396649e87d1e5f827e01b010038a61e9dcf2a6c5ebcab5c985f763"))
                                .thenReturn(csrfResponse);
    }

    private ContentResponse mockCSRFResponse() {
        ContentResponse csrfResponse = mock(ContentResponse.class);
        when(csrfResponse.getStatus()).thenReturn(200);
        when(csrfResponse.getContentAsString()).thenReturn("<html> ..." //
                + "csrf_token = \"" + CSRF_TOKEN + "\";");
        return csrfResponse;
    }

    private ContentResponse mockLoginResponse() {
        ContentResponse loginResponse = mock(ContentResponse.class);
        when(loginResponse.getContentAsString()).thenReturn("[ " //
                + "{varid:\"login\", varvalue:\"success\" }]");
        return loginResponse;
    }

    private ContentResponse mockChallengevResponse() {
        ContentResponse challengevResponse = mock(ContentResponse.class);
        when(challengevResponse.getStatus()).thenReturn(200);
        when(challengevResponse.getContentAsString()).thenReturn("<html> ..." //
                + "challenge = \"" + CHALLENGEV + "\"");
        return challengevResponse;
    }

    @Test
    public void initializeShouldCallTheCallback() {
        // we expect the handler#initialize method to call the callback during execution and
        // pass it the thing and a ThingStatusInfo object containing the ThingStatus of the thing.
        handler.initialize();

        // the argument captor will capture the argument of type ThingStatusInfo given to the
        // callback#statusUpdated method.
        ArgumentCaptor<ThingStatusInfo> statusInfoCaptor = ArgumentCaptor.forClass(ThingStatusInfo.class);

        // verify the interaction with the callback and capture the ThingStatusInfo argument:
        verify(callback).statusUpdated(eq(thing), statusInfoCaptor.capture());
        // assert that the ThingStatusInfo given to the callback was build with the ONLINE status:
        ThingStatusInfo thingStatusInfo = statusInfoCaptor.getValue();
        assertThat(thingStatusInfo.getStatus(), is(equalTo(ThingStatus.ONLINE)));
    }
}
