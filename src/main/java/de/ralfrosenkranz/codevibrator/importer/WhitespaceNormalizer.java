package de.ralfrosenkranz.codevibrator.importer;

public final class WhitespaceNormalizer {
    private WhitespaceNormalizer() {}

    public static String normalize(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean inWs = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!inWs) {
                    out.append(' ');
                    inWs = true;
                }
            } else {
                out.append(c);
                inWs = false;
            }
        }
        return out.toString();
    }
}
