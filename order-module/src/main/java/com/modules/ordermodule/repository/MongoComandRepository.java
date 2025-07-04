package com.modules.ordermodule.repository;

import com.modules.common.model.Comand;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MongoComandRepository extends MongoRepository<Comand, String> {
    //@Query("{ 'idTable' : ?0 }")
    //List<Comand> findByIdTable(long idTable);

    List<Comand> findByTableSessionIdAndIdAgencyAndStatusIn(String sessionId, long idAgency, List<String> statuses);

}
