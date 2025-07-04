package com.modules.stylemodule.service;

import com.modules.common.dto.StyleDto;
import com.modules.common.finders.FileUtils;
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
        style.setUpdatedAt(OffsetDateTime.now());

        style = styleRepository.save(style);
        // todo gestire log
        // todo gestire notifiche kafka

        return new StyleDto(style);
    }

}
