package com.modules.common.model.variant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.common.model.IngredientOrderPlus;
import com.modules.common.model.OptionInProduct;

import java.util.*;

/**
 * Gruppi di varianti del prodotto (colonna products.variant_groups, JSON): lettura/scrittura, validazione,
 * risoluzione delle scelte di un ordine, prezzo unitario e allergeni effettivi. Vedi docs/VARIANTI_PRODOTTO.md.
 * Classe pura (nessuna dipendenza Spring), usata da main-app e webflux.
 */
public final class VariantGroups {

    public static final int MAX_GROUPS = 6;
    public static final int MIN_CHOICES = 2;
    public static final int MAX_CHOICES = 12;
    public static final int MAX_ID_LENGTH = 40;
    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_TAGS = 10;
    public static final int MAX_TAG_LENGTH = 30;
    public static final double MAX_ABS_DELTA = 1000;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final TypeReference<List<VariantGroup>> LIST = new TypeReference<>() {};

    private VariantGroups() {}

    // ── JSON ─────────────────────────────────────────────────────────────────

    /** JSON della colonna → gruppi. null, vuoto o JSON non valido = nessun gruppo (mai null). */
    public static List<VariantGroup> parse(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            List<VariantGroup> list = MAPPER.readValue(json, LIST);
            if (list == null) return new ArrayList<>();
            list.removeIf(Objects::isNull);
            for (VariantGroup g : list) g.getChoices().removeIf(Objects::isNull);
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * JSON ricevuto dal client (campo form variantGroups) → gruppi validati e normalizzati.
     *
     * @throws IllegalArgumentException con messaggio in italiano se il JSON o i gruppi non sono validi
     */
    public static List<VariantGroup> parseRequest(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        List<VariantGroup> list;
        try {
            list = MAPPER.readValue(json, LIST);
        } catch (Exception e) {
            throw new IllegalArgumentException("Gruppi di varianti non leggibili (JSON non valido)");
        }
        return validate(list);
    }

    /** Gruppi → JSON per la colonna; nessun gruppo = null. */
    public static String toJson(List<VariantGroup> groups) {
        if (groups == null || groups.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(groups);
        } catch (Exception e) {
            throw new IllegalStateException("Serializzazione gruppi di varianti non riuscita", e);
        }
    }

    // ── Validazione ──────────────────────────────────────────────────────────

    /**
     * Valida e normalizza (nomi/id senza spazi ai lati, allergeni ordinati senza duplicati, supplementi al centesimo,
     * etichette pulite). Regole: nomi non vuoti, id unici, 2..12 scelte per gruppo, esattamente una predefinita,
     * allergeni 1..14, al massimo 6 gruppi.
     *
     * @throws IllegalArgumentException con messaggio in italiano alla prima regola violata
     */
    public static List<VariantGroup> validate(List<VariantGroup> groups) {
        if (groups == null) return new ArrayList<>();
        if (groups.size() > MAX_GROUPS) throw new IllegalArgumentException("Al massimo " + MAX_GROUPS + " gruppi di varianti per prodotto");
        List<VariantGroup> out = new ArrayList<>();
        Set<String> groupIds = new HashSet<>();
        int gi = 0;
        for (VariantGroup g : groups) {
            gi++;
            if (g == null) throw new IllegalArgumentException("Gruppo di varianti " + gi + " non valido");
            String gName = clean(g.getName());
            if (gName.isEmpty()) throw new IllegalArgumentException("Il gruppo di varianti " + gi + " non ha un nome");
            if (gName.length() > MAX_NAME_LENGTH) throw new IllegalArgumentException("Nome del gruppo \"" + gName + "\" troppo lungo (massimo " + MAX_NAME_LENGTH + " caratteri)");
            String gId = cleanId(g.getId(), "il gruppo \"" + gName + "\"");
            if (!groupIds.add(gId)) throw new IllegalArgumentException("Id del gruppo \"" + gName + "\" ripetuto: ogni gruppo deve avere un id diverso");
            List<VariantChoice> choices = g.getChoices();
            if (choices.size() < MIN_CHOICES) throw new IllegalArgumentException("Il gruppo \"" + gName + "\" deve avere almeno " + MIN_CHOICES + " scelte");
            if (choices.size() > MAX_CHOICES) throw new IllegalArgumentException("Il gruppo \"" + gName + "\" può avere al massimo " + MAX_CHOICES + " scelte");

            List<VariantChoice> outChoices = new ArrayList<>();
            Set<String> choiceIds = new HashSet<>();
            int defaults = 0;
            int ci = 0;
            for (VariantChoice c : choices) {
                ci++;
                if (c == null) throw new IllegalArgumentException("Scelta " + ci + " del gruppo \"" + gName + "\" non valida");
                String cName = clean(c.getName());
                if (cName.isEmpty()) throw new IllegalArgumentException("La scelta " + ci + " del gruppo \"" + gName + "\" non ha un nome");
                if (cName.length() > MAX_NAME_LENGTH) throw new IllegalArgumentException("Nome della scelta \"" + cName + "\" troppo lungo (massimo " + MAX_NAME_LENGTH + " caratteri)");
                String cId = cleanId(c.getId(), "la scelta \"" + cName + "\"");
                if (!choiceIds.add(cId)) throw new IllegalArgumentException("Id della scelta \"" + cName + "\" ripetuto nel gruppo \"" + gName + "\"");
                double delta = c.getPriceDelta();
                if (Double.isNaN(delta) || Double.isInfinite(delta) || Math.abs(delta) > MAX_ABS_DELTA) {
                    throw new IllegalArgumentException("Supplemento non valido per \"" + cName + "\"");
                }
                if (c.isDefault()) defaults++;

                VariantChoice n = new VariantChoice(cId, cName, Math.round(delta * 100) / 100.0, c.isDefault());
                n.setAvailable(c.isAvailable());
                n.setRemovesAllergens(allergens(c.getRemovesAllergens(), cName));
                n.setAddsAllergens(allergens(c.getAddsAllergens(), cName));
                n.setCertifiedFree(c.isCertifiedFree());
                n.setDietaryTags(tags(c.getDietaryTags(), cName));
                n.setHighlight(c.isHighlight());
                outChoices.add(n);
            }
            if (defaults != 1) throw new IllegalArgumentException("Il gruppo \"" + gName + "\" deve avere esattamente una scelta predefinita");
            out.add(new VariantGroup(gId, gName, outChoices));
        }
        return out;
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ");
    }

    private static String cleanId(String id, String what) {
        String s = id == null ? "" : id.strip();
        if (s.isEmpty()) throw new IllegalArgumentException("Manca l'id per " + what);
        if (s.length() > MAX_ID_LENGTH) throw new IllegalArgumentException("Id troppo lungo per " + what + " (massimo " + MAX_ID_LENGTH + " caratteri)");
        return s;
    }

    private static List<Integer> allergens(List<Integer> list, String choiceName) {
        if (list == null) return new ArrayList<>();
        TreeSet<Integer> set = new TreeSet<>();
        for (Integer a : list) {
            if (a == null || a < 1 || a > 14) {
                throw new IllegalArgumentException("Allergene non valido nella scelta \"" + choiceName + "\": gli allergeni vanno da 1 a 14");
            }
            set.add(a);
        }
        return new ArrayList<>(set);
    }

    private static List<String> tags(List<String> list, String choiceName) {
        if (list == null) return new ArrayList<>();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String t : list) {
            String s = clean(t);
            if (s.isEmpty()) continue;
            if (s.length() > MAX_TAG_LENGTH) throw new IllegalArgumentException("Etichetta \"" + s + "\" troppo lunga nella scelta \"" + choiceName + "\"");
            set.add(s);
        }
        if (set.size() > MAX_TAGS) throw new IllegalArgumentException("Troppe etichette nella scelta \"" + choiceName + "\"");
        return new ArrayList<>(set);
    }

