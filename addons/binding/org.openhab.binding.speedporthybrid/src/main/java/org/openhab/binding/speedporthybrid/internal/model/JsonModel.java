package org.openhab.binding.speedporthybrid.internal.model;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public class JsonModel {

    public @Nullable String vartype;
    public @Nullable String varid;
    public @Nullable String varvalue;

    public boolean hasValue(String value) {
        return value.equals(varvalue);
    }
}
