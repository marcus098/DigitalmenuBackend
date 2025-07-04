package com.modules.webfluxmodule.models.db;

import com.modules.common.model.db.Style;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("styles")
public class StyleR2dbc extends Style {

    @Id
    @Override
    public long getId() {
        return super.getId();
    }

    @Override
    public String getAddress() {
        return super.getAddress();
    }

    @Override
    public String getTextTitle() {
        return super.getTextTitle();
    }

    @Override
    public String getTextOnPrimary() {
        return super.getTextOnPrimary();
    }

    public StyleR2dbc() {
        super();
    }

    public StyleR2dbc(long id, String backgroundGradient, String cardBackground, String primaryColor, String textBody, String textOnPrimary, String textTitle, String address, String phone, String facebookUrl, String instagramUrl, String heroImageUrl, String logoUrl, String restaurantName, String cardStyle, boolean showImages, String font, long idAgency, OffsetDateTime updatedAt) {
        super(id, backgroundGradient, cardBackground, primaryColor, textBody, textOnPrimary, textTitle, address, phone, facebookUrl, instagramUrl, heroImageUrl, logoUrl, restaurantName, cardStyle, showImages, font, idAgency, updatedAt);
    }

    public StyleR2dbc(String backgroundGradient, String cardBackground, String primaryColor, String textBody, String textOnPrimary, String textTitle, String address, String phone, String facebookUrl, String instagramUrl, String heroImageUrl, String logoUrl, String restaurantName, String cardStyle, boolean showImages, String font, long idAgency, OffsetDateTime updatedAt) {
        super(backgroundGradient, cardBackground, primaryColor, textBody, textOnPrimary, textTitle, address, phone, facebookUrl, instagramUrl, heroImageUrl, logoUrl, restaurantName, cardStyle, showImages, font, idAgency, updatedAt);
    }

    public StyleR2dbc(String restaurantName, String logoUrl, long idAgency, OffsetDateTime updatedAt) {
        super(restaurantName, logoUrl, idAgency, updatedAt);
    }

    @Override
    public void setId(long id) {
        super.setId(id);
    }

    @Override
    public String getBackgroundGradient() {
        return super.getBackgroundGradient();
    }

    @Override
    public void setBackgroundGradient(String backgroundGradient) {
        super.setBackgroundGradient(backgroundGradient);
    }

    @Override
    public String getCardBackground() {
        return super.getCardBackground();
    }

    @Override
    public void setCardBackground(String cardBackground) {
        super.setCardBackground(cardBackground);
    }

    @Override
    public String getPrimaryColor() {
        return super.getPrimaryColor();
    }

    @Override
    public void setPrimaryColor(String primaryColor) {
        super.setPrimaryColor(primaryColor);
    }

    @Override
    public String getTextBody() {
        return super.getTextBody();
    }

    @Override
    public void setTextBody(String textBody) {
        super.setTextBody(textBody);
    }

    @Override
    public void setTextOnPrimary(String textOnPrimary) {
        super.setTextOnPrimary(textOnPrimary);
    }

    @Override
    public void setTextTitle(String textTitle) {
        super.setTextTitle(textTitle);
    }

    @Override
    public void setAddress(String address) {
        super.setAddress(address);
    }

    @Override
    public String getPhone() {
        return super.getPhone();
    }

    @Override
    public void setPhone(String phone) {
        super.setPhone(phone);
    }

    @Override
    public String getFacebookUrl() {
        return super.getFacebookUrl();
    }

    @Override
    public void setFacebookUrl(String facebookUrl) {
        super.setFacebookUrl(facebookUrl);
    }

    @Override
    public String getInstagramUrl() {
        return super.getInstagramUrl();
    }

    @Override
    public void setInstagramUrl(String instagramUrl) {
        super.setInstagramUrl(instagramUrl);
    }

    @Override
    public String getHeroImageUrl() {
        return super.getHeroImageUrl();
    }

    @Override
    public void setHeroImageUrl(String heroImageUrl) {
        super.setHeroImageUrl(heroImageUrl);
    }

    @Override
    public String getLogoUrl() {
        return super.getLogoUrl();
    }

    @Override
    public void setLogoUrl(String logoUrl) {
        super.setLogoUrl(logoUrl);
    }

    @Override
    public String getRestaurantName() {
        return super.getRestaurantName();
    }

    @Override
    public void setRestaurantName(String restaurantName) {
        super.setRestaurantName(restaurantName);
    }

    @Override
    public String getCardStyle() {
        return super.getCardStyle();
    }

    @Override
    public void setCardStyle(String cardStyle) {
        super.setCardStyle(cardStyle);
    }

    @Override
    public boolean isShowImages() {
        return super.isShowImages();
    }

    @Override
    public void setShowImages(boolean showImages) {
        super.setShowImages(showImages);
    }

    @Override
    public long getIdAgency() {
        return super.getIdAgency();
    }

    @Override
    public void setIdAgency(long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public String getFont() {
        return super.getFont();
    }

    @Override
    public void setFont(String font) {
        super.setFont(font);
    }

    @Override
    public OffsetDateTime getUpdatedAt() {
        return super.getUpdatedAt();
    }

    @Override
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        super.setUpdatedAt(updatedAt);
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    @Override
    public void setDeleted(boolean deleted) {
        super.setDeleted(deleted);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
