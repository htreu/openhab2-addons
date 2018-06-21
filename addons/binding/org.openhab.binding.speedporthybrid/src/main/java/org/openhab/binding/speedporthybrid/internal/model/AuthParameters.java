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
package org.openhab.binding.speedporthybrid.internal.model;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.speedporthybrid.internal.CryptoUtils;

/**
 * Represents the set of authentication parameters used to communicate with the SpeedPort Hybrid router.
 *
 * @author Henning Treu - initial contribution
 *
 */
@NonNullByDefault
public class AuthParameters {

    private static final String NULLTOKEN = "nulltoken";

    private final CryptoUtils cryptoUtils;

    @Nullable
    private String challengev;

    private String csrfToken;

    @Nullable
    private String derivedKey;

    @Nullable
    private String passwordHash;

    public AuthParameters() {
        csrfToken = NULLTOKEN;
        cryptoUtils = new CryptoUtils();
    }

    public @Nullable String getChallengev() {
        return challengev;
    }

    public void updateChallengev(String challengev, @Nullable String password) {
        this.challengev = challengev;
        this.derivedKey = cryptoUtils.deriveKey(challengev, password);
        this.passwordHash = cryptoUtils.hashPassword(challengev, password);
    }

    public String getCSRFToken() {
        return csrfToken;
    }

    public void updateCSRFToken(String csrfToken) {
        this.csrfToken = csrfToken;
    }

    public byte[] getDerivedKey() throws DecoderException {
        String dKey = this.derivedKey;
        if (dKey == null) {
            return new byte[0];
        }
        return Hex.decodeHex(dKey.toCharArray());
    }

    public boolean isValid() {
        return !csrfToken.equals(NULLTOKEN) && challengev != null && derivedKey != null;
    }

    public void reset() {
        csrfToken = NULLTOKEN;
        challengev = null;
        derivedKey = null;
        passwordHash = null;
    }

    public String getAuthData() {
        return "?showpw=0" + //
                "&csrf_token=" + csrfToken + //
                "&challengev=" + (challengev != null ? challengev : "") + //
                "&password=" + (challengev != null ? passwordHash : "");
    }
}
