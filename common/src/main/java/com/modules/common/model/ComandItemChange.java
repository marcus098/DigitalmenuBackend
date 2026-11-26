package com.modules.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.modules.common.model.enums.ComandItemChangeType;

/**
 * Una differenza tra le righe di una comanda prima e dopo una modifica (snapshot leggibile, non riferimenti).
 * <ul>
 *   <li>ADDED: solo newQty/newDescription/newNote;</li>
 *   <li>REMOVED: solo oldQty/oldDescription/oldNote;</li>
 *   <li>QTY_CHANGED: stessa riga, quantità diversa (description e note uguali nei due campi);</li>
 *   <li>VARIANT_CHANGED: stesso prodotto, formato/varianti/ingredienti diversi (quantità e note possono cambiare);</li>
 *   <li>NOTE_CHANGED: stesso prodotto e stesse scelte, cambia la nota (la quantità può cambiare).</li>
 * </ul>
 * description = formato + varianti + "senza ..." + "+ ..." (senza la nota, che è in oldNote/newNote).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComandItemChange {
    private ComandItemChangeType type;
    private long idProduct;
    private String productName;
    private long idCategory;
    private String categoryName;
    private Integer oldQty;
    private Integer newQty;
    private String oldDescription;
    private String newDescription;
    private String oldNote;
    private String newNote;

    public ComandItemChange() {}

    public ComandItemChange(ComandItemChangeType type, long idProduct, String productName, long idCategory, String categoryName) {
        this.type = type;
        this.idProduct = idProduct;
        this.productName = productName;
        this.idCategory = idCategory;
        this.categoryName = categoryName;
    }

    public ComandItemChangeType getType() { return type; }
    public void setType(ComandItemChangeType type) { this.type = type; }
    public long getIdProduct() { return idProduct; }
    public void setIdProduct(long idProduct) { this.idProduct = idProduct; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public long getIdCategory() { return idCategory; }
    public void setIdCategory(long idCategory) { this.idCategory = idCategory; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public Integer getOldQty() { return oldQty; }
    public void setOldQty(Integer oldQty) { this.oldQty = oldQty; }
    public Integer getNewQty() { return newQty; }
    public void setNewQty(Integer newQty) { this.newQty = newQty; }
    public String getOldDescription() { return oldDescription; }
    public void setOldDescription(String oldDescription) { this.oldDescription = oldDescription; }
    public String getNewDescription() { return newDescription; }
    public void setNewDescription(String newDescription) { this.newDescription = newDescription; }
    public String getOldNote() { return oldNote; }
    public void setOldNote(String oldNote) { this.oldNote = oldNote; }
    public String getNewNote() { return newNote; }
    public void setNewNote(String newNote) { this.newNote = newNote; }

    @Override
    public String toString() {
        return "ComandItemChange{" + type + " " + productName + " " + oldQty + "->" + newQty
                + ", [" + oldDescription + "]->[" + newDescription + "], note [" + oldNote + "]->[" + newNote + "]}";
    }
}
