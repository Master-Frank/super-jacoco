package com.example.sampletestedservice.service;

import org.springframework.stereotype.Service;

@Service
public class CalculatorService {

    public int add(int a, int b) {
        int result = Math.addExact(a, b);
        return result;
    }

    public int sub(int a, int b) {
        return Math.subtractExact(a, b);
    }

    public int mul(int a, int b) {
        return a * b;
    }

    public int square(int x) {
        return Math.multiplyExact(x, x);
    }

    public int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    public int sumUpTo(int n) {
        int sum = 0;
        for (int i = 1; i <= n; i++) {
            sum = add(sum, i);
        }
        return sum;
    }

    public boolean isEven(int x) {
        return (x & 1) == 0;
    }

    public int warmup() {
        int a = clamp(-1, 0, 10);
        int b = clamp(11, 0, 10);
        int c = clamp(5, 0, 10);

        int even = sumUpTo(4);
        int odd = sumUpTo(5);

        int result = add(a, add(b, c));
        if (isEven(even)) {
            result = add(result, square(3));
        }
        if (!isEven(odd)) {
            result = add(result, sub(odd, 1));
        }
        return result;
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
