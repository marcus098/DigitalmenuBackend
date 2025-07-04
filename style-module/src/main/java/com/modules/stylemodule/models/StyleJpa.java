package com.modules.stylemodule.models;

import com.modules.common.model.db.Style;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "styles")
public class StyleJpa extends Style {


    public StyleJpa(){
        super();
    }

    public StyleJpa(long id, String backgroundGradient, String cardBackground, String primaryColor, String textBody, String textOnPrimary, String textTitle, String address, String phone, String facebookUrl, String instagramUrl, String heroImageUrl, String logoUrl, String restaurantName, String cardStyle, boolean showImages, String font, long idAgency, OffsetDateTime updatedAt) {
        super(id, backgroundGradient, cardBackground, primaryColor, textBody, textOnPrimary, textTitle, address, phone, facebookUrl, instagramUrl, heroImageUrl, logoUrl, restaurantName, cardStyle, showImages, font, idAgency, updatedAt);
    }

    public StyleJpa(String backgroundGradient, String cardBackground, String primaryColor, String textBody, String textOnPrimary, String textTitle, String address, String phone, String facebookUrl, String instagramUrl, String heroImageUrl, String logoUrl, String restaurantName, String cardStyle, boolean showImages, String font, long idAgency, OffsetDateTime updatedAt) {
        super(backgroundGradient, cardBackground, primaryColor, textBody, textOnPrimary, textTitle, address, phone, facebookUrl, instagramUrl, heroImageUrl, logoUrl, restaurantName, cardStyle, showImages, font, idAgency, updatedAt);
    }

    public StyleJpa(String restaurantName, String logoUrl, long idAgency, OffsetDateTime updatedAt){
        super(restaurantName, logoUrl, idAgency, updatedAt);
    }

    @Override
    @Column(name="id_agency")
    public long getIdAgency() {
        return super.getIdAgency();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Override
    public long getId() {
        return super.getId();
    }

    @Override
    public String getPhone() {
        return super.getPhone();
    }

    @Override
    @Column(name="updated_at")
    public OffsetDateTime getUpdatedAt() {
        return super.getUpdatedAt();
    }

    @Override
    public String getFont() {
        return super.getFont();
    }

    @Override
    public String getAddress() {
        return super.getAddress();
    }

    @Override
    @Column(name="background_gradient")
    public String getBackgroundGradient() {
        return super.getBackgroundGradient();
    }

    @Override
    @Column(name="card_background")
    public String getCardBackground() {
        return super.getCardBackground();
    }

    @Override
    @Column(name="card_style")
    public String getCardStyle() {
        return super.getCardStyle();
    }

    @Override
    @Column(name="facebook_url")
    public String getFacebookUrl() {
        return super.getFacebookUrl();
    }

    @Override
    @Column(name="hero_image_url")
    public String getHeroImageUrl() {
        return super.getHeroImageUrl();
    }

    @Override
    @Column(name="instagram_url")
    public String getInstagramUrl() {
        return super.getInstagramUrl();
    }

    @Override
    @Column(name="logo_url")
    public String getLogoUrl() {
        return super.getLogoUrl();
    }

    @Override
    @Column(name="restaurant_name")
    public String getRestaurantName() {
        return super.getRestaurantName();
    }

    @Override
    @Column(name="primary_color")
    public String getPrimaryColor() {
        return super.getPrimaryColor();
    }

    @Override
    @Column(name="text_body")
    public String getTextBody() {
        return super.getTextBody();
    }

    @Override
    @Column(name="text_on_primary")
    public String getTextOnPrimary() {
        return super.getTextOnPrimary();
    }

    @Override
    @Column(name="text_title")
    public String getTextTitle() {
        return super.getTextTitle();
    }

    @Override
    @Column(name="show_images")
    public boolean isShowImages() {
        return super.isShowImages();
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    @Override
    public void setIdAgency(long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public void setDeleted(boolean deleted) {
        super.setDeleted(deleted);
    }

    @Override
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        super.setUpdatedAt(updatedAt);
    }

    @Override
    public void setAddress(String address) {
        super.setAddress(address);
    }

    @Override
    public void setBackgroundGradient(String backgroundGradient) {
        super.setBackgroundGradient(backgroundGradient);
    }

    @Override
    public void setCardBackground(String cardBackground) {
        super.setCardBackground(cardBackground);
    }

    @Override
    public void setCardStyle(String cardStyle) {
        super.setCardStyle(cardStyle);
    }

    @Override
    public void setFacebookUrl(String facebookUrl) {
        super.setFacebookUrl(facebookUrl);
    }

    @Override
    public void setFont(String font) {
        super.setFont(font);
    }

    @Override
    public void setHeroImageUrl(String heroImageUrl) {
        super.setHeroImageUrl(heroImageUrl);
    }

    @Override
    public void setInstagramUrl(String instagramUrl) {
        super.setInstagramUrl(instagramUrl);
    }

    @Override
    public void setLogoUrl(String logoUrl) {
        super.setLogoUrl(logoUrl);
    }

    @Override
    public void setPhone(String phone) {
        super.setPhone(phone);
    }

    @Override
    public void setPrimaryColor(String primary) {
        super.setPrimaryColor(primary);
    }

    @Override
    public void setRestaurantName(String restaurantName) {
        super.setRestaurantName(restaurantName);
    }

    @Override
    public void setShowImages(boolean showImages) {
        super.setShowImages(showImages);
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
    public String toString() {
        return super.toString();
    }
}
