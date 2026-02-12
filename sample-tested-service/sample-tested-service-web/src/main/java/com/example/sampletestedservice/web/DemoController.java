package com.example.sampletestedservice.web;

import com.example.sampletestedservice.service.CalculatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    private final CalculatorService calculatorService;

    public DemoController(CalculatorService calculatorService) {
        this.calculatorService = calculatorService;
    }

    @GetMapping("/demo/ping")
    public ResponseEntity<String> ping() {
        calculatorService.warmup();
        return ResponseEntity.ok("pong");
    }

    @GetMapping("/demo/calc")
    public ResponseEntity<Integer> calc(@RequestParam("op") String op,
                                        @RequestParam("a") int a,
                                        @RequestParam("b") int b) {
        int result = calculatorService.calcWithBranch(op, a, b);
        return ResponseEntity.ok(result);
    }
}
