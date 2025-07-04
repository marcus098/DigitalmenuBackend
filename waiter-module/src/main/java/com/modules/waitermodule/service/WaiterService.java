package com.modules.waitermodule.service;

import com.modules.waitermodule.repository.WaiterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WaiterService {
    @Autowired
    private WaiterRepository waiterRepository;


}
