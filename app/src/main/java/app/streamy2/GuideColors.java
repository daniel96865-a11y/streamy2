package app.streamy2;

/** Placeholder logo colours and initials for channels without a logo. */
final class GuideColors {
    private static final int[] PALETTE = {
            0xFF3A6DF0, 0xFFE0564F, 0xFF22B573, 0xFFD9A441, 0xFFE24B6A, 0xFF8B5CF6,
            0xFFF59E0B, 0xFFEC4899, 0xFF14B8A6, 0xFF6366F1, 0xFF10B981, 0xFF38BDF8};

    private GuideColors() {
    }

    static int forName(String name) {
        String n = name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
        int h = 0;
        for (int i = 0; i < n.length(); i++) h = h * 31 + n.charAt(i);
        return PALETTE[Math.abs(h % PALETTE.length)];
    }

    /** "Kanal 1" → "K1", "Sport HD" → "SH", "Nachrichten" → "NA". */
    static String initials(String name) {
        String clean = name == null ? "" : name.replaceAll("[^\\p{L}\\p{N} ]", " ").trim();
        if (clean.isEmpty()) return "TV";
        String[] parts = clean.split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            b.append(Character.toUpperCase(p.charAt(0)));
            if (b.length() == 2) break;
        }
        if (b.length() == 1 && parts[0].length() > 1) b.append(Character.toUpperCase(parts[0].charAt(1)));
        return b.toString();
    }
}