    // ── Disponibilità ────────────────────────────────────────────────────────

    /**
     * Esaurisce/ripristina una scelta (modifica la lista ricevuta).
     *
     * @return false se gruppo o scelta non esistono
     */
    public static boolean setChoiceAvailable(List<VariantGroup> groups, String groupId, String choiceId, boolean available) {
        if (groups == null) return false;
        for (VariantGroup g : groups) {
            if (!Objects.equals(g.getId(), groupId)) continue;
            for (VariantChoice c : g.getChoices()) {
                if (Objects.equals(c.getId(), choiceId)) {
                    c.setAvailable(available);
                    return true;
                }
            }
        }
        return false;
    }

    // ── Ordini ───────────────────────────────────────────────────────────────

    /**
     * Risolve le scelte inviate dal client contro i gruppi del prodotto (presi dal DB): una scelta per ogni gruppo,
     * nell'ordine dei gruppi; i gruppi omessi prendono la scelta predefinita.
     *
     * @throws IllegalArgumentException (messaggio in italiano) per gruppo/scelta inesistente o scelta non disponibile
     */
    public static List<SelectedVariant> resolve(String productName, List<VariantGroup> groups, List<VariantSelection> requested) {
        String product = productName == null || productName.isBlank() ? "il prodotto" : "\"" + productName + "\"";
        Map<String, String> wanted = new LinkedHashMap<>();
        if (requested != null) {
            for (VariantSelection s : requested) {
                if (s == null) continue;
                String g = s.getGroupId() == null ? "" : s.getGroupId().strip();
                String c = s.getChoiceId() == null ? "" : s.getChoiceId().strip();
                if (g.isEmpty() || c.isEmpty()) throw new IllegalArgumentException("Variante non valida per " + product);
                String prev = wanted.putIfAbsent(g, c);
                if (prev != null && !prev.equals(c)) {
                    throw new IllegalArgumentException("Più scelte per la stessa variante di " + product + ": se ne può scegliere una sola");
                }
            }
        }
        List<VariantGroup> list = groups == null ? List.of() : groups;
        Set<String> known = new HashSet<>();
        for (VariantGroup g : list) known.add(g.getId());
        for (String g : wanted.keySet()) {
            if (!known.contains(g)) throw new IllegalArgumentException("Variante non più presente per " + product + ": aggiorna il menu e riprova");
        }

        List<SelectedVariant> out = new ArrayList<>();
        for (VariantGroup g : list) {
            String choiceId = wanted.get(g.getId());
            VariantChoice choice;
            if (choiceId != null) {
                choice = g.getChoices().stream().filter(c -> choiceId.equals(c.getId())).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Scelta non più presente per " + g.getName() + " di " + product + ": aggiorna il menu e riprova"));
                if (!choice.isAvailable()) {
                    throw new IllegalArgumentException("\"" + choice.getName() + "\" (" + g.getName() + ") per " + product + " è esaurito: scegli un'altra opzione");
                }
            } else {
                choice = g.getChoices().stream().filter(VariantChoice::isDefault).findFirst()
                        .orElse(g.getChoices().isEmpty() ? null : g.getChoices().get(0));
                if (choice == null) continue;
                if (!choice.isAvailable()) {
                    throw new IllegalArgumentException("\"" + choice.getName() + "\" (" + g.getName() + ") per " + product + " è esaurito: scegli un'altra opzione per " + g.getName());
                }
            }
            out.add(new SelectedVariant(g.getId(), g.getName(), choice.getId(), choice.getName(), choice.getPriceDelta(), choice.isHighlight()));
        }
        return out;
    }

