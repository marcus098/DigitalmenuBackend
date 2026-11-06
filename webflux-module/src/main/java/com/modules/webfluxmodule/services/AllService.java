package com.modules.webfluxmodule.services;

import com.modules.common.dto.*;
import com.modules.common.mapper.CategoryMapper;
import com.modules.common.mapper.IngredientMapper;
import com.modules.common.mapper.ProductMapper;
import com.modules.common.model.LongInteger;
import com.modules.common.model.enums.ComandStatus;
import com.modules.webfluxmodule.models.ListToExport;
import com.modules.webfluxmodule.models.db.AgencyR2dbc;
import com.modules.webfluxmodule.models.db.CategoryR2dbc;
import com.modules.webfluxmodule.models.db.ComandReactive;
import com.modules.webfluxmodule.models.db.Users;
import com.modules.webfluxmodule.repositories.*;
import com.modules.common.model.variant.VariantChoice;
import com.modules.common.model.variant.VariantGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AllService {
    @Autowired
    private WebfluxCategoryRepository webfluxCategoryRepository;
    @Autowired
    private WebfluxIngredientRepository webfluxIngredientRepository;
    @Autowired
    private WebfluxProductRepository webfluxProductRepository;
    @Autowired
    private WebfluxImageRepository webfluxImageRepository;
    @Autowired
    private WebfluxTableRepository webfluxTableRepository;
    @Autowired
    private WebfluxAgencyRepository webfluxAgencyRepository;
    @Autowired
    private WebfluxComandRepository webfluxComandRepository;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private IngredientMapper ingredientMapper;
    @Autowired
    private WebfluxStyleRepository webfluxStyleRepository;
    @Autowired
    private DatabaseClient databaseClient;

    public Mono<ListToExport> getAll(Mono<Users> user, Long idAgency, String localname, boolean auth) {
        return getAll(user, idAgency, localname, auth, null);
    }

    /**
     * @param lang lingua del menu pubblico (es. "en"): i nomi di gruppi e scelte delle varianti escono tradotti
     *             (traduzioni aggiornate di menu_translations, tipo VARIANT). null/"it" = italiano.
     */
    public Mono<ListToExport> getAll(Mono<Users> user, Long idAgency, String localname, boolean auth, String lang) {
        try {
            Mono<AgencyR2dbc> agencyMono = (idAgency == null || idAgency == -1)
                    ? webfluxAgencyRepository.findByNameAndDeleted(localname.strip(), false)
                    : webfluxAgencyRepository.findByIdAndDeleted(idAgency, false);

            return agencyMono
                    .switchIfEmpty(Mono.error(new RuntimeException("Agency not found")))
                    .flatMap(agency -> {
                        //boolean auth = user.isPresent() && user.get().getId_agency() == idAgency.longValue();
                        System.out.println(agency.getId());
                        Flux<ProductDto> productsFlux = translateVariants(agency.getId(), lang, getProducts(agency.getId(), auth)).cache();
                        Flux<CategoryDto> categoriesFlux = getCategories(agency.getId(), auth, productsFlux);
                        Flux<IngredientDto> ingredientsFlux = getIngredients(agency.getId(), auth);
                        Flux<TableDto> tablesFlux = auth ? getTables(agency.getId()) : Flux.empty();
                        Flux<ImageDto> imagesFlux = auth ? getImages(agency.getId()) : Flux.empty();
                        Mono<StyleDto> styleMono = getStyle(agency.getId());
                        Flux<ComandReactive> comandFlux = auth ? getComands(idAgency).onErrorResume(e -> Flux.empty()) : Flux.empty();

                        return Mono.zip(
                                categoriesFlux.collectList(),
                                ingredientsFlux.collectList(),
                                productsFlux.collectList(),
                                imagesFlux.collectList(),
                                tablesFlux.collectList(),
                                styleMono,
                                comandFlux.collectList()
                        ).map(tuple6 -> new ListToExport(
                                tuple6.getT1(), // List<CategoryDto>
                                tuple6.getT2(), // List<IngredientDto>
                                tuple6.getT3(), // List<ProductDto>
                                tuple6.getT4(), // List<ImageDto>
                                tuple6.getT5(), // List<TableDto>
                                tuple6.getT6(),  // StyleDto
                                tuple6.getT7()
                        ));

                    });
        } catch (Exception e) {
            System.out.println("Errore: " + e.getMessage());
            return null;
        }
    }


    private Flux<CategoryDto> getCategories(long idAgency, boolean auth, Flux<ProductDto> products) {
        Mono<Map<Long, List<LongInteger>>> productsByCategoryMono = products
                .groupBy(ProductDto::getIdCategory)
                .flatMap(groupedFlux -> groupedFlux
                        .map(productDto -> new LongInteger(productDto.getId(), productDto.getPosition_progressive()))
                        .collectList()
                        .map(ids -> Map.entry(groupedFlux.key(), ids))
                )
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);

        Flux<CategoryR2dbc> categories = auth
                ? webfluxCategoryRepository.findByIdAgencyAndDeleted(idAgency, false)
                : webfluxCategoryRepository.findByIdAgencyAndDeletedAndAvailable(idAgency, false, true);

        return productsByCategoryMono
                .defaultIfEmpty(new HashMap<>()) // Assicura che ci sia almeno una mappa vuota
                .flatMapMany(productsByCategory ->
                        categories.map(categoryR2dbc ->
                                new CategoryDto(productsByCategory.getOrDefault(categoryR2dbc.getId(), new ArrayList<>()), categoryR2dbc)));

    }

    private Flux<ImageDto> getImages(long idAgency) {
        return webfluxImageRepository.findByIdAgencyAndDeleted(idAgency, false).map(ImageDto::new);
    }

    private Flux<ComandReactive> getComands(long idAgency) {
//        return webfluxComandRepository.findAll();
        // AWAIT = ordini nuovi dal QR del tavolo: senza, non comparivano né in dashboard né nell'app staff
        return webfluxComandRepository.findByStatusInAndIdAgency(Arrays.asList("AWAIT", "PROGRESS", "PENDING", "CANCELLED", "AWAIT_APPROVAL"), idAgency);
    }

    private Flux<ProductDto> getProducts(long idAgency, boolean auth) {
        return auth ?
                webfluxProductRepository.findAllByIdAgencyAndDeleted(idAgency, false).map(p -> productMapper.toDto(p)) :
                webfluxProductRepository.findAllByIdAgencyAndDeletedAndAvailable(idAgency, false, true).map(p -> productMapper.toDto(p));
    }

    private Flux<IngredientDto> getIngredients(long idAgency, boolean auth) {
        return auth ?
                webfluxIngredientRepository.findByIdAgencyAndDeleted(idAgency, false).map(i -> ingredientMapper.toDto(i)) :
                webfluxIngredientRepository.findByIdAgencyAndDeletedAndAvailable(idAgency, false, true).map(i -> ingredientMapper.toDto(i));
    }

    private Flux<TableDto> getTables(long idAgency) {
        return webfluxTableRepository.findByIdAgencyAndDeleted(idAgency, false).map(TableDto::new);
    }

    private Mono<StyleDto> getStyle(long idAgency) {
        return webfluxStyleRepository.findByIdAgency(idAgency).map(StyleDto::new);
    }

    // query per cercare i completati o gli eliminati
    public Flux<ComandReactive> getComandsByStatus(boolean completed, LocalDateTime localDateTime, long idAgency) {
        LocalDateTime before = localDateTime.plusDays(1);
        return webfluxComandRepository.findAllByStatusAndIdAgencyAndCreatedAtBetween(completed ? ComandStatus.COMPLETED.toString() : ComandStatus.DELETED.toString(), idAgency, localDateTime, before);
    }

    public Mono<ComandReactive> getComandById(String comandId) {
        return webfluxComandRepository.findById(comandId);
    }

    // query per cercare le comande del tavolo
    public Flux<ComandReactive> getComandsByTableSessionId(long tableId, long idAgency) {
        return webfluxTableRepository
                .findByIdAndIdAgencyAndDeleted(tableId, idAgency, false)
                .switchIfEmpty(Mono.error(new Exception("Tavolo non trovato o eliminato")))
                .flatMap(table -> webfluxComandRepository.findAllByStatusInAndIdAgencyAndTableSessionId(
                        List.of(
                                ComandStatus.COMPLETED.toString(),
                                ComandStatus.PENDING.toString(),
                                ComandStatus.PROGRESS.toString()
                        ),
                        idAgency,
                        table.getSessionId()
                ));
    }


    // ── Traduzione dei nomi delle varianti (menu pubblico) ───────────────────

    private static boolean validLang(String lang) {
        return lang != null && lang.matches("[a-z]{2,3}") && !lang.equals("it");
    }

    private record VariantText(long productId, String field, String text, String sourceHash) {}

    /** Applica le traduzioni VARIANT (solo se la lingua è attiva per il locale e la traduzione è aggiornata). */
    Flux<ProductDto> translateVariants(long idAgency, String lang, Flux<ProductDto> products) {
        if (!validLang(lang)) return products;
        // Valori inline (idAgency numerico, lang validato [a-z]{2,3}): il bind di DatabaseClient non è supportato
        // dal driver r2dbc-postgresql in uso ("Cannot encode parameter of type InParameter")
        Mono<Boolean> enabled = databaseClient.sql("SELECT languages FROM menu_language_settings WHERE idagency = " + idAgency)
                .map((row, meta) -> Optional.ofNullable(row.get("languages", String.class)).orElse(""))
                .one()
                .map(langs -> Arrays.stream(langs.split(",")).map(String::trim).anyMatch(lang::equals))
                .defaultIfEmpty(false);
        Mono<Map<Long, Map<String, VariantText>>> texts = databaseClient.sql(
                        "SELECT entityid, field, text, sourcehash FROM menu_translations WHERE idagency = " + idAgency
                                + " AND lang = '" + lang + "' AND entitytype = 'VARIANT'")
                .map((row, meta) -> new VariantText(row.get("entityid", Long.class), row.get("field", String.class),
                        row.get("text", String.class), row.get("sourcehash", String.class)))
                .all()
                .collect(HashMap::new, (Map<Long, Map<String, VariantText>> m, VariantText t) ->
                        m.computeIfAbsent(t.productId(), k -> new HashMap<>()).put(t.field(), t));
        return enabled.flatMapMany(on -> !on ? products : texts.flatMapMany(map -> map.isEmpty() ? products
                        : products.map(p -> applyVariantTexts(p, map.get(p.getId())))))
                .onErrorResume(e -> {
                    System.out.println("Traduzione varianti non applicata (agency " + idAgency + ", " + lang + "): " + e);
                    return products;
                });
    }

    static ProductDto applyVariantTexts(ProductDto p, Map<String, VariantText> texts) {
        if (texts == null || texts.isEmpty() || p.getVariantGroups().isEmpty()) return p;
        for (VariantGroup g : p.getVariantGroups()) {
            g.setName(translated(texts.get(g.getId()), g.getName()));
            for (VariantChoice c : g.getChoices()) {
                c.setName(translated(texts.get(g.getId() + "/" + c.getId()), c.getName()));
            }
        }
        return p;
    }

    /** Solo traduzioni aggiornate: se il testo italiano è cambiato (impronta diversa) resta in italiano. */
    private static String translated(VariantText t, String source) {
        if (t == null || source == null || t.text() == null || t.text().isBlank()) return source;
        return sha256(source.strip()).equals(t.sourceHash()) ? t.text() : source;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

}
