package com.example.sampletestedservice.service;

import org.springframework.stereotype.Service;

@Service
public class CalculatorService {

    public int add(int a, int b) {
        return a + b;
    }

    public int sub(int a, int b) {
        return a - b;
    }

    public int mul(int a, int b) {
        return a * b;
    }

    public int calcWithBranch(String op, int a, int b) {
        if ("add".equalsIgnoreCase(op)) {
            return add(a, b);
        } else if ("sub".equalsIgnoreCase(op)) {
            return sub(a, b);
        } else if ("mul".equalsIgnoreCase(op)) {
            return mul(a, b);
        } else {
            throw new IllegalArgumentException("unsupported op: " + op);
        }
    }
}

