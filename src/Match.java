import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Skill-based match between a student's skills and a posting's skills (all comma separated).
 * Score = share of required skills the student has. If the posting also lists preferred skills,
 * they count for 20% of the score and required skills for 80%.
 */
public final class Match {
    public final int percent;
    public final List<String> matched = new ArrayList<>();      // required skills the student has
    public final List<String> missing = new ArrayList<>();      // required skills the student lacks
    public final List<String> prefMatched = new ArrayList<>();  // preferred skills the student has
    public final List<String> prefMissing = new ArrayList<>();

    private Match(String studentSkills, String required, String preferred) {
        Set<String> have = new HashSet<>();
        for (String s : skillList(studentSkills)) have.add(s.toLowerCase(Locale.ROOT));
        split(have, required, matched, missing);
        split(have, preferred, prefMatched, prefMissing);
        int reqTotal = matched.size() + missing.size();
        int prefTotal = prefMatched.size() + prefMissing.size();
        double req = reqTotal == 0 ? 0 : (double) matched.size() / reqTotal;
        double pref = prefTotal == 0 ? 0 : (double) prefMatched.size() / prefTotal;
        double score;
        if (reqTotal == 0) score = pref;
        else if (prefTotal == 0) score = req;
        else score = 0.8 * req + 0.2 * pref;
        percent = (int) Math.round(score * 100);
    }

    public static Match of(String studentSkills, String required) {
        return new Match(studentSkills, required, "");
    }

    public static Match of(String studentSkills, String required, String preferred) {
        return new Match(studentSkills, required, preferred);
    }

    /** True if the posting lists at least one skill. */
    public boolean hasRequirements() {
        return !matched.isEmpty() || !missing.isEmpty() || !prefMatched.isEmpty() || !prefMissing.isEmpty();
    }

    private static void split(Set<String> have, String skills, List<String> yes, List<String> no) {
        Set<String> seen = new HashSet<>();
        for (String s : skillList(skills)) {
            String key = s.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) continue;   // ignore duplicates
            (have.contains(key) ? yes : no).add(s);
        }
    }

    /** Splits "Java, SQL; Git" into trimmed, non-empty skills. */
    public static List<String> skillList(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String part : s.split("[,;/]")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
