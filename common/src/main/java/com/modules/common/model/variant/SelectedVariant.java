package com.modules.common.model.variant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Variante scelta per una riga della comanda: snapshot di nomi e supplemento al momento dell'ordine
 * (se il prodotto cambia dopo, la comanda resta com'era).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SelectedVariant {
    private String groupId;
    private String groupName;
    private String choiceId;
    private String choiceName;
    private double priceDelta;
    private boolean highlight;

    public SelectedVariant() {}

    public SelectedVariant(String groupId, String groupName, String choiceId, String choiceName, double priceDelta, boolean highlight) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.choiceId = choiceId;
        this.choiceName = choiceName;
        this.priceDelta = priceDelta;
        this.highlight = highlight;
    }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getChoiceId() { return choiceId; }
    public void setChoiceId(String choiceId) { this.choiceId = choiceId; }
    public String getChoiceName() { return choiceName; }
    public void setChoiceName(String choiceName) { this.choiceName = choiceName; }
    public double getPriceDelta() { return priceDelta; }
    public void setPriceDelta(double priceDelta) { this.priceDelta = priceDelta; }
    public boolean isHighlight() { return highlight; }
    public void setHighlight(boolean highlight) { this.highlight = highlight; }

    @Override
    public String toString() {
        return "SelectedVariant{" + groupId + "=" + choiceId + ", priceDelta=" + priceDelta + ", highlight=" + highlight + '}';
    }
}
