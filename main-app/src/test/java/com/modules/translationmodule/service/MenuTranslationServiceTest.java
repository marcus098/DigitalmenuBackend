package com.modules.translationmodule.service;

import com.modules.authmodule.repository.AgencyRepository;
import com.modules.categorymodule.model.CategoryJpa;
import com.modules.categorymodule.repository.CategoryRepository;
import com.modules.ingredientmodule.repository.IngredientRepository;
import com.modules.productmodule.model.ProductJpa;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MenuTranslationServiceTest {

    private static final long AGENCY = 7L;

    private MenuTranslationRepository translations;
    private OllamaClient ollama;
    private MenuTranslationService service;
    private final List<MenuTranslationJpa> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        translations = mock(MenuTranslationRepository.class);
        MenuLanguageSettingsRepository settings = mock(MenuLanguageSettingsRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        IngredientRepository ingredients = mock(IngredientRepository.class);
        ollama = mock(OllamaClient.class);

        MenuTranslator translator = new MenuTranslator();
        ReflectionTestUtils.setField(translator, "ollama", ollama);
        service = new MenuTranslationService();
        ReflectionTestUtils.setField(service, "translationRepository", translations);
        ReflectionTestUtils.setField(service, "settingsRepository", settings);
        ReflectionTestUtils.setField(service, "categoryRepository", categories);
        ReflectionTestUtils.setField(service, "productRepository", products);
        ReflectionTestUtils.setField(service, "ingredientRepository", ingredients);
        ReflectionTestUtils.setField(service, "agencyRepository", mock(AgencyRepository.class));
        ReflectionTestUtils.setField(service, "translator", translator);
        ReflectionTestUtils.setField(service, "ollama", ollama);

        CategoryJpa pizze = mock(CategoryJpa.class);
        when(pizze.getId()).thenReturn(1L);
        when(pizze.getName()).thenReturn("Pizze");
        when(categories.findAllByIdAgencyAndDeletedOrderByPositionProgressive(AGENCY, false)).thenReturn(List.of(pizze));
        ProductJpa diavola = mock(ProductJpa.class);
        when(diavola.getId()).thenReturn(10L);
        when(diavola.getName()).thenReturn("Diavola");
        when(diavola.getDescription()).thenReturn("Pomodoro, mozzarella e salame piccante");
        when(products.findAllByDeletedAndIdAgency(false, AGENCY)).thenReturn(List.of(diavola));
        when(ingredients.findAllByIdAgencyAndDeletedOrderByName(AGENCY, false)).thenReturn(List.of());

        MenuLanguageSettingsJpa s = new MenuLanguageSettingsJpa(AGENCY);
        s.setLanguages("en");
        when(settings.findById(AGENCY)).thenReturn(Optional.of(s));

        when(ollama.isEnabled()).thenReturn(true);
        when(ollama.translateModel()).thenReturn("test");
        when(translations.findAllByIdAgencyAndLang(AGENCY, "en")).thenAnswer(i -> new ArrayList<>(stored));
        when(translations.saveAll(anyList())).thenAnswer(i -> {
            List<MenuTranslationJpa> list = i.getArgument(0);
            stored.removeAll(list);
            stored.addAll(list);
            return list;
        });
    }

    private MenuTranslationJpa stored(EntityType type, long id, Field field, String text, String sourceText, boolean manual) {
        MenuTranslationJpa t = new MenuTranslationJpa(AGENCY, new TranslationKey(type, id, field), "en");
        t.setText(text);
        t.setSourceHash(MenuTranslationService.hash(sourceText));
        t.setManual(manual);
        stored.add(t);
        return t;
    }

    @Test
    void translatesOnlyMissingAndChangedTextsAndKeepsManualOnes() {
        stored(EntityType.CATEGORY, 1, Field.NAME, "Pizzas", "Pizze", false);                // aggiornata
        stored(EntityType.PRODUCT, 10, Field.NAME, "Devil", "Diavolo", true);                 // manuale ma vecchia: non si tocca
        stored(EntityType.PRODUCT, 10, Field.DESCRIPTION, "old", "Pomodoro e mozzarella", false); // testo cambiato
        when(ollama.chat(anyString(), anyList(), eq(true), anyDouble()))
                .thenReturn("{\"items\":[{\"k\":\"0\",\"t\":\"Tomato, mozzarella and spicy salami\"}]}");

        service.run(AGENCY, List.of("en"));

        verify(ollama, times(1)).chat(anyString(), argThat(msgs -> msgs.get(1).content().contains("salame piccante")
                && !msgs.get(1).content().contains("\"Pizze\"")), eq(true), anyDouble());
        MenuTranslationJpa desc = stored.stream().filter(t -> t.getField().equals("DESCRIPTION")).findFirst().orElseThrow();
        assertEquals("Tomato, mozzarella and spicy salami", desc.getText());
        assertEquals(MenuTranslationService.hash("Pomodoro, mozzarella e salame piccante"), desc.getSourceHash());
        MenuTranslationJpa manual = stored.stream().filter(MenuTranslationJpa::isManual).findFirst().orElseThrow();
        assertEquals("Devil", manual.getText());
        MenuTranslationService.JobStatus job = service.overview(AGENCY).job();
        assertFalse(job.running());
        assertNull(job.error());
    }

    @Test
    void publicMenuServesOnlyUpToDateTranslations() {
        stored(EntityType.CATEGORY, 1, Field.NAME, "Pizzas", "Pizze", false);
        stored(EntityType.PRODUCT, 10, Field.NAME, "Devil", "Diavolo", false); // il nome è cambiato in "Diavola"
        AgencyRepository agencies = mock(AgencyRepository.class);
        com.modules.authmodule.model.AgencyJpa agency = mock(com.modules.authmodule.model.AgencyJpa.class);
        when(agency.getId()).thenReturn(AGENCY);
        when(agencies.findByNameAndDeleted("dapeppe", false)).thenReturn(Optional.of(agency));
        ReflectionTestUtils.setField(service, "agencyRepository", agencies);

        MenuTranslationService.PublicMenu menu = service.publicMenu("dapeppe", "en");

        assertEquals("Pizzas", menu.texts().get("CATEGORY").get(1L).get("NAME"));
        assertNull(menu.texts().get("PRODUCT"));
    }

    @Test
    void aiFailureIsReportedInJobStatus() {
        when(ollama.chat(anyString(), anyList(), anyBoolean(), anyDouble())).thenThrow(new AiUnavailableException("Servizio di traduzione non raggiungibile"));

        service.run(AGENCY, List.of("en"));

        assertEquals("Servizio di traduzione non raggiungibile", service.overview(AGENCY).job().error());
        assertTrue(stored.isEmpty());
    }

    @Test
    void parserDropsUnknownKeysEmptyAndRunawayTranslations() {
        MenuTranslator t = new MenuTranslator();
        List<MenuTranslator.Item> items = List.of(
                new MenuTranslator.Item("0", "dish", "Margherita"),
                new MenuTranslator.Item("1", "ingredient", "Basilico"),
                new MenuTranslator.Item("2", "category", "Bibite"));
        Map<String, String> out = t.parse("{\"items\":[{\"k\":\"0\",\"t\":\"\\\"Margherita\\\"\"},{\"k\":\"1\",\"t\":\"  \"},"
                + "{\"k\":\"2\",\"t\":\"" + "x".repeat(200) + "\"},{\"k\":\"9\",\"t\":\"Ghost\"}]}", items);

        assertEquals(Map.of("0", "Margherita"), out);
        assertThrows(AiUnavailableException.class, () -> t.parse("not json", items));
    }

    @Test
    void settingsRejectUnsupportedLanguage() {
        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(AGENCY, List.of("en", "klingon"), false));
    }
}
