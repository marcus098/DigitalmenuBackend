package com.modules.common.dto;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.common.model.db.Style;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StyleDto {
    private List<String> backgroundGradient;
    private String cardBackground;
    private String primary;
    private String textBody;
    private String textOnPrimary;
    private String textTitle;
    private String address;
    private String phone;
    private String facebookUrl;
    private String instagramUrl;
    private String heroImageUrl;
    private String logoUrl;
    private String restaurantName;
    private String cardStyle;
    private boolean showImages;
    private String font;
    private String description;
    private String openingHours;
    private String whatsapp;
    private String tiktokUrl;
    private String landingTemplate;
    // features: stored as JSON text on the entity, exposed to the client as a parsed array.
    private String heroBgColor;
    private Double heroOverlayOpacity;
    private String secondaryColor;
    private String secondaryTextColor;
    private JsonNode features;
    private String sectionMenuTitle;
    private String sectionBookingTitle;
    private String sectionWhyTitle;
    private Boolean showWhyUs;
    private Boolean showBooking;
    private Boolean showTicker;
    @JsonView(Views.Updating.class)
    private String sessionUpdating;
    @JsonView(Views.Updating.class)
    private String changeType;

    public StyleDto() {

    }

    public StyleDto(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public StyleDto(Style style) {
        System.out.println(style);
        this.backgroundGradient = style.getBackgroundGradient().isEmpty() ? new ArrayList<>() : Arrays.stream(style.getBackgroundGradient().split(";")).toList();
        this.cardBackground = style.getCardBackground();
        this.primary = style.getPrimaryColor();
        this.textBody = style.getTextBody();
        this.textOnPrimary = style.getTextOnPrimary();
        this.textTitle = style.getTextTitle();
        this.address = style.getAddress();
        this.phone = style.getPhone();
        this.facebookUrl = style.getFacebookUrl();
        this.instagramUrl = style.getInstagramUrl();
        this.heroImageUrl = style.getHeroImageUrl();
        this.logoUrl = style.getLogoUrl();
        this.restaurantName = style.getRestaurantName();
        this.cardStyle = style.getCardStyle();
        this.showImages = style.isShowImages();
        this.font = style.getFont();
        this.description = style.getDescription();
        this.openingHours = style.getOpeningHours();
        this.whatsapp = style.getWhatsapp();
        this.tiktokUrl = style.getTiktokUrl();
        this.landingTemplate = style.getLandingTemplate();
        this.heroBgColor = style.getHeroBgColor();
        this.heroOverlayOpacity = style.getHeroOverlayOpacity();
        this.secondaryColor = style.getSecondaryColor();
        this.secondaryTextColor = style.getSecondaryTextColor();
        this.features = parseFeatures(style.getFeatures());
        this.sectionMenuTitle = style.getSectionMenuTitle();
        this.sectionBookingTitle = style.getSectionBookingTitle();
        this.sectionWhyTitle = style.getSectionWhyTitle();
        this.showWhyUs = style.getShowWhyUs();
        this.showBooking = style.getShowBooking();
        this.showTicker = style.getShowTicker();
    }

    private static final ObjectMapper FEATURES_MAPPER = new ObjectMapper();

    /** Parses the stored features JSON; null (client uses its defaults) if missing or not an array. */
    private static JsonNode parseFeatures(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            JsonNode node = FEATURES_MAPPER.readTree(raw);
            return node != null && node.isArray() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    public StyleDto(String backgroundGradient, String cardBackground, String primary, String textBody, String textOnPrimary, String textTitle, String address, String phone, String facebookUrl, String instagramUrl, String heroImageUrl, String logoUrl, String restaurantName, String cardStyle, boolean showImages, String font, String sessionUpdating, String changeType) {
        this.backgroundGradient = backgroundGradient.isEmpty() ? new ArrayList<>() : Arrays.stream(backgroundGradient.split(";")).toList();
        this.cardBackground = cardBackground;
        this.primary = primary;
        this.textBody = textBody;
        this.textOnPrimary = textOnPrimary;
        this.textTitle = textTitle;
        this.address = address;
        this.phone = phone;
        this.facebookUrl = facebookUrl;
        this.instagramUrl = instagramUrl;
        this.heroImageUrl = heroImageUrl;
        this.logoUrl = logoUrl;
        this.restaurantName = restaurantName;
        this.cardStyle = cardStyle;
        this.showImages = showImages;
        this.font = font;
        this.sessionUpdating = sessionUpdating;
        this.changeType = changeType;
    }

    public StyleDto(String backgroundGradient, String cardBackground, String primary, String textBody, String textOnPrimary, String textTitle, String address, String phone, String facebookUrl, String instagramUrl, String heroImageUrl, String logoUrl, String restaurantName, String cardStyle, boolean showImages, String font) {
        this.backgroundGradient = backgroundGradient.isEmpty() ? new ArrayList<>() : Arrays.stream(backgroundGradient.split(";")).toList();
        this.cardBackground = cardBackground;
        this.primary = primary;
        this.textBody = textBody;
        this.textOnPrimary = textOnPrimary;
        this.textTitle = textTitle;
        this.address = address;
        this.phone = phone;
        this.facebookUrl = facebookUrl;
        this.instagramUrl = instagramUrl;
        this.heroImageUrl = heroImageUrl;
        this.logoUrl = logoUrl;
        this.restaurantName = restaurantName;
        this.cardStyle = cardStyle;
        this.showImages = showImages;
        this.font = font;
    }

    public List<String> getBackgroundGradient() {
        return backgroundGradient;
    }

    public void setBackgroundGradient(List<String> backgroundGradient) {
        this.backgroundGradient = backgroundGradient;
    }

    public void setBackgroundGradient(String backgroundGradient) {
        this.backgroundGradient = Arrays.stream(backgroundGradient.split(";")).toList();
    }

    public String getCardBackground() {
        return cardBackground;
    }

    public void setCardBackground(String cardBackground) {
        this.cardBackground = cardBackground;
    }

    public String getPrimary() {
        return primary;
    }

    public void setPrimary(String primary) {
        this.primary = primary;
    }

    public String getTextBody() {
        return textBody;
    }

    public void setTextBody(String textBody) {
        this.textBody = textBody;
    }

    public String getTextOnPrimary() {
        return textOnPrimary;
    }

    public void setTextOnPrimary(String textOnPrimary) {
        this.textOnPrimary = textOnPrimary;
    }

    public String getTextTitle() {
        return textTitle;
    }

    public void setTextTitle(String textTitle) {
        this.textTitle = textTitle;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getFacebookUrl() {
        return facebookUrl;
    }

    public void setFacebookUrl(String facebookUrl) {
        this.facebookUrl = facebookUrl;
    }

    public String getInstagramUrl() {
        return instagramUrl;
    }

    public void setInstagramUrl(String instagramUrl) {
        this.instagramUrl = instagramUrl;
    }

    public String getHeroImageUrl() {
        return heroImageUrl;
    }

    public void setHeroImageUrl(String heroImageUrl) {
        this.heroImageUrl = heroImageUrl;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public void setRestaurantName(String restaurantName) {
        this.restaurantName = restaurantName;
    }

    public String getCardStyle() {
        return cardStyle;
    }

    public void setCardStyle(String cardStyle) {
        this.cardStyle = cardStyle;
    }

    public boolean isShowImages() {
        return showImages;
    }

    public void setShowImages(boolean showImages) {
        this.showImages = showImages;
    }

    public String getFont() {
        return font;
    }

    public void setFont(String font) {
        this.font = font;
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }

    public String getWhatsapp() { return whatsapp; }
    public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }

    public String getTiktokUrl() { return tiktokUrl; }
    public void setTiktokUrl(String tiktokUrl) { this.tiktokUrl = tiktokUrl; }

    public String getLandingTemplate() { return landingTemplate; }
    public void setLandingTemplate(String landingTemplate) { this.landingTemplate = landingTemplate; }

    public String getHeroBgColor() { return heroBgColor; }
    public void setHeroBgColor(String heroBgColor) { this.heroBgColor = heroBgColor; }

    public Double getHeroOverlayOpacity() { return heroOverlayOpacity; }
    public void setHeroOverlayOpacity(Double heroOverlayOpacity) { this.heroOverlayOpacity = heroOverlayOpacity; }

    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }

    public String getSecondaryTextColor() { return secondaryTextColor; }
    public void setSecondaryTextColor(String secondaryTextColor) { this.secondaryTextColor = secondaryTextColor; }

    public JsonNode getFeatures() { return features; }
    public void setFeatures(JsonNode features) { this.features = features; }

    public String getSectionMenuTitle() { return sectionMenuTitle; }
    public void setSectionMenuTitle(String sectionMenuTitle) { this.sectionMenuTitle = sectionMenuTitle; }

    public String getSectionBookingTitle() { return sectionBookingTitle; }
    public void setSectionBookingTitle(String sectionBookingTitle) { this.sectionBookingTitle = sectionBookingTitle; }

    public String getSectionWhyTitle() { return sectionWhyTitle; }
    public void setSectionWhyTitle(String sectionWhyTitle) { this.sectionWhyTitle = sectionWhyTitle; }

    public Boolean getShowWhyUs() { return showWhyUs; }
    public void setShowWhyUs(Boolean showWhyUs) { this.showWhyUs = showWhyUs; }

    public Boolean getShowBooking() { return showBooking; }
    public void setShowBooking(Boolean showBooking) { this.showBooking = showBooking; }

    public Boolean getShowTicker() { return showTicker; }
    public void setShowTicker(Boolean showTicker) { this.showTicker = showTicker; }

    public String getSessionUpdating() {
        return sessionUpdating;
    }

    public void setSessionUpdating(String sessionUpdating) {
        this.sessionUpdating = sessionUpdating;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }
}
