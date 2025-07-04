package com.modules.common.model.db;

import java.time.OffsetDateTime;

public abstract class Style {
    private long id;
    private String backgroundGradient;
    private String cardBackground;
    private String primaryColor;
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
    private long idAgency;
    private String font;
    private OffsetDateTime updatedAt;
    private boolean deleted;

    public Style(){

    }

    public Style(long id, String backgroundGradient, String cardBackground, String primaryColor, String textBody, String textOnPrimary, String textTitle, String address, String phone, String facebookUrl, String instagramUrl, String heroImageUrl, String logoUrl, String restaurantName, String cardStyle, boolean showImages, String font, long idAgency, OffsetDateTime updatedAt) {
        this.id = id;
        this.font = font;
        this.backgroundGradient = backgroundGradient;
        this.cardBackground = cardBackground;
        this.primaryColor = primaryColor;
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
        this.idAgency = idAgency;
        this.updatedAt = updatedAt;
        this.deleted = false;
    }

    public Style(String backgroundGradient, String cardBackground, String primaryColor, String textBody, String textOnPrimary, String textTitle, String address, String phone, String facebookUrl, String instagramUrl, String heroImageUrl, String logoUrl, String restaurantName, String cardStyle, boolean showImages, String font, long idAgency, OffsetDateTime updatedAt) {
        this.backgroundGradient = backgroundGradient;
        this.font = font;
        this.cardBackground = cardBackground;
        this.primaryColor = primaryColor;
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
        this.idAgency = idAgency;
        this.updatedAt = updatedAt;
        this.deleted = false;
    }

    public Style(String restaurantName, String logoUrl, long idAgency, OffsetDateTime updatedAt){
        this.restaurantName = restaurantName;
        this.backgroundGradient = "";
        this.cardBackground = "#FFFFFF";
        this.primaryColor = "#fb923c";
        this.textBody = "#6b7280";
        this.textOnPrimary = "#FFFFFF";
        this.textTitle = "#1f2937";
        this.address = "";
        this.phone = "";
        this.facebookUrl = "";
        this.instagramUrl = "";
        this.heroImageUrl = "";
        this.logoUrl = logoUrl;
        this.cardStyle = "soft";
        this.showImages = true;
        this.idAgency = idAgency;
        this.updatedAt = updatedAt;
        this.deleted = false;
    }
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getBackgroundGradient() {
        return backgroundGradient;
    }

    public void setBackgroundGradient(String backgroundGradient) {
        this.backgroundGradient = backgroundGradient;
    }

    public String getCardBackground() {
        return cardBackground;
    }

    public void setCardBackground(String cardBackground) {
        this.cardBackground = cardBackground;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
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

    public long getIdAgency() {
        return idAgency;
    }

    public void setIdAgency(long idAgency) {
        this.idAgency = idAgency;
    }

    public String getFont() {
        return font;
    }

    public void setFont(String font) {
        this.font = font;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public String toString() {
        return "Style{" +
                "id=" + id +
                ", backgroundGradient='" + backgroundGradient + '\'' +
                ", cardBackground='" + cardBackground + '\'' +
                ", primary='" + primaryColor + '\'' +
                ", textBody='" + textBody + '\'' +
                ", textOnPrimary='" + textOnPrimary + '\'' +
                ", textTitle='" + textTitle + '\'' +
                ", address='" + address + '\'' +
                ", phone='" + phone + '\'' +
                ", facebookUrl='" + facebookUrl + '\'' +
                ", instagramUrl='" + instagramUrl + '\'' +
                ", heroImageUrl='" + heroImageUrl + '\'' +
                ", logoUrl='" + logoUrl + '\'' +
                ", restaurantName='" + restaurantName + '\'' +
                ", cardStyle='" + cardStyle + '\'' +
                ", showImages=" + showImages +
                ", idAgency=" + idAgency +
                ", font='" + font + '\'' +
                ", updatedAt=" + updatedAt +
                ", deleted=" + deleted +
                '}';
    }
}
