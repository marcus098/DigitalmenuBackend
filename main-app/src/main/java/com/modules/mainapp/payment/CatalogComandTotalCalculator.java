package com.modules.mainapp.payment;

import com.modules.common.dto.IngredientDto;
import com.modules.common.dto.ProductDto;
import com.modules.common.finders.IngredientUtils;
import com.modules.common.finders.ProductUtils;
import com.modules.common.model.Comand;
import com.modules.common.model.IngredientOrderPlus;
import com.modules.common.model.OptionInProduct;
import com.modules.common.model.Order;
import com.modules.common.model.ProductToOrder;
import com.modules.ordermodule.repository.MongoComandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementazione TEMPORANEA di {@link ComandTotalCalculator}: ricalcola il totale dai prezzi di
 * catalogo (opzione del prodotto per nome + ingredienti extra per id), con fallback sul prezzo
 * memorizzato nella comanda (anch'esso calcolato lato server da OrderComandService.orderList)
 * se prodotto/opzione/ingrediente non esistono più.
 *
 * TODO(lead): dopo il merge sostituire con delega a OrderComandService.computeTotalCents(comandId)
 * (somma di unitPriceCents * quantity) ed eliminare questa classe.
 */
@Component
public class CatalogComandTotalCalculator implements ComandTotalCalculator {

    @Autowired
    private MongoComandRepository mongoComandRepository;
    @Autowired
    private ProductUtils productUtils;
    @Autowired
    private IngredientUtils ingredientUtils;

    @Override
    public long computeTotalCents(String comandId) {
        Comand comand = mongoComandRepository.findById(comandId)
                .orElseThrow(() -> new NoSuchElementException("Comanda non trovata"));
        long idAgency = comand.getIdAgency();
        List<ProductToOrder> items = comand.getOrders() == null ? List.of() : comand.getOrders().stream()
                .map(Order::getProducts)
                .filter(p -> p != null)
                .flatMap(List::stream)
                .toList();

        Set<Long> productIds = new HashSet<>();
        Set<Long> ingredientIds = new HashSet<>();
        for (ProductToOrder p : items) {
            productIds.add(p.getIdProduct());
            if (p.getIngredientsPlus() != null) {
                p.getIngredientsPlus().forEach(i -> ingredientIds.add(i.getId()));
            }
        }

        Map<Long, ProductDto> products = productIds.isEmpty() ? Map.of() :
                productUtils.findAllByIdInAndIdAgencyAndDeleted(productIds, idAgency).stream()
                        .collect(Collectors.toMap(ProductDto::getId, Function.identity(), (a, b) -> a));
        Map<Long, IngredientDto> ingredients = ingredientIds.isEmpty() ? Map.of() :
                ingredientUtils.findAllByIdInAndIdAgencyAndDeleted(ingredientIds, idAgency).stream()
                        .collect(Collectors.toMap(IngredientDto::getId, Function.identity(), (a, b) -> a));

        long total = 0;
        for (ProductToOrder p : items) {
            if (p.getQuantity() <= 0) continue;
            long unit = optionCents(p, products.get(p.getIdProduct()));
            if (p.getIngredientsPlus() != null) {
                for (IngredientOrderPlus extra : p.getIngredientsPlus()) {
                    IngredientDto catalog = ingredients.get(extra.getId());
                    double price = catalog != null && catalog.getPrice() != null ? catalog.getPrice() : extra.getPrice();
                    unit += toCents(price);
                }
            }
            total = Math.addExact(total, Math.multiplyExact(unit, (long) p.getQuantity()));
        }
        return total;
    }

    private long optionCents(ProductToOrder item, ProductDto catalogProduct) {
        OptionInProduct ordered = item.getProductOption();
        if (catalogProduct != null && catalogProduct.getOptions() != null && ordered != null && ordered.getName() != null) {
            for (OptionInProduct o : catalogProduct.getOptions()) {
                if (o != null && ordered.getName().equals(o.getName())) {
                    return toCents(o.getPrice());
                }
            }
        }
        return ordered != null ? toCents(ordered.getPrice()) : 0L;
    }

    private static long toCents(double euros) {
        return euros > 0 ? Math.round(euros * 100d) : 0L;
    }
}
