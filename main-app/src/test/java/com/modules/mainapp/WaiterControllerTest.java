package com.modules.mainapp;

import com.modules.waitermodule.service.WaiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class WaiterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WaiterService waiterService;


}
