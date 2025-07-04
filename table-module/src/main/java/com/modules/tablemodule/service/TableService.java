package com.modules.tablemodule.service;

import com.modules.common.dto.TableDto;
import com.modules.common.finders.OrderUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.Comand;
import com.modules.common.model.EntityLog;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.LogOperation;
import com.modules.common.model.enums.TypeEntity;
import com.modules.common.utilities.Utilities;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.tablemodule.model.TableEntityJpa;
import com.modules.tablemodule.repository.mongo.MongoTableRepository;
import com.modules.tablemodule.repository.TableEntityRepository;
import com.modules.tablemodule.requests.AddTable;
import com.modules.tablemodule.requests.UpdateTableRow;
import com.modules.tablemodule.requests.UpdateTables;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TableService {
    @Autowired
    private TableEntityRepository tableEntityRepository;
    @Autowired
    private MongoTableRepository mongoTableRepository;
    @Autowired
    private AuthenticatedUserProvider authUserProvider;
    @Autowired
    private OrderUtils orderUtils;
    @Autowired
    private Utilities utilities;

    @Transactional
    public TableDto addTable(AddTable addTable) {
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();
            TableEntityJpa tableEntity = tableEntityRepository.save(new TableEntityJpa(
                    addTable.getName(),
                    idAgency,
                    addTable.getX(),
                    addTable.getY(),
                    addTable.getW(),
                    addTable.getH(),
                    addTable.getLocation()
            ));
            EntityLog<TableDto> tableLog = new EntityLog<>(LogOperation.ADD, null, new TableDto(tableEntity), "function addTable", idUser, idAgency);
            mongoTableRepository.save(tableLog);
            List<TypeEntity> entities = new ArrayList<>();
            entities.add(TypeEntity.TABLE);
            //utilities.updateType(entities);
            return new TableDto(tableEntity);
        } catch (Exception e) {
            ErrorLog.logger.error("Error adding table", e);
            return null;
        }
    }

    @Transactional
    public List<TableDto> updateTable(UpdateTables updateTables) {
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();
            List<TableEntityJpa> tables = new ArrayList<>();

            for (UpdateTableRow updateTableRow : updateTables.getTables()) {
                TableEntityJpa tableEntityJpa = new TableEntityJpa(
                        updateTableRow.getName(),
                        updateTableRow.getSeats(),
                        !updateTableRow.getStatus().equalsIgnoreCase("libero"),
                        updateTableRow.getCode(),
                        idAgency,
                        updateTableRow.getX(),
                        updateTableRow.getY(),
                        updateTableRow.getW(),
                        updateTableRow.getH(),
                        updateTableRow.getLocation()
                );
                if(!updateTableRow.getId().startsWith("temp")){
                    tableEntityJpa.setId(Long.valueOf(updateTableRow.getId()));
                }
            }

            tables = tableEntityRepository.saveAll(tables);

            //mongoTableRepository.save(new TableLog(LogOperation.UPDATE, oldTableDto, new TableDto(tableEntity), "function updateTable", idUser, idAgency));
            //List<TypeEntity> entities = new ArrayList<>();
            //entities.add(TypeEntity.TABLE);
            ////utilities.updateType(entities);
            //return new TableDto(tableEntity);
            return tables.stream().map(TableDto::new).collect(Collectors.toList());
        } catch (Exception e) {
            ErrorLog.logger.error("Errore updateTable", e);
            return null;
        }
    }

    @Transactional
    public int deleteTable(long idTable) {
        // devo cercare su mongo se ci sono degli ordini o delle comande associate a quel tavolo. In tal caso non posso eliminare il tavolo
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();
            // cerco su sql se esiste il tavolo
            Optional<TableEntityJpa> tableEntityJpa = tableEntityRepository.findByIdAndIdAgencyAndDeleted(idTable, idAgency, false);
            if (tableEntityJpa.isEmpty()) {
                return 404;
            }

            TableEntityJpa table = tableEntityJpa.get();
            //cerco su mongodb se ci sono comande associate al tavolo
            List<Comand> comands = orderUtils.findByTableSessionIdAndIdAgencyAndStatusIn(table.getSessionId(), idAgency, List.of(ComandStatus.PENDING.toString(), ComandStatus.PROGRESS.toString()));

            // se ci sono comande mando errore 402
            if (!comands.isEmpty()) {
                return 402;
            }

            // se la lista e' vuota posso eliminare
            table.setDeleted(true);
            table.setDeletedAt(OffsetDateTime.now());
            tableEntityRepository.save(table);

            // salvo i log se elimino
            EntityLog<TableDto> tableLog = new EntityLog<>(LogOperation.DELETE, null, new TableDto(table), "function deleteTable", idUser, idAgency);
            mongoTableRepository.save(tableLog);
            List<TypeEntity> entities = new ArrayList<>();
            entities.add(TypeEntity.TABLE);

            return 200;
        } catch (Exception e) {
            ErrorLog.logger.error("Error deleting table", e);
            return 400;
        }
    }

    @Transactional
    public int deleteTableAndCloseComands(long idTable) {
        // devo cercare su mongo se ci sono degli ordini o delle comande associate a quel tavolo. In tal caso devo chiuderle
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();
            // controllo che il tavolo esista su sql
            Optional<TableEntityJpa> tableEntityJpa = tableEntityRepository.findByIdAndIdAgencyAndDeleted(idTable, idAgency, false);
            if (tableEntityJpa.isEmpty()) {
                return 404;
            }

            TableEntityJpa table = tableEntityJpa.get();
            // cerco su mongodb le comande associate al tavolo
            List<Comand> comands = orderUtils.findByTableSessionIdAndIdAgencyAndStatusIn(table.getSessionId(), idAgency, List.of(ComandStatus.PENDING.toString(), ComandStatus.PROGRESS.toString()));
            List<EntityLog<?>> comandLogs = new ArrayList<>();

            // segno come completate tutte le comande e le salvo su mongo
            if (!comands.isEmpty()) {
                for (Comand comand : comands) {
                    ComandStatus oldStatus = comand.getStatus();
                    comand.setStatus(ComandStatus.COMPLETED);
                    EntityLog<?> newLog = new EntityLog<>(LogOperation.OTHER, oldStatus, ComandStatus.COMPLETED, "function deleteTableAndCloseComands for comand " + comand.getId(), idUser, idAgency);
                    comandLogs.add(newLog);
                }
                orderUtils.saveAll(comands); // todo errore se non riesco a salvare
                orderUtils.saveAllLogs(comandLogs);
            }

            // elimino il tavolo
            table.setDeleted(true);
            table.setDeletedAt(OffsetDateTime.now());
            tableEntityRepository.save(table);

            // salvo i log di chiusura comande ed eliminazione tavolo
            EntityLog<TableDto> tableLog = new EntityLog<>(LogOperation.DELETE, new TableDto(table), null, "function deleteTable", idUser, idAgency);
            mongoTableRepository.save(tableLog);
            List<TypeEntity> entities = new ArrayList<>();
            entities.add(TypeEntity.TABLE);
            if (!comandLogs.isEmpty())
                entities.add(TypeEntity.ORDER);

            return 200;
        } catch (Exception e) {
            ErrorLog.logger.error("Error checkAndDeleteTable", e);
            return 400;
        }
    }

    @Transactional
    public String setBusyAndCloseComands(long idTable, boolean setBusy, int seats) {
        // se devo liberare il tavolo e arrivo in questa chiamata significa che sto forzando tutti gli ordini a completati
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();
            // controllo che il tavolo esista su sql
            Optional<TableEntityJpa> tableEntityJpa = tableEntityRepository.findByIdAndIdAgencyAndDeleted(idTable, idAgency, false);
            if (tableEntityJpa.isEmpty()) {
                return null;
            }
            TableEntityJpa table = tableEntityJpa.get();
            // cerco su mongodb le comande attive associate al tavolo
            List<Comand> comands = orderUtils.findByTableSessionIdAndIdAgencyAndStatusIn(table.getSessionId(), idAgency, List.of(ComandStatus.PENDING.toString(), ComandStatus.PROGRESS.toString()));
            List<EntityLog<?>> comandLogs = new ArrayList<>();

            // segno come completate tutte le comande e le salvo su mongo
            if (!comands.isEmpty()) {
                for (Comand comand : comands) {
                    ComandStatus oldStatus = comand.getStatus();
                    comand.setStatus(ComandStatus.COMPLETED);
                    EntityLog<?> newLog = new EntityLog<>(LogOperation.OTHER, oldStatus, ComandStatus.COMPLETED, "function setBusyAndCloseComands for comand " + comand.getId(), idUser, idAgency);
                    comandLogs.add(newLog);
                }
                orderUtils.saveAll(comands); // todo gestione errore
                orderUtils.saveAllLogs(comandLogs);
            }

            // libero il tavolo e genero un nuovo codice
            table.setBusy(setBusy);
            String returnValue = "Success";
            if (setBusy) {
                table.setSeats(seats);
                table.setSessionId(UUID.randomUUID().toString().concat(Long.toString(System.currentTimeMillis())));
            }else{
                returnValue = utilities.generateCode();
                table.setCode(returnValue);
                table.setSessionId("");
            }
            table = tableEntityRepository.save(table);

            // salvo i log di chiusura comande e liberazione tavolo
            EntityLog<TableDto> tableLog = new EntityLog<>(LogOperation.DELETE, new TableDto(table),null, "function setBusyAndCloseComands for table " + idTable + ", seats: " + seats, idUser, idAgency);
            mongoTableRepository.save(tableLog);
            List<TypeEntity> entities = new ArrayList<>();
            entities.add(TypeEntity.TABLE);
            if (!comandLogs.isEmpty())
                entities.add(TypeEntity.ORDER);

            return returnValue;
        } catch (Exception e) {
            ErrorLog.logger.error("Error setBusy", e);
            return null;
        }
    }

    @Transactional
    public String setBusy(long idTable, boolean setBusy, int seats) {
        // se devo liberare un tavolo devo controllare su mongo se gli ordini sono tutti evasi. in tal caso libero, altrimenti mando codice errore ordini in corso
        try {
            long idUser = authUserProvider.getUserId();
            long idAgency = authUserProvider.getAgencyId();
            // cerco su sql se esiste il tavolo
            Optional<TableEntityJpa> tableEntityJpa = tableEntityRepository.findByIdAndIdAgencyAndDeleted(idTable, idAgency, false);
            if (tableEntityJpa.isEmpty()) {
                return "404";
            }

            TableEntityJpa table = tableEntityJpa.get();

            if(!setBusy) {
                //cerco su mongodb se ci sono comande associate al tavolo
                List<Comand> comands = orderUtils.findByTableSessionIdAndIdAgencyAndStatusIn(table.getSessionId(), idAgency, List.of(ComandStatus.PENDING.toString(), ComandStatus.PROGRESS.toString()));

                // se ci sono comande mando errore 402
                if (!comands.isEmpty()) {
                    return "comands";
                }
            }

            // se la lista e' vuota posso eliminare
            table.setBusy(setBusy);
            String returnValue = "Success";
            if (setBusy) {
                table.setSeats(seats);
                table.setSessionId(UUID.randomUUID().toString().concat(Long.toString(System.currentTimeMillis())));
            }else{
                returnValue = utilities.generateCode();
                table.setCode(returnValue);
                table.setSessionId("");
            }
            table = tableEntityRepository.save(table);

            // salvo i log se modifico busy todo rivedere meglio il log e mostrare id e valore busy
            EntityLog<TableDto> tableLog = new EntityLog<>(LogOperation.OTHER, null, new TableDto(table), "function setBusy " + setBusy, idUser, idAgency);
            mongoTableRepository.save(tableLog);
            List<TypeEntity> entities = new ArrayList<>();
            entities.add(TypeEntity.TABLE);

            return returnValue;
        } catch (Exception e) {
            ErrorLog.logger.error("Error checkAndSetBusy", e);
            return "400";
        }
    }

}
