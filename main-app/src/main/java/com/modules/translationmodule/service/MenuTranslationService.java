package com.modules.translationmodule.service;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.authmodule.repository.AgencyRepository;
import com.modules.categorymodule.repository.CategoryRepository;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.ingredientmodule.repository.IngredientRepository;
import com.modules.productmodule.repository.ProductRepository;
import com.modules.translationmodule.ai.AiUnavailableException;
import com.modules.translationmodule.ai.OllamaClient;
import com.modules.translationmodule.model.MenuLanguageSettingsJpa;
import com.modules.translationmodule.model.MenuTranslationJpa;
import com.modules.translationmodule.model.MenuTranslationJpa.EntityType;
import com.modules.translationmodule.model.MenuTranslationJpa.Field;
import com.modules.translationmodule.model.MenuTranslationJpa.TranslationKey;
import com.modules.translationmodule.repository.MenuLanguageSettingsRepository;
import com.modules.translationmodule.repository.MenuTranslationRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Menu in più lingue: testi italiani del locale tradotti con Ollama, salvati e serviti al menu pubblico.
 * Una traduzione vale finché il testo italiano non cambia (impronta {@code sourceHash});
 * quelle corrette a mano non vengono mai sovrascritte.
 */
@Service
public class MenuTranslationService {

    /** Testi per chiamata a Ollama: abbastanza da essere veloci, pochi da non confondere un modello 7B. */
    static final int BATCH_SIZE = 15;
    private static final long PUBLIC_CACHE_MS = 60_000;

    @Autowired private MenuTranslationRepository translationRepository;
    @Autowired private MenuLanguageSettingsRepository settingsRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private AgencyRepository agencyRepository;
    @Autowired private MenuTranslator translator;
    @Autowired private OllamaClient ollama;

    /** Un solo lavoro alla volta per tutto il server: Ollama elabora comunque una richiesta per volta. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "menu-translation");
        t.setDaemon(true);
        return t;
    });
    private final Map<Long, JobStatus> jobs = new ConcurrentHashMap<>();
    private final Map<String, CachedMenu> publicCache = new ConcurrentHashMap<>();

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }

    // ── Modelli esposti ──────────────────────────────────────────────────────

    public record Source(TranslationKey key, String text) {}

    public record JobStatus(boolean running, String lang, int done, int total, String error,
                            OffsetDateTime startedAt, OffsetDateTime finishedAt) {
        static JobStatus idle() { return new JobStatus(false, null, 0, 0, null, null, null); }
    }

    public record LanguageInfo(String code, String label) {}

    public record Settings(List<String> languages, boolean autoTranslate, boolean aiEnabled, List<LanguageInfo> supported) {}

    public record LanguageOverview(String lang, int total, int upToDate, int stale, int missing, int manual) {}

    public record Overview(Settings settings, List<LanguageOverview> languages, JobStatus job) {}

    /** status: OK, STALE (testo italiano cambiato), MISSING. */
    public record Row(String type, long id, String field, String source, String translation, String status, boolean manual) {}

    /** Menu pubblico: tipo → id → campo → testo. */
    public record PublicMenu(String lang, Map<String, Map<Long, Map<String, String>>> texts) {}

    private record CachedMenu(PublicMenu menu, long at) {}

    // ── Impostazioni ─────────────────────────────────────────────────────────

    public Settings getSettings(long idAgency) {
        MenuLanguageSettingsJpa s = settingsRepository.findById(idAgency).orElse(null);
        return new Settings(languagesOf(s), s != null && s.isAutoTranslate(), ollama.isEnabled(),
                MenuTranslator.SUPPORTED.stream().map(l -> new LanguageInfo(l.code(), l.label())).toList());
    }

    public Settings updateSettings(long idAgency, List<String> languages, boolean autoTranslate) {
        List<String> clean = languages == null ? List.of() : languages.stream()
                .filter(Objects::nonNull).map(String::trim).distinct().toList();
        for (String l : clean) {
            if (MenuTranslator.language(l).isEmpty()) throw new IllegalArgumentException("Lingua non supportata: " + l);
        }
        MenuLanguageSettingsJpa s = settingsRepository.findById(idAgency).orElseGet(() -> new MenuLanguageSettingsJpa(idAgency));
        s.setLanguages(String.join(",", clean));
        s.setAutoTranslate(autoTranslate);
        s.setUpdatedAt(OffsetDateTime.now());
        settingsRepository.save(s);
        evictPublic(idAgency);
        return getSettings(idAgency);
    }

