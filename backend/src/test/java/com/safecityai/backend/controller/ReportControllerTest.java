package com.safecityai.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safecityai.backend.dto.ReportCreateDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.service.ReportService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
public class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createReport_Returns201_WhenValid() throws Exception {
        ReportCreateDTO dto = new ReportCreateDTO();
        dto.setDescription("Very serious cell phone robbery on main avenue");
        dto.setIncidentType(IncidentType.ROBBERY);
        dto.setAddress("Evergreen Terrace 123");
        dto.setSource(ReportSource.CITIZEN_TEXT);
        dto.setLatitude(4.6097);
        dto.setLongitude(-74.0817);

        ReportResponseDTO response = ReportResponseDTO.builder()
                .id(1L)
                .description(dto.getDescription())
                .status(ReportStatus.PENDING)
                .build();

        Mockito.when(reportService.createReport(any(ReportCreateDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    void createReport_Returns400_WhenInvalidDescription() throws Exception {
        ReportCreateDTO dto = new ReportCreateDTO();
        dto.setDescription("Short"); // Invalid description (< 10 chars)
        dto.setIncidentType(IncidentType.ROBBERY);
        dto.setAddress("Avenue");
        dto.setSource(ReportSource.CITIZEN_TEXT);

        mockMvc.perform(post("/api/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReportById_Returns200() throws Exception {
        ReportResponseDTO response = ReportResponseDTO.builder()
                .id(1L)
                .description("Get by ID test")
                .status(ReportStatus.PENDING)
                .build();

        Mockito.when(reportService.getReportById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/reports/1"))
                .andExpect(status().isOk());
    }
}
