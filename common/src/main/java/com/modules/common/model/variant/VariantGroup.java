package com.modules.common.model.variant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/** Gruppo di varianti a scelta singola, sempre obbligatorio (es. "Impasto"). Vedi docs/VARIANTI_PRODOTTO.md. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VariantGroup {
    private String id;
    private String name;
    private List<VariantChoice> choices = new ArrayList<>();

    public VariantGroup() {}

    public VariantGroup(String id, String name, List<VariantChoice> choices) {
        this.id = id;
        this.name = name;
        setChoices(choices);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<VariantChoice> getChoices() { return choices; }
    public void setChoices(List<VariantChoice> choices) { this.choices = choices == null ? new ArrayList<>() : choices; }

    @Override
    public String toString() {
        return "VariantGroup{id='" + id + "', name='" + name + "', choices=" + choices + '}';
    }
}
