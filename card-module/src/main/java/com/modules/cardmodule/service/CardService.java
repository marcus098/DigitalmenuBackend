package com.modules.cardmodule.service;

import com.modules.cardmodule.models.CardClaim;
import com.modules.cardmodule.models.CardJpa;
import com.modules.cardmodule.repository.CardClaimRepository;
import com.modules.cardmodule.repository.CardRepository;
import com.modules.cardmodule.requests.AddCard;
import com.modules.common.dto.CardDto;
import com.modules.common.email.EmailService;
import com.modules.common.utilities.Utilities;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import jakarta.transaction.Transactional;
import com.modules.common.finders.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CardService {
    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private AuthenticatedUserProvider authUserProvider;
    @Autowired
    private Utilities utilities;
    @Autowired
    private FileUtils fileUtils;
    @Autowired
    private EmailService emailService;
    @Autowired
    private CardClaimRepository cardClaimRepository;

    @Transactional
    public CardDto claimPoints(long id, int quantity){
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();

        CardJpa cardJpa = cardRepository.findByIdAndDeletedAndIdAgency(id, false, idAgency).orElse(null);

        CardClaim cardClaim = new CardClaim(cardJpa.getId(), OffsetDateTime.now(), idAgency, idUser, quantity);
        if(quantity > cardJpa.getActualValue()){
            quantity = cardJpa.getActualValue();
        }
        cardClaimRepository.save(cardClaim);
        cardJpa.setActualValue(cardJpa.getActualValue() - quantity);
        cardJpa = cardRepository.save(cardJpa);
        return new CardDto(cardJpa);
    }

    @Transactional
    public CardDto addPoints(long id, int quantity) {
        long idAgency = authUserProvider.getAgencyId();
        CardJpa cardJpa = cardRepository.findByIdAndDeletedAndIdAgency(id, false, idAgency).orElseThrow();
        int value = cardJpa.getActualValue() + quantity;
        if(!cardJpa.isTypePoints() && value > cardJpa.getScope()){
            value = cardJpa.getScope();
        }
        cardJpa.setActualValue(value);
        cardJpa = cardRepository.save(cardJpa);
        return new CardDto(cardJpa);
    }

    @Transactional
    public CardDto resetPoints(long id){
        long idAgency = authUserProvider.getAgencyId();
        CardJpa cardJpa = cardRepository.findByIdAndDeletedAndIdAgency(id, false, idAgency).orElseThrow();
        cardJpa.setActualValue(0);
        cardJpa = cardRepository.save(cardJpa);
        return new CardDto(cardJpa);
    }

    @Transactional
    public CardDto getCardInfo(String code){
        return new CardDto(cardRepository.findByCodeAndDeleted(code, false).orElseThrow());
    }

    @Transactional
    public CardDto addCard(AddCard addCard) throws Exception{
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        String cardCode = "";
        boolean find = true;
        int count = 10;
        while (find) {
            cardCode = count > 0 ? UUID.randomUUID().toString().substring(0, 13) : UUID.randomUUID().toString().substring(0, 18);
            cardCode = cardCode.toUpperCase();
            find = cardRepository.existsByCode(cardCode);
            count--;
        }

        MultipartFile multipartFile = utilities.generateQRCodeAsMultipartFile(cardCode, 300, 300, "webp");

        if(multipartFile == null) {
            throw new Exception("Errore creazione qrcode per card. multipartfile e' null");
        }

        String url = fileUtils.uploadImageWithBucket(multipartFile, idAgency, idUser, "cards");

        CardJpa card = new CardJpa(cardCode, addCard.isTypePoints(), 0, addCard.getScope(), addCard.isTypePoints() ? addCard.getPriceForPoint() : 0, url, idAgency, OffsetDateTime.now());
        card = cardRepository.save(card);
        // todo gestire i log su mongo
        return new CardDto(card);
    }

    public boolean sendCardByEmail(String email, long idCard){
        long idAgency = authUserProvider.getAgencyId();
        CardJpa cardJpa = cardRepository.findByIdAndDeletedAndIdAgency(idCard, false, idAgency).orElseThrow();
        //emailService.sendEmail(email, "Carta fedelta'", );
        // todo gestire invio email e creare template
        // todo loggare l'invio della mail
        return true;
    }

    @Transactional
    public boolean deleteCard(long id){
        long idAgency = authUserProvider.getAgencyId();
        long idUser = authUserProvider.getUserId();
        CardJpa cardJpa = cardRepository.findByIdAndDeletedAndIdAgency(id, false, idAgency).orElseThrow();
        cardJpa.setDeleted(true);
        cardJpa.setDeletedAt(OffsetDateTime.now());
        cardRepository.save(cardJpa);
        //todo gestire i log
        return true;
    }

    @Transactional
    public List<CardDto> getAllCards(){
        long idAgency = authUserProvider.getAgencyId();
        return cardRepository
                .findAllByIdAgencyAndDeleted(idAgency, false)
                .stream()
                .map(CardDto::new).collect(Collectors.toList());
    }

}
