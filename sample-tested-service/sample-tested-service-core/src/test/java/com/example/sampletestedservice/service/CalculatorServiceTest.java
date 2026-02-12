package com.example.sampletestedservice.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CalculatorServiceTest {

    @Test
    void add_returnsSum() {
        CalculatorService service = new CalculatorService();
        Assertions.assertEquals(3, service.add(1, 2));
    }

    @Test
    void sub_returnsDifference() {
        CalculatorService service = new CalculatorService();
        Assertions.assertEquals(7, service.sub(10, 3));
    }

    @Test
    void mul_returnsProduct() {
        CalculatorService service = new CalculatorService();
        Assertions.assertEquals(42, service.mul(6, 7));
    }

    @Test
    void calcWithBranch_routesOperations() {
        CalculatorService service = new CalculatorService();
        Assertions.assertEquals(3, service.calcWithBranch("add", 1, 2));
        Assertions.assertEquals(7, service.calcWithBranch("sub", 10, 3));
        Assertions.assertEquals(42, service.calcWithBranch("mul", 6, 7));
    }

    @Test
    void calcWithBranch_throwsOnUnsupportedOp() {
        CalculatorService service = new CalculatorService();
        IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.calcWithBranch("div", 6, 2)
        );
        Assertions.assertTrue(ex.getMessage().contains("unsupported op"));
    }
}
