package com.modules.waitermodule.repository;

import com.modules.waitermodule.model.WaiterJpa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaiterRepository extends JpaRepository<WaiterJpa, Long> {
}