    /** Prezzo unitario in centesimi = formato + Σ supplementi delle varianti + extra (mai negativo). */
    public static long unitPriceCents(OptionInProduct option, List<SelectedVariant> variants, List<IngredientOrderPlus> extras) {
        long cents = option != null ? Math.max(0, Math.round(option.getPrice() * 100)) : 0;
        if (variants != null) {
            for (SelectedVariant v : variants) {
                if (v != null) cents += Math.round(v.getPriceDelta() * 100);
            }
        }
        if (extras != null) {
            for (IngredientOrderPlus e : extras) {
                if (e != null) cents += Math.max(0, Math.round(e.getPrice() * 100));
            }
        }
        return Math.max(0, cents);
    }

    // ── Allergeni ────────────────────────────────────────────────────────────

    /** Allergeni effettivi di una combinazione e tracce ("può contenere tracce di"), id 1..14 ordinati. */
    public record EffectiveAllergens(List<Integer> allergens, List<Integer> traces) {}

    /**
     * Allergeni = (prodotto + ingredienti) − removesAllergens + addsAllergens.
     * Un allergene tolto finisce nelle tracce salvo certifiedFree; le tracce del prodotto restano
     * (tranne quelle che diventano allergeni veri).
     */
    public static EffectiveAllergens effectiveAllergens(Collection<Integer> base, Collection<Integer> baseTraces, List<VariantChoice> chosen) {
        TreeSet<Integer> allergens = new TreeSet<>();
        if (base != null) base.stream().filter(VariantGroups::validAllergen).forEach(allergens::add);
        TreeSet<Integer> traces = new TreeSet<>();
        if (baseTraces != null) baseTraces.stream().filter(VariantGroups::validAllergen).forEach(traces::add);
        TreeSet<Integer> added = new TreeSet<>();
        if (chosen != null) {
            for (VariantChoice c : chosen) {
                if (c == null) continue;
                for (Integer a : c.getRemovesAllergens()) {
                    if (!validAllergen(a)) continue;
                    boolean wasPresent = allergens.remove(a);
                    if (wasPresent && !c.isCertifiedFree()) traces.add(a);
                }
                for (Integer a : c.getAddsAllergens()) if (validAllergen(a)) added.add(a);
            }
        }
        allergens.addAll(added);
        traces.removeAll(allergens);
        return new EffectiveAllergens(new ArrayList<>(allergens), new ArrayList<>(traces));
    }

    private static boolean validAllergen(Integer a) {
        return a != null && a >= 1 && a <= 14;
    }
}
