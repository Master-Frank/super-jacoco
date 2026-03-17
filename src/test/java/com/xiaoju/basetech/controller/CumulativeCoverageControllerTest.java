package com.xiaoju.basetech.controller;

import com.xiaoju.basetech.entity.CoverageSourceResponse;
import com.xiaoju.basetech.service.CumulativeCoverageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CumulativeCoverageControllerTest {

    private MockMvc mockMvc;
    private CumulativeCoverageService service;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(CumulativeCoverageService.class);
        CumulativeCoverageController controller = new CumulativeCoverageController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturn304WhenEtagMatched() throws Exception {
        CoverageSourceResponse response = new CoverageSourceResponse();
        response.setEtag("etag-1");
        Mockito.when(service.querySource("set1", "com.example.Foo")).thenReturn(response);

        mockMvc.perform(get("/api/v1/coverage/sets/set1/source")
                        .param("class_key", "com.example.Foo")
                        .header("If-None-Match", "etag-1"))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", "etag-1"));
    }

    @Test
    void shouldReturn200WhenEtagNotMatched() throws Exception {
        CoverageSourceResponse response = new CoverageSourceResponse();
        response.setEtag("etag-2");
        Mockito.when(service.querySource("set1", "com.example.Foo")).thenReturn(response);

        mockMvc.perform(get("/api/v1/coverage/sets/set1/source")
                        .param("class_key", "com.example.Foo")
                        .header("If-None-Match", "etag-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "etag-2"));
    }
}
