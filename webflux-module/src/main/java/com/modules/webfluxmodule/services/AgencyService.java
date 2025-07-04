package com.modules.webfluxmodule.services;

import com.modules.webfluxmodule.models.db.AgencyR2dbc;
import com.modules.webfluxmodule.repositories.WebfluxAgencyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AgencyService {

    @Autowired
    private WebfluxAgencyRepository agencyRepository;

    public Mono<Boolean> existsAgencyOrName(Long idAgency, String localname) {
        return agencyRepository.existsByIdOrNameAndDeleted(idAgency, localname, false);
    }

    public Mono<AgencyR2dbc> findByIdAgencyOrNameAndDeleted(Long idAgency, String localname) {
        return idAgency == null ? agencyRepository.findByNameAndDeleted(localname, false) : agencyRepository.findByIdAndDeleted(idAgency, false);
    }
}
