package com.modules.common.model.variant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Scelta inviata dal client per un gruppo: {groupId, choiceId}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VariantSelection {
    private String groupId;
    private String choiceId;

    public VariantSelection() {}

    public VariantSelection(String groupId, String choiceId) {
        this.groupId = groupId;
        this.choiceId = choiceId;
    }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getChoiceId() { return choiceId; }
    public void setChoiceId(String choiceId) { this.choiceId = choiceId; }

    @Override
    public String toString() {
        return groupId + "=" + choiceId;
    }
}
