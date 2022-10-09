package ru.chemicalbase.utils;

public class Math {
    public static int incrementIfContainsRemainder(int numerator, int denominator) {
        int remainder = numerator % denominator;
        return remainder == 0 ? numerator / denominator : numerator / denominator + 1;
    }
}