    private static List<String> languagesOf(MenuLanguageSettingsJpa s) {
        if (s == null || s.getLanguages() == null || s.getLanguages().isBlank()) return List.of();
        return Arrays.stream(s.getLanguages().split(",")).map(String::trim)
                .filter(c -> MenuTranslator.language(c).isPresent()).toList();
    }

    // ── Testi del menu ───────────────────────────────────────────────────────

    /** Tutti i testi italiani da tradurre del locale (elementi non eliminati, testi non vuoti). */
    List<Source> sources(long idAgency) {
        List<Source> out = new ArrayList<>();
        categoryRepository.findAllByIdAgencyAndDeletedOrderByPositionProgressive(idAgency, false).forEach(c -> {
            add(out, EntityType.CATEGORY, c.getId(), Field.NAME, c.getName());
            add(out, EntityType.CATEGORY, c.getId(), Field.DESCRIPTION, c.getDescription());
        });
        productRepository.findAllByDeletedAndIdAgency(false, idAgency).forEach(p -> {
            add(out, EntityType.PRODUCT, p.getId(), Field.NAME, p.getName());
            add(out, EntityType.PRODUCT, p.getId(), Field.DESCRIPTION, p.getDescription());
        });
        ingredientRepository.findAllByIdAgencyAndDeletedOrderByName(idAgency, false).forEach(i ->
                add(out, EntityType.INGREDIENT, i.getId(), Field.NAME, i.getName()));
        return out;
    }

    private static void add(List<Source> out, EntityType type, Long id, Field field, String text) {
        if (id == null || text == null || text.isBlank()) return;
        out.add(new Source(new TranslationKey(type, id, field), text.strip()));
    }

