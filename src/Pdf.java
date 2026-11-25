import java.nio.charset.StandardCharsets;
import java.util.List;

/** Tiny PDF writer: one page, a title and plain text lines (ASCII only). Used for demo resumes and offer letters. */
public final class Pdf {
    private Pdf() {}

    public static byte[] simple(String title, List<String> lines) {
        StringBuilder cs = new StringBuilder("BT /F1 16 Tf 72 740 Td (").append(clean(title))
                .append(") Tj /F1 11 Tf 0 -30 Td 18 TL ");
        for (String line : lines) cs.append("(").append(clean(line)).append(") Tj T* ");
        cs.append("ET");

        String[] objs = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 5 0 R /Resources << /Font << /F1 4 0 R >> >> >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + cs.length() + " >>\nstream\n" + cs + "\nendstream"};
        StringBuilder sb = new StringBuilder("%PDF-1.4\n");
        int[] off = new int[objs.length];
        for (int i = 0; i < objs.length; i++) {
            off[i] = sb.length();
            sb.append(i + 1).append(" 0 obj\n").append(objs[i]).append("\nendobj\n");
        }
        int xref = sb.length();
        sb.append("xref\n0 6\n0000000000 65535 f \n");
        for (int o : off) sb.append(String.format("%010d 00000 n \n", o));
        sb.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Keeps printable ASCII and escapes the characters that are special inside a PDF string. */
    private static String clean(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c < 32 || c > 126) sb.append('?');
            else if (c == '(' || c == ')' || c == '\\') sb.append('\\').append(c);
            else sb.append(c);
        }
        return sb.toString();
    }
}
