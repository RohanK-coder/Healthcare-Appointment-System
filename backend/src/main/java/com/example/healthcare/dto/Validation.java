package com.example.healthcare.dto;

/** Shared validation rules so every endpoint that accepts a password or name enforces the same policy. */
public final class Validation {
    private Validation() {}

    /**
     * 8-72 characters (BCrypt ignores bytes past 72) with at least one letter and one digit.
     * Length beats complexity rules; this is a deliberately modest policy for a demo system.
     */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE = "must be 8-72 characters and contain a letter and a digit";

    public static final String PHONE_REGEX = "^$|^[0-9+()\\-\\s]{7,20}$";
    public static final String PHONE_MESSAGE = "must be 7-20 digits, spaces, or + ( ) -";
}