    static String hash(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.strip().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<TranslationKey, MenuTranslationJpa> existing(long idAgency, String lang) {
        return translationRepository.findAllByIdAgencyAndLang(idAgency, lang).stream()
                .collect(Collectors.toMap(MenuTranslationJpa::key, Function.identity(), (a, b) -> a));
    }

    // ── Stato e modifica manuale ─────────────────────────────────────────────

    public Overview overview(long idAgency) {
        Settings settings = getSettings(idAgency);
        List<Source> sources = sources(idAgency);
        List<LanguageOverview> langs = new ArrayList<>();
        for (String lang : settings.languages()) {
            Map<TranslationKey, MenuTranslationJpa> ex = existing(idAgency, lang);
            int ok = 0, stale = 0, missing = 0, manual = 0;
            for (Source s : sources) {
                MenuTranslationJpa t = ex.get(s.key());
                if (t == null) missing++;
                else if (hash(s.text()).equals(t.getSourceHash())) ok++;
                else stale++;
                if (t != null && t.isManual()) manual++;
            }
            langs.add(new LanguageOverview(lang, sources.size(), ok, stale, missing, manual));
        }
        return new Overview(settings, langs, jobs.getOrDefault(idAgency, JobStatus.idle()));
    }

    public List<Row> rows(long idAgency, String lang) {
        requireLanguage(lang);
        Map<TranslationKey, MenuTranslationJpa> ex = existing(idAgency, lang);
        return sources(idAgency).stream().map(s -> {
            MenuTranslationJpa t = ex.get(s.key());
            String status = t == null ? "MISSING" : hash(s.text()).equals(t.getSourceHash()) ? "OK" : "STALE";
            return new Row(s.key().type().name(), s.key().id(), s.key().field().name(), s.text(),
                    t == null ? null : t.getText(), status, t != null && t.isManual());
        }).toList();
    }

    /** Correzione a mano; testo vuoto = torna alla traduzione automatica. */
    public Row saveManual(long idAgency, String lang, String type, long id, String field, String text) {
        requireLanguage(lang);
        TranslationKey key;
        try {
            key = new TranslationKey(EntityType.valueOf(type), id, Field.valueOf(field));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Elemento non valido");
        }
        Source source = sources(idAgency).stream().filter(s -> s.key().equals(key)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Elemento del menu non trovato"));
        Optional<MenuTranslationJpa> current = translationRepository.findByIdAgencyAndEntityTypeAndEntityIdAndLangAndField(
                idAgency, key.type().name(), key.id(), lang, key.field().name());
        evictPublic(idAgency);
        if (text == null || text.isBlank()) {
            current.ifPresent(translationRepository::delete);
            return new Row(type, id, field, source.text(), null, "MISSING", false);
        }
        if (text.length() > 2000) throw new IllegalArgumentException("Testo troppo lungo");
        MenuTranslationJpa t = current.orElseGet(() -> new MenuTranslationJpa(idAgency, key, lang));
        t.setText(text.strip());
        t.setSourceHash(hash(source.text()));
        t.setManual(true);
        t.setUpdatedAt(OffsetDateTime.now());
        translationRepository.save(t);
        return new Row(type, id, field, source.text(), t.getText(), "OK", true);
    }

    private static void requireLanguage(String lang) {
        if (MenuTranslator.language(lang).isEmpty()) throw new IllegalArgumentException("Lingua non supportata");
    }

    // ── Traduzione automatica ────────────────────────────────────────────────

    /** Avvia in background la traduzione dei testi nuovi o modificati nelle lingue indicate (tutte se vuoto). */
    public JobStatus start(long idAgency, List<String> requested) {
        if (!ollama.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Traduzione automatica non attiva sul server");
        }
        List<String> enabled = getSettings(idAgency).languages();
        List<String> langs = requested == null || requested.isEmpty() ? enabled
                : requested.stream().filter(enabled::contains).toList();
        if (langs.isEmpty()) throw new IllegalArgumentException("Scegli prima almeno una lingua");

        JobStatus queued = new JobStatus(true, langs.get(0), 0, 0, null, OffsetDateTime.now(), null);
        JobStatus previous = jobs.putIfAbsent(idAgency, queued);
        if (previous != null) {
            if (previous.running()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Traduzione già in corso");
            if (!jobs.replace(idAgency, previous, queued)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Traduzione già in corso");
        }
        executor.submit(() -> run(idAgency, langs));
        return queued;
    }

    /** Esegue il lavoro nel thread corrente (usato dal job notturno e dai test). */
    void run(long idAgency, List<String> langs) {
        OffsetDateTime started = OffsetDateTime.now();
        try {
            List<Source> sources = sources(idAgency);
            Map<String, List<Source>> pending = new LinkedHashMap<>();
            int total = 0;
            for (String lang : langs) {
                Map<TranslationKey, MenuTranslationJpa> ex = existing(idAgency, lang);
                List<Source> todo = sources.stream().filter(s -> {
                    MenuTranslationJpa t = ex.get(s.key());
                    return t == null || (!t.isManual() && !hash(s.text()).equals(t.getSourceHash()));
                }).toList();
                pending.put(lang, todo);
                total += todo.size();
            }
            int done = 0;
            for (Map.Entry<String, List<Source>> e : pending.entrySet()) {
                String lang = e.getKey();
                MenuTranslator.Language language = MenuTranslator.language(lang).orElseThrow();
                List<Source> todo = e.getValue();
                for (int i = 0; i < todo.size(); i += BATCH_SIZE) {
                    jobs.put(idAgency, new JobStatus(true, lang, done, total, null, started, null));
                    List<Source> batch = todo.subList(i, Math.min(i + BATCH_SIZE, todo.size()));
                    saveBatch(idAgency, lang, batch, translator.translate(language, items(batch)));
                    done += batch.size();
                }
                evictPublic(idAgency);
            }
            jobs.put(idAgency, new JobStatus(false, null, done, total, null, started, OffsetDateTime.now()));
            ErrorLog.logger.info("Traduzione menu completata (agency {}, lingue {}, testi {})", idAgency, langs, total);
        } catch (AiUnavailableException e) {
            JobStatus last = jobs.getOrDefault(idAgency, JobStatus.idle());
            jobs.put(idAgency, new JobStatus(false, last.lang(), last.done(), last.total(), e.getMessage(), started, OffsetDateTime.now()));
            evictPublic(idAgency);
        } catch (Exception e) {
            ErrorLog.logger.error("Traduzione menu non riuscita (agency " + idAgency + ")", e);
            JobStatus last = jobs.getOrDefault(idAgency, JobStatus.idle());
            jobs.put(idAgency, new JobStatus(false, last.lang(), last.done(), last.total(), "Errore imprevisto durante la traduzione", started, OffsetDateTime.now()));
            evictPublic(idAgency);
        }
    }

    static List<MenuTranslator.Item> items(List<Source> batch) {
        List<MenuTranslator.Item> items = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            TranslationKey k = batch.get(i).key();
            String kind = switch (k.type()) {
                case CATEGORY -> k.field() == Field.NAME ? "category" : "description";
                case PRODUCT -> k.field() == Field.NAME ? "dish" : "description";
                case INGREDIENT -> "ingredient";
            };
            items.add(new MenuTranslator.Item(String.valueOf(i), kind, batch.get(i).text()));
        }
        return items;
    }

    private void saveBatch(long idAgency, String lang, List<Source> batch, Map<String, String> translated) {
        Map<TranslationKey, MenuTranslationJpa> ex = existing(idAgency, lang);
        List<MenuTranslationJpa> toSave = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            String text = translated.get(String.valueOf(i));
            if (text == null) continue; // resta da tradurre: verrà ripreso al prossimo giro
            Source s = batch.get(i);
            MenuTranslationJpa t = ex.getOrDefault(s.key(), new MenuTranslationJpa(idAgency, s.key(), lang));
            if (t.isManual()) continue;
            t.setText(text);
            t.setSourceHash(hash(s.text()));
            t.setUpdatedAt(OffsetDateTime.now());
            toSave.add(t);
        }
        translationRepository.saveAll(toSave);
    }

    /** Ogni notte traduce testi nuovi o modificati per i locali che l'hanno chiesto. */
    @Scheduled(cron = "0 30 4 * * *", zone = "Europe/Rome")
    public void nightly() {
        if (!ollama.isEnabled()) return;
        for (MenuLanguageSettingsJpa s : settingsRepository.findAllByAutoTranslateTrue()) {
            List<String> langs = languagesOf(s);
            if (langs.isEmpty() || jobs.getOrDefault(s.getIdAgency(), JobStatus.idle()).running()) continue;
            jobs.put(s.getIdAgency(), new JobStatus(true, langs.get(0), 0, 0, null, OffsetDateTime.now(), null));
            run(s.getIdAgency(), langs);
        }
    }

    // ── Menu pubblico ────────────────────────────────────────────────────────

    /** Lingue offerte dal locale (italiano escluso, è sempre disponibile). */
    public List<LanguageInfo> publicLanguages(String idOrName) {
        long idAgency = resolveAgency(idOrName);
        Set<String> enabled = new HashSet<>(getSettings(idAgency).languages());
        return MenuTranslator.SUPPORTED.stream().filter(l -> enabled.contains(l.code()))
                .map(l -> new LanguageInfo(l.code(), l.label())).toList();
    }

    /** Solo traduzioni aggiornate: un testo cambiato e non ancora ritradotto resta in italiano. */
    public PublicMenu publicMenu(String idOrName, String lang) {
        long idAgency = resolveAgency(idOrName);
        if (!getSettings(idAgency).languages().contains(lang)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lingua non disponibile");
        }
        String cacheKey = idAgency + ":" + lang;
        CachedMenu cached = publicCache.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.at() < PUBLIC_CACHE_MS) return cached.menu();

        Map<TranslationKey, MenuTranslationJpa> ex = existing(idAgency, lang);
        Map<String, Map<Long, Map<String, String>>> texts = new LinkedHashMap<>();
        for (Source s : sources(idAgency)) {
            MenuTranslationJpa t = ex.get(s.key());
            if (t == null || !hash(s.text()).equals(t.getSourceHash())) continue;
            texts.computeIfAbsent(s.key().type().name(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(s.key().id(), k -> new LinkedHashMap<>())
                    .put(s.key().field().name(), t.getText());
        }
        PublicMenu menu = new PublicMenu(lang, texts);
        publicCache.put(cacheKey, new CachedMenu(menu, System.currentTimeMillis()));
        return menu;
    }

    private void evictPublic(long idAgency) {
        publicCache.keySet().removeIf(k -> k.startsWith(idAgency + ":"));
    }

    private long resolveAgency(String idOrName) {
        Optional<AgencyJpa> agency = idOrName != null && idOrName.matches("\\d{1,18}")
                ? agencyRepository.findByIdAndDeleted(Long.parseLong(idOrName), false)
                : agencyRepository.findByNameAndDeleted(idOrName, false);
        return agency.map(AgencyJpa::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Locale non trovato"));
    }
}
