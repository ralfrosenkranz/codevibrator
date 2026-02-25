package de.ralfrosenkranz.codevibrator.selectors;

public record FileDecision(
        boolean inZip,
        boolean conflict,
        boolean blocked,
        String reason
) {}
