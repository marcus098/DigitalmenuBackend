package com.modules.stylemodule.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.common.dto.StyleDto;
import com.modules.common.finders.FileUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.stylemodule.models.StyleJpa;
import com.modules.stylemodule.repository.StyleRepository;
import com.modules.stylemodule.requests.UpdateStyle;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class StyleService {
    @Autowired
    private StyleRepository styleRepository;
    @Autowired
    private AuthenticatedUserProvider authUserProvider;
    @Autowired
    private FileUtils fileUtils;

    @Transactional
    public StyleDto updateStyle(UpdateStyle updateStyle, MultipartFile logoFile, MultipartFile heroFile) {
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();

        StyleJpa style = styleRepository.findByIdAgencyAndDeleted(idAgency, false).orElseThrow();
        String logoImage = style.getLogoUrl() == null ? "" : style.getLogoUrl();
        String heroImage = style.getHeroImageUrl() == null ? "" : style.getHeroImageUrl();

        if (logoFile != null) {
            logoImage = fileUtils.uploadImageWithBucket(logoFile, idAgency, idUser, "img");
        }
        if (heroFile != null) {
            heroImage = fileUtils.uploadImageWithBucket(heroFile, idAgency, idUser, "img");
        }

        if(logoFile == null && updateStyle.getLogoUrl().equals("DELETE")){
            logoImage = "";
        }
        if(heroFile == null && updateStyle.getHeroImageUrl().equals("DELETE")){
            heroImage = "";
        }

        style.setBackgroundGradient(updateStyle.getBackgroundGradient());
        style.setCardBackground(updateStyle.getCardBackground());
        style.setCardStyle(updateStyle.getCardStyle());
        style.setPrimaryColor(updateStyle.getPrimary());
        style.setTextBody(updateStyle.getTextBody());
        style.setTextOnPrimary(updateStyle.getTextOnPrimary());
        style.setTextTitle(updateStyle.getTextTitle());
        style.setAddress(updateStyle.getAddress());
        style.setPhone(updateStyle.getPhone());
        style.setFacebookUrl(updateStyle.getFacebookUrl());
        style.setInstagramUrl(updateStyle.getInstagramUrl());
        style.setHeroImageUrl(heroImage);
        style.setLogoUrl(logoImage);
        style.setRestaurantName(updateStyle.getRestaurantName());
        style.setShowImages(updateStyle.isShowImages());
        style.setFont(updateStyle.getFont());
        if (updateStyle.getDescription() != null) style.setDescription(updateStyle.getDescription());
        if (updateStyle.getOpeningHours() != null) style.setOpeningHours(updateStyle.getOpeningHours());
        if (updateStyle.getWhatsapp() != null) style.setWhatsapp(updateStyle.getWhatsapp());
        if (updateStyle.getTiktokUrl() != null) style.setTiktokUrl(updateStyle.getTiktokUrl());
        if (updateStyle.getLandingTemplate() != null) style.setLandingTemplate(updateStyle.getLandingTemplate());

        // Landing appearance. Convention: parameter absent (null) = leave unchanged,
        // blank string = reset to NULL (client falls back to the template default).
        if (updateStyle.getHeroBgColor() != null) style.setHeroBgColor(validHexOrNull("heroBgColor", updateStyle.getHeroBgColor()));
        if (updateStyle.getSecondaryColor() != null) style.setSecondaryColor(validHexOrNull("secondaryColor", updateStyle.getSecondaryColor()));
        if (updateStyle.getSecondaryTextColor() != null) style.setSecondaryTextColor(validHexOrNull("secondaryTextColor", updateStyle.getSecondaryTextColor()));
        if (updateStyle.getHeroOverlayOpacity() != null) {
            double opacity = updateStyle.getHeroOverlayOpacity();
            if (Double.isNaN(opacity) || opacity < 0 || opacity > 1) {
                throw badRequest("heroOverlayOpacity must be between 0 and 1");
            }
            style.setHeroOverlayOpacity(opacity);
        }
        if (updateStyle.getFeatures() != null) style.setFeatures(validFeaturesOrNull(updateStyle.getFeatures()));
        if (updateStyle.getSectionMenuTitle() != null) style.setSectionMenuTitle(validTitle("sectionMenuTitle", updateStyle.getSectionMenuTitle()));
        if (updateStyle.getSectionBookingTitle() != null) style.setSectionBookingTitle(validTitle("sectionBookingTitle", updateStyle.getSectionBookingTitle()));
        if (updateStyle.getSectionWhyTitle() != null) style.setSectionWhyTitle(validTitle("sectionWhyTitle", updateStyle.getSectionWhyTitle()));
        if (updateStyle.getShowWhyUs() != null) style.setShowWhyUs(updateStyle.getShowWhyUs());
        if (updateStyle.getShowBooking() != null) style.setShowBooking(updateStyle.getShowBooking());
        if (updateStyle.getShowTicker() != null) style.setShowTicker(updateStyle.getShowTicker());

        style.setUpdatedAt(OffsetDateTime.now());

        style = styleRepository.save(style);
        ErrorLog.logger.info("Style updated for agency {}", idAgency);

        return new StyleDto(style);
    }

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_FEATURES_LENGTH = 5000;
    private static final int MAX_FEATURES = 12;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static IllegalArgumentException badRequest(String message) {
        return new IllegalArgumentException(message); // mapped to 400 by GlobalExceptionHandler
    }

    private static String validHexOrNull(String field, String value) {
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (!HEX_COLOR.matcher(v).matches()) {
            throw badRequest(field + " must be a hex color like #RRGGBB");
        }
        return v;
    }

    private static String validTitle(String field, String value) {
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.length() > MAX_TITLE_LENGTH) {
            throw badRequest(field + " is too long (max " + MAX_TITLE_LENGTH + " characters)");
        }
        return v;
    }

    /** Features must be a JSON array of objects with string icon/title/sub; stored as compact JSON text. */
    private static String validFeaturesOrNull(String value) {
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.length() > MAX_FEATURES_LENGTH) {
            throw badRequest("features is too long");
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(v);
        } catch (Exception e) {
            throw badRequest("features must be a JSON array");
        }
        if (node == null || !node.isArray() || node.size() > MAX_FEATURES) {
            throw badRequest("features must be a JSON array of at most " + MAX_FEATURES + " items");
        }
        for (JsonNode item : node) {
            if (!item.isObject()) throw badRequest("each feature must be an object");
            for (String key : new String[]{"icon", "title", "sub"}) {
                JsonNode f = item.get(key);
                if (f != null && !f.isNull() && !f.isTextual()) {
                    throw badRequest("feature." + key + " must be a string");
                }
            }
        }
        return node.toString();
    }

}
