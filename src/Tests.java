import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Automated unit tests for the parts of the portal that do not need the database:
 * skill matching (Match), password hashing (Auth), the PDF writer (Pdf) and status names.
 *
 * Run after compiling:  java -cp "out;lib\*" Tests
 * Each check prints PASS or FAIL, and the program exits with code 1 if anything failed.
 */
public final class Tests {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("== Skill matching (Match) ==");
        // posting needs Java, SQL, Git; student has Java and SQL -> 2 of 3 = 67%
        Match m = Match.of("Java, SQL", "Java, SQL, Git");
        check("2 of 3 required skills gives 67%", m.percent == 67);
        check("matched skills are Java and SQL", m.matched.equals(List.of("Java", "SQL")));
        check("missing skill is Git", m.missing.equals(List.of("Git")));

        check("all required skills gives 100%", Match.of("Java, SQL, Git", "Java, SQL, Git").percent == 100);
        check("no matching skills gives 0%", Match.of("Python", "Java, SQL").percent == 0);
        check("matching ignores upper/lower case", Match.of("java, sql", "Java, SQL").percent == 100);
        check("extra spaces are ignored", Match.of("  Java ,SQL  ", "Java,SQL").percent == 100);

        // with preferred skills: 0.8 x required share + 0.2 x preferred share
        // required 2 of 3 (0.667), preferred 0 of 1 -> 0.8 x 0.667 = 53%
        check("preferred skills count 20% (example gives 53%)",
                Match.of("Java, SQL", "Java, SQL, Git", "Docker").percent == 53);
        check("having the preferred skill too gives 100%",
                Match.of("Java, SQL, Git, Docker", "Java, SQL, Git", "Docker").percent == 100);

        check("a posting with no skills has no requirements", !Match.of("Java", "").hasRequirements());
        check("skills split on commas, semicolons and slashes",
                Match.skillList("Java; SQL / Git, ,").equals(List.of("Java", "SQL", "Git")));
        check("duplicate required skills are counted once", Match.of("Java", "Java, java").percent == 100);

        System.out.println();
        System.out.println("== Password hashing (Auth) ==");
        String stored = Auth.hash("secret123");
        check("the correct password is accepted", Auth.verify("secret123", stored));
        check("a wrong password is rejected", !Auth.verify("secret124", stored));
        check("the password is not stored as plain text", !stored.contains("secret123"));
        check("stored value has the form salt:hash", stored.split(":").length == 2);
        check("same password hashed twice gives different results (random salt)",
                !Auth.hash("secret123").equals(Auth.hash("secret123")));
        check("a damaged stored value is rejected, not crashed on", !Auth.verify("secret123", "garbage"));

        System.out.println();
        System.out.println("== Offer-letter PDF (Pdf) ==");
        byte[] pdf = Pdf.simple("Offer Letter", List.of("Dear Aarav,", "Start date: 2027-01-15"));
        String text = new String(pdf, StandardCharsets.ISO_8859_1);
        check("output starts with the PDF signature %PDF", text.startsWith("%PDF"));
        check("output ends with the PDF end marker %%EOF", text.trim().endsWith("%%EOF"));
        check("the letter text is inside the PDF", text.contains("Dear Aarav,"));

        System.out.println();
        System.out.println("== Status names ==");
        check("UNDER_REVIEW is shown as \"Under review\"", PortalDao.pretty("UNDER_REVIEW").equals("Under review"));
        check("SELECTED is shown as \"Selected\"", PortalDao.pretty("SELECTED").equals("Selected"));

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void check(String name, boolean ok) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
    }
}
