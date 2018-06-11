package org.openhab.binding.speedporthybrid.internal.model;

import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;

public class JsonModelList {

    public List<JsonModel> jsonModels;

    public @Nullable JsonModel getModel(String varId) {
        Optional<JsonModel> model = jsonModels.stream().filter(lm -> lm.varid.equals(varId)).findFirst();
        if (model.isPresent()) {
            return model.get();
        }

        return null;
    }

}
