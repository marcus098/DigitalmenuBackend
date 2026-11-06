package com.modules.common.model.variant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Scelta di un gruppo di varianti (es. "Senza glutine" nel gruppo "Impasto"). Vedi docs/VARIANTI_PRODOTTO.md.
 * I campi assenti nel JSON prendono i valori predefiniti (disponibile, nessun allergene, nessuna evidenza).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VariantChoice {
    private String id;
    private String name;
    /** Supplemento in euro rispetto al prezzo del formato (può essere 0 o negativo). */
    private double priceDelta;
    private boolean isDefault;
    private boolean available = true;
    private List<Integer> removesAllergens = new ArrayList<>();
    private List<Integer> addsAllergens = new ArrayList<>();
    /** Il locale garantisce l'assenza: l'allergene tolto non finisce nelle tracce. */
    private boolean certifiedFree;
    private List<String> dietaryTags = new ArrayList<>();
    /** Stampata in evidenza sulla comanda. */
    private boolean highlight;

    public VariantChoice() {}

    public VariantChoice(String id, String name, double priceDelta, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.priceDelta = priceDelta;
        this.isDefault = isDefault;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getPriceDelta() { return priceDelta; }
    public void setPriceDelta(double priceDelta) { this.priceDelta = priceDelta; }

    @JsonProperty("isDefault")
    public boolean isDefault() { return isDefault; }
    @JsonProperty("isDefault")
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public List<Integer> getRemovesAllergens() { return removesAllergens; }
    public void setRemovesAllergens(List<Integer> removesAllergens) { this.removesAllergens = removesAllergens == null ? new ArrayList<>() : removesAllergens; }
    public List<Integer> getAddsAllergens() { return addsAllergens; }
    public void setAddsAllergens(List<Integer> addsAllergens) { this.addsAllergens = addsAllergens == null ? new ArrayList<>() : addsAllergens; }
    public boolean isCertifiedFree() { return certifiedFree; }
    public void setCertifiedFree(boolean certifiedFree) { this.certifiedFree = certifiedFree; }
    public List<String> getDietaryTags() { return dietaryTags; }
    public void setDietaryTags(List<String> dietaryTags) { this.dietaryTags = dietaryTags == null ? new ArrayList<>() : dietaryTags; }
    public boolean isHighlight() { return highlight; }
    public void setHighlight(boolean highlight) { this.highlight = highlight; }

    @Override
    public String toString() {
        return "VariantChoice{id='" + id + "', name='" + name + "', priceDelta=" + priceDelta + ", isDefault=" + isDefault
                + ", available=" + available + ", highlight=" + highlight + '}';
    }
}
