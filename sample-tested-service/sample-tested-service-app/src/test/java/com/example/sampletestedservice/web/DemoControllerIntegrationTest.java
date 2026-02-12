package com.example.sampletestedservice.web;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void ping_returnsPong() {
        ResponseEntity<String> resp = restTemplate.getForEntity(url("/demo/ping"), String.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        Assertions.assertEquals("pong", resp.getBody());
    }

    @Test
    void calc_coversAllBranches() {
        ResponseEntity<Integer> add = restTemplate.getForEntity(url("/demo/calc?op=add&a=1&b=2"), Integer.class);
        Assertions.assertEquals(HttpStatus.OK, add.getStatusCode());
        Assertions.assertEquals(3, add.getBody());

        ResponseEntity<Integer> sub = restTemplate.getForEntity(url("/demo/calc?op=sub&a=10&b=3"), Integer.class);
        Assertions.assertEquals(HttpStatus.OK, sub.getStatusCode());
        Assertions.assertEquals(7, sub.getBody());

        ResponseEntity<Integer> mul = restTemplate.getForEntity(url("/demo/calc?op=mul&a=6&b=7"), Integer.class);
        Assertions.assertEquals(HttpStatus.OK, mul.getStatusCode());
        Assertions.assertEquals(42, mul.getBody());

        ResponseEntity<String> unsupported = restTemplate.getForEntity(url("/demo/calc?op=div&a=6&b=2"), String.class);
        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, unsupported.getStatusCode());
    }

    private String url(String pathAndQuery) {
        return "http://127.0.0.1:" + port + pathAndQuery;
    }
}
