import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Web front end for the Job & Internship Portal. Run, then open http://localhost:8080 */
public class WebApp {
    private static PortalDao dao;
    private static final Map<String, User> sessions = new ConcurrentHashMap<>();
    private static final SecureRandom random = new SecureRandom();

    public static void main(String[] args) throws IOException {
        try {
            dao = new PortalDao();
        } catch (RuntimeException e) {
            System.out.println("Startup failed: " + e.getMessage());
            return;
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8080), 0);
        server.createContext("/", WebApp::handle);
        server.start();
        System.out.println("Portal running. Open http://localhost:8080 in your browser (Ctrl+C to stop).");
    }

    // ---------- request plumbing ----------

    private static final int MAX_BODY = 3 * 1024 * 1024;       // whole request
    private static final int MAX_RESUME = 2 * 1024 * 1024;     // resume file

    private static final class TooLarge extends IOException {}

    private static final class Req {
        final HttpExchange ex;
        final Map<String, String> p = new HashMap<>();
        User user;
        String sid;
        byte[] fileData;   // uploaded file, if any
        String fileName;

        Req(HttpExchange ex) throws IOException {
            this.ex = ex;
            parse(ex.getRequestURI().getRawQuery());
            if (ex.getRequestMethod().equals("POST")) {
                byte[] body = ex.getRequestBody().readNBytes(MAX_BODY + 1);
                if (body.length > MAX_BODY) throw new TooLarge();
                String ct = ex.getRequestHeaders().getFirst("Content-Type");
                if (ct != null && ct.startsWith("multipart/form-data")) parseMultipart(body, ct);
                else parse(new String(body, StandardCharsets.UTF_8));
            }
            String cookie = ex.getRequestHeaders().getFirst("Cookie");
            if (cookie != null) {
                for (String part : cookie.split(";")) {
                    String[] kv = part.trim().split("=", 2);
                    if (kv.length == 2 && kv[0].equals("sid")) {
                        sid = kv[1];
                        user = sessions.get(sid);
                    }
                }
            }
        }

        /** Minimal multipart/form-data parser: text fields go into p, one file into fileData. */
        private void parseMultipart(byte[] body, String ct) {
            int bi = ct.indexOf("boundary=");
            if (bi < 0) return;
            String boundary = "--" + ct.substring(bi + 9).replace("\"", "").trim();
            String s = new String(body, StandardCharsets.ISO_8859_1);   // 1 char = 1 byte
            int pos = s.indexOf(boundary);
            while (pos >= 0) {
                int start = pos + boundary.length();
                if (s.startsWith("--", start)) break;
                int next = s.indexOf(boundary, start);
                if (next < 0) break;
                String part = s.substring(start, next);
                int hEnd = part.indexOf("\r\n\r\n");
                if (hEnd > 0) {
                    String headers = part.substring(0, hEnd);
                    String content = part.substring(hEnd + 4);
                    if (content.endsWith("\r\n")) content = content.substring(0, content.length() - 2);
                    String name = attr(headers, "name");
                    String fn = attr(headers, "filename");
                    if (fn != null) {
                        if (!fn.isEmpty()) {
                            fileName = fn;
                            fileData = content.getBytes(StandardCharsets.ISO_8859_1);
                        }
                    } else if (name != null) {
                        p.put(name, new String(content.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8).trim());
                    }
                }
                pos = next;
            }
        }

        private static String attr(String headers, String key) {
            Matcher m = Pattern.compile("[; ]" + key + "=\"([^\"]*)\"").matcher(headers);
            return m.find() ? m.group(1) : null;
        }

        private void parse(String s) {
            if (s == null || s.isEmpty()) return;
            for (String pair : s.split("&")) {
                String[] kv = pair.split("=", 2);
                p.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8).trim() : "");
            }
        }

        String get(String k) { return p.getOrDefault(k, ""); }

        int getInt(String k) {
            try { return Integer.parseInt(get(k)); } catch (NumberFormatException e) { return -1; }
        }

        boolean post() { return ex.getRequestMethod().equals("POST"); }
    }

    private static void handle(HttpExchange ex) throws IOException {
        try {
            Req r = new Req(ex);
            switch (ex.getRequestURI().getPath()) {
                case "/" -> home(r);
                case "/login" -> login(r);
                case "/register" -> register(r);
                case "/logout" -> logout(r);
                case "/jobs" -> jobs(r);
                case "/job" -> jobDetails(r);
                case "/company" -> companyPage(r);
                case "/apply" -> apply(r);
                case "/report" -> report(r);
                case "/profile" -> profile(r);
                case "/applications" -> myApplications(r);
                case "/resume" -> resume(r);
                case "/notifications" -> notifications(r);
                case "/offer/respond" -> respondToOffer(r);
                case "/offer/letter" -> offerLetter(r);
                case "/company/edit" -> companyEdit(r);
                case "/post" -> postJob(r);
                case "/postings" -> myPostings(r);
                case "/close" -> closePosting(r);
                case "/reopen" -> reopenPosting(r);
                case "/edit" -> editPosting(r);
                case "/withdraw" -> withdraw(r);
                case "/password" -> changePassword(r);
                case "/admin/password" -> adminResetPassword(r);
                case "/applicants" -> applicants(r);
                case "/candidate" -> candidate(r);
                case "/status" -> updateStatus(r);
                case "/interview" -> scheduleInterview(r);
                case "/offer" -> makeOffer(r);
                case "/admin" -> admin(r);
                case "/admin/user" -> adminUser(r);
                case "/admin/company" -> adminCompany(r);
                case "/admin/postings" -> adminPostings(r);
                case "/admin/delete" -> adminDelete(r);
                case "/admin/reports" -> adminReports(r);
                case "/admin/report" -> adminReportAction(r);
                default -> send(r, 404, layout(r, "Not found", "<p>That page does not exist.</p>"));
            }
        } catch (TooLarge e) {
            byte[] b = "File too large (max 2 MB). Go back and choose a smaller PDF.".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(413, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        } catch (Exception e) {
            e.printStackTrace();
            byte[] b = "Something went wrong.".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(500, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        } finally {
            ex.close();
        }
    }

    private static void send(Req r, int code, String html) throws IOException {
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        r.ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        r.ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = r.ex.getResponseBody()) { os.write(b); }
    }

    private static void sendPdf(Req r, byte[] data, String name) throws IOException {
        r.ex.getResponseHeaders().set("Content-Type", "application/pdf");
        r.ex.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + name + "\"");
        r.ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        r.ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = r.ex.getResponseBody()) { os.write(data); }
    }

    private static void redirect(Req r, String to, String msg) throws IOException {
        if (msg != null) to += (to.contains("?") ? "&" : "?") + "msg=" + URLEncoder.encode(msg, StandardCharsets.UTF_8);
        r.ex.getResponseHeaders().set("Location", to);
        r.ex.sendResponseHeaders(302, -1);
    }

    /** @return true if the user has the role; otherwise redirects to login and returns false. */
    private static boolean require(Req r, String role) throws IOException {
        if (r.user != null && r.user.getRole().equals(role)) return true;
        redirect(r, "/login", "Please log in with a " + role.toLowerCase() + " account.");
        return false;
    }

    // ---------- html helpers ----------

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static final String CSS = """
            *{box-sizing:border-box}body{margin:0;font-family:Segoe UI,Arial,sans-serif;background:#f4f6fb;color:#1f2937}
            header{background:#1e3a8a;color:#fff;padding:10px 24px;display:flex;flex-wrap:wrap;align-items:center;gap:8px 20px}
            header a{color:#dbeafe;text-decoration:none;white-space:nowrap}header a:hover{color:#fff}
            header .brand{font-weight:700;font-size:1.15rem;color:#fff}header .sp{flex:1}
            header nav,header .right{display:flex;flex-wrap:wrap;align-items:center;gap:6px 16px}
            header .right{font-size:.92rem}
            @media(max-width:1200px){header nav{order:3;flex-basis:100%;border-top:1px solid rgba(255,255,255,.25);padding-top:8px}}
            main{max-width:1100px;margin:24px auto;padding:0 16px}
            .card{background:#fff;border-radius:10px;padding:20px;box-shadow:0 1px 4px rgba(0,0,0,.08);margin-bottom:20px}
            h1,h2{margin-top:0}table{width:100%;border-collapse:collapse;font-size:.92rem}
            th,td{text-align:left;padding:8px;border-bottom:1px solid #e5e7eb;vertical-align:top}th{background:#f1f5f9}
            input,select,textarea{padding:8px;border:1px solid #cbd5e1;border-radius:6px;font:inherit;width:100%}
            label{display:block;margin:10px 0 4px;font-weight:600}
            .inline input,.inline select{width:auto}
            button,.btn{background:#2563eb;color:#fff;border:0;border-radius:6px;padding:8px 14px;cursor:pointer;font:inherit;text-decoration:none;display:inline-block}
            button:hover,.btn:hover{background:#1d4ed8}button.red{background:#dc2626}button.green{background:#16a34a}button.grey{background:#6b7280}
            .msg{background:#dbeafe;border-left:4px solid #2563eb;padding:10px 14px;margin-bottom:16px;border-radius:4px}
            .warn{background:#fef3c7;border-left:4px solid #d97706;padding:10px 14px;margin-bottom:16px;border-radius:4px}
            .tag{padding:2px 8px;border-radius:10px;font-size:.8rem;background:#e0e7ff;white-space:nowrap}
            .JOB{background:#dcfce7}.INTERNSHIP{background:#fef3c7}.stats{display:flex;gap:12px;flex-wrap:wrap}
            .stat{flex:1;min-width:120px;background:#eff6ff;border-radius:8px;padding:14px;text-align:center}
            .stat b{display:block;font-size:1.6rem;color:#1e3a8a}form.d{display:inline}
            .ver{color:#16a34a;font-weight:700;font-size:.8rem}
            .match{padding:2px 8px;border-radius:10px;font-weight:700;font-size:.85rem}
            .m-hi{background:#dcfce7;color:#166534}.m-mid{background:#fef3c7;color:#92400e}.m-lo{background:#fee2e2;color:#991b1b}
            .pipe{display:flex;gap:4px;flex-wrap:wrap}.pipe span{padding:3px 8px;border-radius:12px;font-size:.78rem;background:#f1f5f9;color:#6b7280}
            .pipe .done{background:#dcfce7;color:#166534}.pipe .cur{background:#2563eb;color:#fff;font-weight:700}
            .rej{background:#fee2e2;color:#991b1b}.risk-HIGH{background:#fee2e2;color:#991b1b}.risk-MEDIUM{background:#fef3c7;color:#92400e}.risk-LOW{background:#dcfce7;color:#166534}
            .ok{color:#16a34a}.no{color:#dc2626}.unread{background:#eff6ff;font-weight:600}
            .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px}
            .two{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:20px}
            .bar{background:#e5e7eb;border-radius:6px;height:14px;min-width:120px}.bar span{display:block;height:100%;background:#2563eb;border-radius:6px}
            .offer{background:#f0fdf4;border:1px solid #bbf7d0;border-radius:8px;padding:10px}
            details{margin-top:4px}summary{cursor:pointer;color:#2563eb}
            @media(max-width:700px){table{display:block;overflow-x:auto}}
            """;

    private static String layout(Req r, String title, String body) {
        StringBuilder nav = new StringBuilder();
        nav.append("<a href='/jobs'>Browse Openings</a>");
        String right;
        if (r.user != null) {
            switch (r.user.getRole()) {
                case "STUDENT" -> nav.append("<a href='/applications'>My Applications</a><a href='/profile'>My Profile</a>");
                case "RECRUITER" -> nav.append("<a href='/postings'>Dashboard</a><a href='/post'>Post Opening</a>"
                        + "<a href='/applicants'>Applicants</a><a href='/company/edit'>Company Profile</a>");
                default -> nav.append("<a href='/admin'>Dashboard</a><a href='/admin/postings'>All Postings</a>"
                        + "<a href='/admin/reports'>Reports (" + dao.openReportCount() + ")</a>");
            }
            int unread = dao.unreadCount(r.user.getId());
            right = "<a href='/notifications'>Notifications" + (unread > 0 ? " (" + unread + ")" : "") + "</a>"
                    + "<a href='/password' title='Change password'>" + esc(r.user.getName()) + " (" + r.user.getRole().toLowerCase() + ")</a>"
                    + "<form class='d' method='post' action='/logout'><button class='red'>Logout</button></form>";
        } else {
            right = "<a href='/login'>Login</a><a href='/register'>Register</a>";
        }
        String msg = r.get("msg");
        return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + esc(title) + " - Job &amp; Internship Portal</title><style>" + CSS + "</style></head><body>"
                + "<header><a class='brand' href='/'>Job &amp; Internship Portal</a><nav>" + nav + "</nav><span class='sp'></span>"
                + "<div class='right'>" + right + "</div></header>"
                + "<main>" + (msg.isEmpty() ? "" : "<div class='msg'>" + esc(msg) + "</div>") + body + "</main></body></html>";
    }

    private static String matchBadge(int percent) {
        String cls = percent >= 70 ? "m-hi" : percent >= 40 ? "m-mid" : "m-lo";
        return "<span class='match " + cls + "'>" + percent + "%</span>";
    }

    private static String modeText(String mode) {
        return mode == null ? "" : PortalDao.pretty(mode);
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP_FMT = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    /** 2027-03-31 becomes "31 Mar 2027"; anything that is not a date (for example "-") is returned unchanged. */
    private static String fmtDate(String iso) {
        try {
            return LocalDate.parse(iso).format(DATE_FMT);
        } catch (Exception e) {
            return iso;
        }
    }

    /** MySQL timestamp "2026-11-18 00:12:10.0" becomes "18 Nov 2026, 00:12". */
    private static String fmtStamp(String ts) {
        try {
            return LocalDateTime.parse(ts.substring(0, 19).replace(' ', 'T')).format(STAMP_FMT);
        } catch (Exception e) {
            return ts;
        }
    }

    /** "Remote" as the location of a remote posting is shown once, not twice. */
    private static boolean sameAsMode(String location, String mode) {
        return location.trim().equalsIgnoreCase(modeText(mode));
    }

    private static String dash(String s) {
        return s == null || s.equals("-") ? "" : s;
    }

    private static String sel(String current, String value) {
        return value.equals(current) ? " selected" : "";
    }

    private static String studentSkills(Req r) {
        return r.user != null && r.user.getRole().equals("STUDENT") ? dao.getProfile(r.user.getId())[4] : null;
    }

    private static String bar(int value, int max) {
        int pct = max <= 0 ? 0 : Math.min(100, value * 100 / max);
        return "<div class='bar'><span style='width:" + pct + "%'></span></div>";
    }

    private static String stat(String label, int v) {
        return "<div class='stat'><b>" + v + "</b>" + label + "</div>";
    }

    private static String hidden(String name, Object value) {
        return "<input type='hidden' name='" + name + "' value='" + esc(String.valueOf(value)) + "'>";
    }

    private static String postingsTable(List<Internship> list, Req r, boolean showApply) {
        if (list.isEmpty()) return "<p>No openings found.</p>";
        String skills = studentSkills(r);
        boolean student = skills != null;
        StringBuilder sb = new StringBuilder("<table><tr><th>Type</th><th>Title</th><th>Company</th><th>Location</th>"
                + "<th>Pay</th><th>Skills</th><th>Deadline</th>" + (student ? "<th>Match</th>" : "")
                + (student && showApply ? "<th></th>" : "") + "</tr>");
        for (Internship i : list) {
            sb.append("<tr><td><span class='tag ").append(i.getType()).append("'>").append(i.getType())
                    .append("</span></td><td><a href='/job?id=").append(i.getId()).append("'>").append(esc(i.getTitle()))
                    .append("</a></td><td><a href='/company?id=").append(i.getCompanyId()).append("'>").append(esc(i.getCompany()))
                    .append("</a>").append(i.isVerified() ? " <span class='ver'>&#10003; Verified</span>" : "")
                    .append("</td><td>").append(esc(i.getLocation()))
                    .append(sameAsMode(i.getLocation(), i.getWorkMode()) ? "" : "<br><small>" + modeText(i.getWorkMode()) + "</small>")
                    .append("</td><td>").append(esc(i.getPay())).append("</td><td>").append(esc(i.getSkills()))
                    .append("</td><td>").append(esc(fmtDate(i.getDeadline()))).append("</td>");
            if (student) {
                Match m = Match.of(skills, i.getSkills(), i.getPreferred());
                sb.append("<td>").append(m.hasRequirements() ? matchBadge(m.percent) : "-").append("</td>");
                if (showApply) {
                    sb.append("<td><form class='d' method='post' action='/apply'>").append(hidden("id", i.getId()))
                            .append("<button>Apply</button></form></td>");
                }
            }
            sb.append("</tr>");
        }
        return sb.append("</table>").toString();
    }

    private static final String[] STEPS = {"APPLIED", "UNDER_REVIEW", "SHORTLISTED", "INTERVIEW", "SELECTED"};

    private static String pipeline(String status) {
        if (status.equals("REJECTED")) return "<span class='tag rej'>Rejected</span>";
        if (status.equals("WITHDRAWN")) return "<span class='tag'>Withdrawn by you</span>";
        int cur = 0;
        for (int i = 0; i < STEPS.length; i++) if (STEPS[i].equals(status)) cur = i;
        StringBuilder sb = new StringBuilder("<div class='pipe'>");
        for (int i = 0; i < STEPS.length; i++) {
            boolean done = i < cur || (i == cur && i == STEPS.length - 1);
            String cls = done ? "done" : i == cur ? "cur" : "";
            String icon = done ? "&#10003; " : i == cur ? "&#9679; " : "&#9675; ";
            sb.append("<span class='").append(cls).append("'>").append(icon).append(PortalDao.pretty(STEPS[i])).append("</span>");
        }
        return sb.append("</div>").toString();
    }

    private static String linkOrText(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return "<a href='" + esc(url) + "' target='_blank' rel='noopener'>" + esc(url) + "</a>";
        }
        return esc(url);
    }

    private static String interviewList(List<String[]> rounds) {
        if (rounds.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (String[] iv : rounds) {
            sb.append("<div><b>").append(esc(iv[0])).append("</b> (").append(PortalDao.pretty(iv[1])).append(")<br>")
                    .append(esc(fmtDate(iv[2]))).append(" at ").append(esc(iv[3]));
            if (!iv[4].equals("-")) sb.append("<br>").append(linkOrText(iv[4]));
            sb.append("</div>");
        }
        return sb.toString();
    }

    // ---------- public pages ----------

    private static void home(Req r) throws IOException {
        if (r.user != null) {
            switch (r.user.getRole()) {
                case "STUDENT" -> redirect(r, "/jobs", null);
                case "RECRUITER" -> redirect(r, "/postings", null);
                default -> redirect(r, "/admin", null);
            }
            return;
        }
        String body = "<div class='card'><h1>Find your next job or internship</h1>"
                + "<p>Students get skill-match scores and recommendations, recruiters rank applicants and run interviews "
                + "through to offers, and admins keep the platform safe.</p>"
                + "<a class='btn' href='/register'>Create an account</a> <a class='btn' href='/login'>Login</a></div>"
                + "<div class='card'><h2>Latest openings</h2>" + postingsTable(dao.searchPostings("", null), r, false) + "</div>";
        send(r, 200, layout(r, "Home", body));
    }

    private static void login(Req r) throws IOException {
        if (r.post()) {
            User u = dao.login(r.get("email"), r.get("password"));
            if (u == null) { redirect(r, "/login", "Wrong email or password."); return; }
            if (u.getStatus().equals("PENDING")) { redirect(r, "/login", "Your account is awaiting admin verification."); return; }
            if (u.getStatus().equals("BLOCKED")) { redirect(r, "/login", "Your account has been blocked."); return; }
            byte[] t = new byte[24];
            random.nextBytes(t);
            String sid = Base64.getUrlEncoder().withoutPadding().encodeToString(t);
            sessions.put(sid, u);
            r.ex.getResponseHeaders().add("Set-Cookie", "sid=" + sid + "; Path=/; HttpOnly; SameSite=Lax");
            redirect(r, "/", null);
            return;
        }
        String body = "<div class='card' style='max-width:420px;margin:auto'><h1>Login</h1><form method='post'>"
                + "<label>Email</label><input name='email' type='email' required>"
                + "<label>Password</label><input name='password' type='password' required><br><br>"
                + "<button>Login</button></form><p>No account? <a href='/register'>Register</a></p>"
                + "<p><small>Forgot your password? Ask an administrator to reset it for you.</small></p></div>";
        send(r, 200, layout(r, "Login", body));
    }

    private static void register(Req r) throws IOException {
        if (r.post()) {
            String role = r.get("role").equals("RECRUITER") ? "RECRUITER" : "STUDENT";
            String name = r.get("name"), email = r.get("email"), pass = r.get("password"), company = r.get("company");
            String err = null;
            if (name.isEmpty() || email.isEmpty()) err = "Name and email are required.";
            else if (!email.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) err = "Invalid email format.";
            else if (pass.length() < 6) err = "Password must be at least 6 characters.";
            else if (role.equals("RECRUITER") && company.isEmpty()) err = "Company name is required for recruiters.";
            if (err == null) err = dao.register(name, email, pass, role, company);
            if (err != null) { redirect(r, "/register", err); return; }
            redirect(r, "/login", role.equals("RECRUITER")
                    ? "Registered. Your company is pending verification - an admin must approve it before you can log in."
                    : "Registered! You can log in now.");
            return;
        }
        String body = "<div class='card' style='max-width:460px;margin:auto'><h1>Register</h1><form method='post'>"
                + "<label>I am a</label><select name='role' id='role' onchange=\"document.getElementById('co').style.display=this.value==='RECRUITER'?'block':'none'\">"
                + "<option value='STUDENT'>Student / Job seeker</option><option value='RECRUITER'>Recruiter / Company</option></select>"
                + "<label>Full name</label><input name='name' required>"
                + "<label>Email</label><input name='email' type='email' required>"
                + "<label>Password (min 6 characters)</label><input name='password' type='password' minlength='6' required>"
                + "<div id='co' style='display:none'><label>Company name</label><input name='company'>"
                + "<p><small>After registering, an admin verifies your company. You can add industry, size, website and "
                + "description from the Company Profile page once you are in.</small></p></div><br>"
                + "<button>Register</button></form></div>";
        send(r, 200, layout(r, "Register", body));
    }

    private static void logout(Req r) throws IOException {
        if (r.sid != null) sessions.remove(r.sid);
        r.ex.getResponseHeaders().add("Set-Cookie", "sid=; Path=/; Max-Age=0");
        redirect(r, "/login", "Logged out.");
    }

    private static void jobs(Req r) throws IOException {
        String q = r.get("q"), type = r.get("type"), loc = r.get("loc"), skill = r.get("skill"), mode = r.get("mode");
        if (!type.equals("JOB") && !type.equals("INTERNSHIP")) type = "";
        if (!mode.equals("ONSITE") && !mode.equals("REMOTE") && !mode.equals("HYBRID")) mode = "";
        boolean filtered = !(q.isEmpty() && type.isEmpty() && loc.isEmpty() && skill.isEmpty() && mode.isEmpty());

        StringBuilder body = new StringBuilder();
        String skills = studentSkills(r);
        if (skills != null && !filtered) body.append(recommendations(skills));

        body.append("<div class='card'><h1>Openings</h1><form class='inline' method='get' action='/jobs'><div class='grid'>")
                .append("<input name='q' placeholder='Keyword (title, company...)' value='").append(esc(q)).append("'>")
                .append("<input name='loc' placeholder='Location' value='").append(esc(loc)).append("'>")
                .append("<input name='skill' placeholder='Skill' value='").append(esc(skill)).append("'>")
                .append("<select name='type'><option value=''>Jobs &amp; Internships</option>")
                .append("<option value='JOB'").append(type.equals("JOB") ? " selected" : "").append(">Jobs</option>")
                .append("<option value='INTERNSHIP'").append(type.equals("INTERNSHIP") ? " selected" : "").append(">Internships</option></select>")
                .append("<select name='mode'><option value=''>Any work mode</option>")
                .append("<option value='ONSITE'").append(mode.equals("ONSITE") ? " selected" : "").append(">On-site</option>")
                .append("<option value='REMOTE'").append(mode.equals("REMOTE") ? " selected" : "").append(">Remote</option>")
                .append("<option value='HYBRID'").append(mode.equals("HYBRID") ? " selected" : "").append(">Hybrid</option></select>")
                .append("<button>Search</button></div></form><br>")
                .append(postingsTable(dao.searchPostings(q, type, loc, skill, mode), r, true)).append("</div>");
        send(r, 200, layout(r, "Openings", body.toString()));
    }

    /** Top matches for a student, based on skill overlap. */
    private static String recommendations(String skills) {
        StringBuilder sb = new StringBuilder("<div class='card'><h2>Recommended for you</h2>");
        if (skills.isBlank()) {
            return sb.append("<p>Add your skills in <a href='/profile'>your profile</a> to get personalised recommendations.</p></div>").toString();
        }
        List<Internship> all = new ArrayList<>(dao.searchPostings("", null));
        all.removeIf(i -> Match.of(skills, i.getSkills(), i.getPreferred()).percent == 0);
        all.sort(Comparator.comparingInt((Internship i) -> -Match.of(skills, i.getSkills(), i.getPreferred()).percent));
        if (all.isEmpty()) return sb.append("<p>No openings match your skills yet.</p></div>").toString();
        sb.append("<table><tr><th>Match</th><th>Title</th><th>Company</th><th>You have</th><th>You are missing</th></tr>");
        for (Internship i : all.subList(0, Math.min(5, all.size()))) {
            Match m = Match.of(skills, i.getSkills(), i.getPreferred());
            sb.append("<tr><td>").append(matchBadge(m.percent)).append("</td><td><a href='/job?id=").append(i.getId())
                    .append("'>").append(esc(i.getTitle())).append("</a></td><td>").append(esc(i.getCompany()))
                    .append("</td><td class='ok'>").append(esc(String.join(", ", m.matched)))
                    .append("</td><td class='no'>").append(esc(String.join(", ", m.missing))).append("</td></tr>");
        }
        return sb.append("</table></div>").toString();
    }

    private static void jobDetails(Req r) throws IOException {
        Internship j = dao.getPosting(r.getInt("id"));
        if (j == null) { send(r, 404, layout(r, "Not found", "<div class='card'>That posting does not exist.</div>")); return; }
        StringBuilder sb = new StringBuilder("<div class='card'><h1>").append(esc(j.getTitle())).append("</h1><h2>")
                .append("<a href='/company?id=").append(j.getCompanyId()).append("'>").append(esc(j.getCompany())).append("</a>")
                .append(j.isVerified() ? " <span class='ver'>&#10003; Verified company</span>" : "")
                .append("</h2><p><span class='tag ").append(j.getType()).append("'>").append(j.getType()).append("</span> &nbsp; ")
                .append(esc(j.getLocation()))
                .append(sameAsMode(j.getLocation(), j.getWorkMode()) ? "" : " (" + modeText(j.getWorkMode()) + ")").append(" &nbsp; ")
                .append(esc(j.getPay())).append(" &nbsp; Deadline: ").append(esc(fmtDate(j.getDeadline())))
                .append(" &nbsp; Status: ").append(j.getStatus()).append("</p>");
        sb.append("<p>");
        if (!j.getDuration().isEmpty()) sb.append("<b>Duration:</b> ").append(esc(j.getDuration())).append(" &nbsp; ");
        if (!j.getEducation().isEmpty()) sb.append("<b>Education:</b> ").append(esc(j.getEducation())).append(" &nbsp; ");
        if (!j.getExperience().isEmpty()) sb.append("<b>Experience:</b> ").append(esc(j.getExperience())).append(" &nbsp; ");
        if (j.getOpenings() > 0) sb.append("<b>Openings:</b> ").append(j.getOpenings());
        sb.append("</p><h3>Required skills</h3><p>").append(esc(j.getSkills())).append("</p>");
        if (!j.getPreferred().isEmpty()) sb.append("<h3>Preferred skills</h3><p>").append(esc(j.getPreferred())).append("</p>");
        sb.append("<h3>Description</h3><p style='white-space:pre-wrap'>").append(esc(j.getDescription())).append("</p>");
        String skills = studentSkills(r);
        if (skills != null) {
            Match m = Match.of(skills, j.getSkills(), j.getPreferred());
            if (m.hasRequirements()) {
                sb.append("<h3>Why this matches you: ").append(matchBadge(m.percent)).append("</h3><p>");
                for (String s : m.matched) sb.append("<span class='ok'>&#10003; ").append(esc(s)).append("</span> &nbsp; ");
                for (String s : m.missing) sb.append("<span class='no'>&#10007; ").append(esc(s)).append("</span> &nbsp; ");
                for (String s : m.prefMatched) sb.append("<span class='ok'>&#10003; ").append(esc(s)).append(" (preferred)</span> &nbsp; ");
                for (String s : m.prefMissing) sb.append("<span class='no'>&#10007; ").append(esc(s)).append(" (preferred)</span> &nbsp; ");
                sb.append("</p>");
                if (!m.missing.isEmpty()) sb.append("<p><small>Skills to learn: ").append(esc(String.join(", ", m.missing))).append("</small></p>");
            }
            if (j.getStatus().equals("OPEN")) {
                sb.append("<form method='post' action='/apply'>").append(hidden("id", j.getId()))
                        .append("<button>Apply now</button></form>");
            }
            sb.append("<details><summary>Report this posting</summary><form method='post' action='/report' style='max-width:420px'>")
                    .append(hidden("id", j.getId()))
                    .append("<label>Reason</label><select name='reason'><option>Fake company</option><option>Asking for money</option>"
                            + "<option>Suspicious link</option><option>Incorrect information</option><option>Other</option></select>"
                            + "<label>Details (optional)</label><textarea name='details' rows='3' maxlength='250'></textarea><br>"
                            + "<button class='red'>Submit report</button></form></details>");
        } else if (r.user == null) {
            sb.append("<p><a class='btn' href='/login'>Login</a> as a student to apply and see your match score.</p>");
        }
        send(r, 200, layout(r, j.getTitle(), sb.append("</div>").toString()));
    }

    private static void companyPage(Req r) throws IOException {
        int id = r.getInt("id");
        String[] c = dao.getCompany(id);
        if (c == null) { send(r, 404, layout(r, "Not found", "<div class='card'>That company does not exist.</div>")); return; }
        StringBuilder sb = new StringBuilder("<div class='card'><h1>").append(esc(c[0]))
                .append(c[6].equals("1") ? " <span class='ver'>&#10003; Verified</span>" : " <span class='tag'>Verification pending</span>")
                .append("</h1><p>");
        if (!c[1].isEmpty()) sb.append(esc(c[1])).append(" &middot; ");
        if (!c[4].isEmpty()) sb.append(esc(c[4])).append(" &middot; ");
        if (!c[2].isEmpty()) sb.append(esc(c[2])).append(" employees &middot; ");
        sb.append("<b>").append(c[7]).append("</b> open position(s)</p>");
        if (!c[3].isEmpty()) sb.append("<p>Website: ").append(linkOrText(c[3])).append("</p>");
        sb.append("<h3>About us</h3><p style='white-space:pre-wrap'>")
                .append(c[5].isEmpty() ? "This company has not added a description yet." : esc(c[5])).append("</p></div>");
        List<Internship> open = new ArrayList<>(dao.postingsOfCompany(id));
        open.removeIf(i -> !i.getStatus().equals("OPEN"));
        sb.append("<div class='card'><h2>Open positions</h2>").append(postingsTable(open, r, true)).append("</div>");
        send(r, 200, layout(r, c[0], sb.toString()));
    }

    // ---------- student ----------

    private static void apply(Req r) throws IOException {
        if (!require(r, "STUDENT")) return;
        if (!r.post()) { redirect(r, "/jobs", null); return; }
        if (!dao.isProfileComplete(r.user.getId())) {
            redirect(r, "/profile", "Please complete your profile and upload your resume before applying.");
            return;
        }
        redirect(r, "/applications", dao.apply(r.getInt("id"), r.user.getId()));
    }

    private static void report(Req r) throws IOException {
        if (!require(r, "STUDENT")) return;
        String reason = r.get("reason");
        List<String> allowed = List.of("Fake company", "Asking for money", "Suspicious link", "Incorrect information", "Other");
        if (!r.post() || !allowed.contains(reason)) { redirect(r, "/jobs", "Invalid report."); return; }
        String details = r.get("details");
        if (details.length() > 250) details = details.substring(0, 250);
        redirect(r, "/job?id=" + r.getInt("id"), dao.reportPosting(r.getInt("id"), r.user.getId(), reason, details));
    }

    private static void myApplications(Req r) throws IOException {
        if (!require(r, "STUDENT")) return;
        List<Application> list = dao.applicationsOfStudent(r.user.getId());
        StringBuilder sb = new StringBuilder("<div class='card'><h1>My Applications</h1>");
        if (list.isEmpty()) sb.append("<p>You have not applied to anything yet. <a href='/jobs'>Browse openings</a></p>");
        else {
            sb.append("<table><tr><th>Position</th><th>Progress</th><th>Interviews</th><th>Offer</th></tr>");
            for (Application a : list) {
                sb.append("<tr><td><a href='/job?id=").append(a.getPostingId()).append("'>").append(esc(a.getTitle()))
                        .append("</a><br><small>").append(esc(a.getCompany())).append("</small><br>");
                boolean withdrawn = a.getStatus().equals("WITHDRAWN");
                if (withdrawn) {
                    sb.append("<a class='btn' href='/job?id=").append(a.getPostingId()).append("'>Apply again</a>");
                } else {
                    sb.append("<form class='d' method='post' action='/withdraw' onsubmit=\"return confirm('Withdraw this application?')\">")
                            .append(hidden("id", a.getId())).append("<button class='grey'>Withdraw</button></form>");
                }
                sb.append("</td><td>").append(pipeline(a.getStatus())).append("</td><td>")
                        .append(a.getStatus().equals("REJECTED") || withdrawn ? "-" : interviewList(dao.getInterviews(a.getId())))
                        .append("</td><td>");
                String[] offer = withdrawn ? null : dao.getOffer(a.getId());
                if (offer != null && !a.getStatus().equals("REJECTED")) {
                    sb.append("<div class='offer'><b>Congratulations! Offer received</b><br>Start: ").append(esc(fmtDate(offer[0])))
                            .append("<br>Pay: ").append(esc(offer[1])).append("<br>Duration: ").append(esc(offer[2]))
                            .append("<br>Status: <b>").append(PortalDao.pretty(offer[3])).append("</b><br>")
                            .append("<a href='/offer/letter?id=").append(a.getId()).append("' target='_blank'>View offer letter</a>");
                    if (offer[3].equals("PENDING")) {
                        sb.append("<br><form class='d' method='post' action='/offer/respond'>").append(hidden("id", a.getId()))
                                .append(hidden("answer", "accept")).append("<button class='green'>Accept</button></form> ")
                                .append("<form class='d' method='post' action='/offer/respond'>").append(hidden("id", a.getId()))
                                .append(hidden("answer", "decline")).append("<button class='red'>Decline</button></form>");
                    }
                    sb.append("</div>");
                } else sb.append("-");
                sb.append("</td></tr>");
            }
            sb.append("</table>");
        }
        send(r, 200, layout(r, "My Applications", sb.append("</div>").toString()));
    }

    private static void withdraw(Req r) throws IOException {
        if (!require(r, "STUDENT")) return;
        redirect(r, "/applications", r.post() ? dao.withdrawApplication(r.getInt("id"), r.user.getId()) : null);
    }

    /** Any logged-in user can change their own password. */
    private static void changePassword(Req r) throws IOException {
        if (r.user == null) { redirect(r, "/login", "Please log in."); return; }
        if (r.post()) {
            String pw = r.get("newpw");
            if (!pw.equals(r.get("confirm"))) { redirect(r, "/password", "The two new passwords do not match."); return; }
            redirect(r, "/password", dao.changePassword(r.user.getId(), r.get("current"), pw));
            return;
        }
        String body = "<div class='card' style='max-width:440px;margin:auto'><h1>Change password</h1>"
                + "<p>" + esc(r.user.getName()) + " &middot; " + esc(r.user.getEmail()) + "</p><form method='post'>"
                + "<label>Current password</label><input name='current' type='password' required>"
                + "<label>New password (min 6 characters)</label><input name='newpw' type='password' minlength='6' required>"
                + "<label>Confirm new password</label><input name='confirm' type='password' minlength='6' required><br><br>"
                + "<button>Change password</button></form></div>";
        send(r, 200, layout(r, "Change password", body));
    }

    private static void respondToOffer(Req r) throws IOException {
        if (!require(r, "STUDENT")) return;
        if (!r.post()) { redirect(r, "/applications", null); return; }
        redirect(r, "/applications", dao.respondToOffer(r.getInt("id"), r.user.getId(), r.get("answer").equals("accept")));
    }

    /** Offer letter PDF, visible to the student it is addressed to and to the company that issued it. */
    private static void offerLetter(Req r) throws IOException {
        if (r.user == null) { redirect(r, "/login", "Please log in."); return; }
        String[] d = dao.offerLetterData(r.getInt("id"));
        boolean ok = d != null && switch (r.user.getRole()) {
            case "STUDENT" -> Integer.parseInt(d[7]) == r.user.getId();
            case "RECRUITER" -> Integer.parseInt(d[8]) == dao.companyIdOf(r.user.getId());
            default -> false;
        };
        if (!ok) { send(r, 404, layout(r, "Not found", "<div class='card'>No such offer letter.</div>")); return; }
        List<String> lines = List.of("Date: " + LocalDate.now(), "", "Dear " + d[0] + ",", "",
                "We are pleased to offer you the position of " + d[1] + " at " + d[2] + ".", "",
                "Start date: " + d[3], "Compensation: " + d[4], "Duration: " + d[5], "",
                "Offer status: " + PortalDao.pretty(d[6]), "",
                "Please accept or decline this offer through the Job & Internship Portal.", "", "Sincerely,", d[2]);
        sendPdf(r, Pdf.simple("OFFER LETTER", lines), "offer-letter.pdf");
    }

    private static void notifications(Req r) throws IOException {
        if (r.user == null) { redirect(r, "/login", "Please log in."); return; }
        List<String[]> list = dao.notificationsOf(r.user.getId());
        StringBuilder sb = new StringBuilder("<div class='card'><h1>Notifications</h1>");
        if (list.isEmpty()) sb.append("<p>Nothing here yet.</p>");
        else {
            sb.append("<table>");
            for (String[] n : list) {
                sb.append("<tr class='").append(n[2].equals("0") ? "unread" : "").append("'><td>").append(esc(n[0]))
                        .append("</td><td><small>").append(esc(fmtStamp(n[1]))).append("</small></td></tr>");
            }
            sb.append("</table>");
        }
        dao.markNotificationsRead(r.user.getId());
        send(r, 200, layout(r, "Notifications", sb.append("</div>").toString()));
    }

    private static void profile(Req r) throws IOException {
        if (!require(r, "STUDENT")) return;
        int uid = r.user.getId();
        if (r.post()) {
            Double cgpa = null;
            try {
                double v = Double.parseDouble(r.get("cgpa"));
                if (v >= 0 && v <= 10) cgpa = v;
            } catch (NumberFormatException ignored) { }
            if (cgpa == null) { redirect(r, "/profile", "CGPA must be a number between 0 and 10."); return; }
            String resume = dao.getProfile(uid)[5];
            String fileErr = null;
            if (r.fileData != null) {
                boolean pdf = r.fileData.length > 4 && r.fileData[0] == '%' && r.fileData[1] == 'P'
                        && r.fileData[2] == 'D' && r.fileData[3] == 'F';
                if (r.fileData.length > MAX_RESUME) fileErr = "Resume must be smaller than 2 MB.";
                else if (!pdf || !r.fileName.toLowerCase().endsWith(".pdf")) fileErr = "Resume must be a PDF file.";
                else {
                    Files.createDirectories(Path.of("uploads"));
                    Files.write(Path.of("uploads", uid + ".pdf"), r.fileData);
                    resume = "uploads/" + uid + ".pdf";
                }
            }
            dao.saveProfile(uid, r.get("phone"), r.get("college"), r.get("degree"),
                    r.getInt("year"), cgpa, r.get("skills"), resume);
            dao.saveProfileExtras(uid, cap(r.get("projects")), cap(r.get("certifications")), cap(r.get("experience")));
            if (fileErr != null) { redirect(r, "/profile", "Profile saved, but: " + fileErr); return; }
            redirect(r, "/profile", dao.isProfileComplete(uid)
                    ? "Profile saved. You can now apply to openings."
                    : "Profile saved, but it is not complete yet - fill in every required field and upload your resume.");
            return;
        }
        String[] f = dao.getProfile(uid);
        String[] x = dao.getProfileExtras(uid);
        boolean hasResume = !f[5].isBlank();
        String banner = dao.isProfileComplete(uid) ? ""
                : "<div class='msg'>Your profile is incomplete. Fill in every required field (*) and upload your resume (PDF) "
                + "before you can apply to openings.</div>";
        String body = "<div class='card' style='max-width:600px'><h1>My Profile</h1>" + banner + "<p>" + esc(r.user.getName())
                + " &middot; " + esc(r.user.getEmail()) + "</p><form method='post' enctype='multipart/form-data'>"
                + "<label>Phone *</label><input name='phone' required value='" + esc(f[0]) + "'>"
                + "<label>College *</label><input name='college' required value='" + esc(f[1]) + "'>"
                + "<label>Degree *</label><input name='degree' required value='" + esc(f[2]) + "'>"
                + "<label>Graduation year *</label><input name='year' type='number' min='1990' max='2100' required value='" + esc(f[3]) + "'>"
                + "<label>CGPA (out of 10) *</label><input name='cgpa' type='number' step='0.01' min='0' max='10' required value='" + esc(f[6]) + "'>"
                + "<label>Skills (comma separated, used for job matching) *</label><input name='skills' required value='" + esc(f[4]) + "'>"
                + "<label>Projects (optional)</label><textarea name='projects' rows='3'>" + esc(x[0]) + "</textarea>"
                + "<label>Certifications (optional)</label><textarea name='certifications' rows='2'>" + esc(x[1]) + "</textarea>"
                + "<label>Work experience (optional - leave blank if fresher)</label><textarea name='experience' rows='2'>" + esc(x[2]) + "</textarea>"
                + "<label>Resume / CV (PDF, max 2 MB) *</label>"
                + (hasResume ? "<p><a href='/resume?student=" + uid + "' target='_blank'>View current resume</a> "
                + "- upload a new file below to replace it.</p>" : "")
                + "<input type='file' name='resumeFile' accept='application/pdf,.pdf'" + (hasResume ? "" : " required") + "><br><br>"
                + "<button>Save profile</button></form></div>";
        send(r, 200, layout(r, "My Profile", body));
    }

    private static String cap(String s) {
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }

    /** Serves a student's resume to that student, an admin, or a recruiter they applied to. */
    private static void resume(Req r) throws IOException {
        if (r.user == null) { redirect(r, "/login", "Please log in."); return; }
        int sid = r.getInt("student");
        boolean ok = switch (r.user.getRole()) {
            case "STUDENT" -> sid == r.user.getId();
            case "ADMIN" -> true;
            default -> dao.companyHasApplicant(dao.companyIdOf(r.user.getId()), sid);
        };
        if (!ok) { send(r, 403, layout(r, "Forbidden", "<p>You cannot view this resume.</p>")); return; }
        String link = dao.getProfile(sid)[5];
        if (link.matches("uploads/\\d+\\.pdf") && Files.exists(Path.of(link))) {
            sendPdf(r, Files.readAllBytes(Path.of(link)), "resume.pdf");
        } else if (link.startsWith("http://") || link.startsWith("https://")) {
            redirect(r, link, null);   // older entries stored a plain link
        } else {
            send(r, 404, layout(r, "No resume", "<p>This student has not uploaded a resume.</p>"));
        }
    }

    // ---------- recruiter ----------

    private static void companyEdit(Req r) throws IOException {
        if (!require(r, "RECRUITER")) return;
        int cid = dao.companyIdOf(r.user.getId());
        if (r.post()) {
            String website = r.get("website");
            if (!website.isEmpty() && !website.startsWith("http://") && !website.startsWith("https://")) {
                redirect(r, "/company/edit", "Website must start with http:// or https://");
                return;
            }
            dao.saveCompanyProfile(cid, r.get("industry"), r.get("size"), website, r.get("location"), cap(r.get("description")));
            redirect(r, "/company?id=" + cid, "Company profile saved.");
            return;
        }
        String[] c = dao.getCompany(cid);
        String body = "<div class='card' style='max-width:600px'><h1>Company Profile</h1>"
                + "<p>" + esc(c[0]) + (c[6].equals("1") ? " <span class='ver'>&#10003; Verified</span>" : "")
                + " &middot; <a href='/company?id=" + cid + "'>View public page</a></p><form method='post'>"
                + "<label>Industry</label><input name='industry' value='" + esc(c[1]) + "' placeholder='e.g. Software &amp; IT'>"
                + "<label>Company size</label><select name='size'>" + sizeOptions(c[2]) + "</select>"
                + "<label>Website</label><input name='website' value='" + esc(c[3]) + "' placeholder='https://...'>"
                + "<label>Location</label><input name='location' value='" + esc(c[4]) + "'>"
                + "<label>About the company</label><textarea name='description' rows='5'>" + esc(c[5]) + "</textarea><br><br>"
                + "<button>Save</button></form></div>";
        send(r, 200, layout(r, "Company Profile", body));
    }

    private static String sizeOptions(String current) {
        StringBuilder sb = new StringBuilder("<option value=''>Not specified</option>");
        for (String s : new String[]{"1-10", "11-50", "51-200", "201-500", "500+"}) {
            sb.append("<option").append(s.equals(current) ? " selected" : "").append(">").append(s).append("</option>");
        }
        return sb.toString();
    }

    /** The posting form, empty for a new posting or filled in (p) for editing. */
    private static String jobForm(Internship p, String heading, String button, String extraHidden) {
        String type = p == null ? "JOB" : p.getType();
        String mode = p == null ? "ONSITE" : p.getWorkMode();
        String exp = p == null ? "" : p.getExperience();
        StringBuilder expOptions = new StringBuilder("<option value=''>Not specified</option>");
        for (String e : new String[]{"Fresher", "0-1 years", "1-3 years", "3+ years"}) {
            expOptions.append("<option").append(sel(exp, e)).append(">").append(e).append("</option>");
        }
        return "<div class='card' style='max-width:640px'><h1>" + heading + "</h1><form method='post'>" + extraHidden
                + "<label>Type</label><select name='type'><option value='JOB'" + sel(type, "JOB") + ">Job</option>"
                + "<option value='INTERNSHIP'" + sel(type, "INTERNSHIP") + ">Internship</option></select>"
                + "<label>Title</label><input name='title' required value='" + esc(p == null ? "" : p.getTitle()) + "'>"
                + "<label>Location</label><input name='location' required value='" + esc(p == null ? "" : p.getLocation()) + "'>"
                + "<label>Work mode</label><select name='mode'><option value='ONSITE'" + sel(mode, "ONSITE") + ">On-site</option>"
                + "<option value='REMOTE'" + sel(mode, "REMOTE") + ">Remote</option>"
                + "<option value='HYBRID'" + sel(mode, "HYBRID") + ">Hybrid</option></select>"
                + "<label>Salary / Stipend (e.g. 6 LPA or 10000/month)</label><input name='pay' value='" + esc(p == null ? "" : dash(p.getPay())) + "'>"
                + "<label>Duration (internships, e.g. 6 months)</label><input name='duration' value='" + esc(p == null ? "" : p.getDuration()) + "'>"
                + "<label>Required skills (comma separated - used for matching)</label><input name='skills' value='" + esc(p == null ? "" : dash(p.getSkills())) + "'>"
                + "<label>Preferred skills (comma separated, optional)</label><input name='preferred' value='" + esc(p == null ? "" : p.getPreferred()) + "'>"
                + "<label>Required education (e.g. B.Tech / BCA / MCA)</label><input name='education' value='" + esc(p == null ? "" : p.getEducation()) + "'>"
                + "<label>Experience</label><select name='experience'>" + expOptions + "</select>"
                + "<label>Number of openings</label><input name='openings' type='number' min='1' value='" + (p == null || p.getOpenings() <= 0 ? "" : p.getOpenings()) + "'>"
                + "<label>Description</label><textarea name='description' rows='4'>" + esc(p == null ? "" : dash(p.getDescription())) + "</textarea>"
                + "<label>Application deadline</label><input name='deadline' type='date' value='" + esc(p == null ? "" : dash(p.getDeadline())) + "'><br><br>"
                + "<button>" + button + "</button></form></div>";
    }

    /** Edit one of the recruiter's own postings. */
    private static void editPosting(Req r) throws IOException {
        if (!require(r, "RECRUITER")) return;
        int cid = dao.companyIdOf(r.user.getId());
        int id = r.getInt("id");
        if (!dao.isCompanyVerified(cid)) {
            redirect(r, "/postings", "Your company is not verified. You can edit openings once an admin verifies it.");
            return;
        }
        Internship p = dao.getPosting(id);
        if (p == null || p.getCompanyId() != cid) {
            redirect(r, "/postings", "No posting with ID " + id + " in your company.");
            return;
        }
        if (r.post()) {
            String type = r.get("type").equals("INTERNSHIP") ? "INTERNSHIP" : "JOB";
            String mode = List.of("ONSITE", "REMOTE", "HYBRID").contains(r.get("mode")) ? r.get("mode") : "ONSITE";
            if (r.get("title").isEmpty() || r.get("location").isEmpty()) {
                redirect(r, "/edit?id=" + id, "Title and location are required.");
                return;
            }
            String res = dao.updatePosting(id, cid, r.get("title"), type, r.get("location"), mode, r.get("pay"),
                    r.get("skills"), r.get("preferred"), r.get("description"), r.get("deadline"), r.get("duration"),
                    r.get("education"), r.get("experience"), r.getInt("openings"));
            redirect(r, res.equals("Posting updated.") ? "/postings" : "/edit?id=" + id, res);
            return;
        }
        send(r, 200, layout(r, "Edit posting", jobForm(p, "Edit posting", "Save changes", hidden("id", id))));
    }

    private static void reopenPosting(Req r) throws IOException {
        if (!require(r, "RECRUITER")) return;
        int cid = dao.companyIdOf(r.user.getId());
        if (!r.post()) { redirect(r, "/postings", null); return; }
        if (!dao.isCompanyVerified(cid)) {
            redirect(r, "/postings", "Your company is not verified. You can reopen openings once an admin verifies it.");
            return;
        }
        redirect(r, "/postings", dao.reopenPosting(r.getInt("id"), cid));
    }

    private static void postJob(Req r) throws IOException {
        if (!require(r, "RECRUITER")) return;
        int cid = dao.companyIdOf(r.user.getId());
        if (!dao.isCompanyVerified(cid)) {
            redirect(r, "/postings", "Your company is not verified. You can post openings once an admin verifies it.");
            return;
        }
        if (r.post()) {
            String type = r.get("type").equals("INTERNSHIP") ? "INTERNSHIP" : "JOB";
            String mode = List.of("ONSITE", "REMOTE", "HYBRID").contains(r.get("mode")) ? r.get("mode") : "ONSITE";
            if (r.get("title").isEmpty() || r.get("location").isEmpty()) {
                redirect(r, "/post", "Title and location are required.");
                return;
            }
            String res = dao.postJob(cid, r.get("title"), type, r.get("location"), mode, r.get("pay"), r.get("skills"),
                    r.get("preferred"), r.get("description"), r.get("deadline"), r.get("duration"), r.get("education"),
                    r.get("experience"), r.getInt("openings"));
            redirect(r, res.equals("Posted.") ? "/postings" : "/post", res);
            return;
        }
        String body = jobForm(null, "Post a Job or Internship", "Publish", "");
        send(r, 200, layout(r, "Post Opening", body));
    }

    private static void myPostings(Req r) throws IOException {
        if (!require(r, "RECRUITER")) return;
        int cid = dao.companyIdOf(r.user.getId());
        int[] st = dao.recruiterStats(cid);
        List<Internship> list = dao.postingsOfCompany(cid);
        StringBuilder sb = new StringBuilder();
        if (!dao.isCompanyVerified(cid)) {
            sb.append("<div class='warn'><b>Verification pending.</b> An admin must verify your company before you can post openings.</div>");
        }
        sb.append("<div class='card'><h1>Recruiter Dashboard</h1><div class='stats'>")
                .append(stat("Active postings", st[0])).append(stat("Applications", st[1])).append(stat("Shortlisted", st[2]))
                .append(stat("Interviewed", st[3])).append(stat("Offers made", st[4])).append(stat("Offers accepted", st[5]))
                .append("</div></div>");

        // analytics: hiring funnel, per-posting numbers, most common applicant skills
        List<String[]> per = dao.perPostingStats(cid);
        Map<String, Integer> skillCounts = new LinkedHashMap<>();
        Map<String, String> shown = new HashMap<>();   // lower-case key -> spelling to display
        Set<Integer> seen = new HashSet<>();
        for (Application a : dao.applicantsForCompany(cid)) {
            if (a.getStatus().equals("WITHDRAWN") || !seen.add(a.getStudentId())) continue;
            for (String s : Match.skillList(dao.getProfile(a.getStudentId())[4])) {
                String key = s.toLowerCase(Locale.ROOT);
                skillCounts.merge(key, 1, Integer::sum);
                shown.putIfAbsent(key, s);
            }
        }
        List<Map.Entry<String, Integer>> top = new ArrayList<>(skillCounts.entrySet());
        top.sort((a, b) -> b.getValue() - a.getValue());
        sb.append("<div class='two'><div class='card'><h2>Hiring funnel</h2><table>");
        String[] labels = {"Applications", "Interviewed", "Offers made", "Offers accepted"};
        int[] vals = {st[1], st[3], st[4], st[5]};
        for (int i = 0; i < labels.length; i++) {
            sb.append("<tr><td>").append(labels[i]).append("</td><td>").append(bar(vals[i], st[1])).append("</td><td>")
                    .append(vals[i]).append("</td></tr>");
        }
        sb.append("</table></div><div class='card'><h2>Most common applicant skills</h2>");
        if (top.isEmpty()) sb.append("<p>No applicants yet.</p>");
        else {
            sb.append("<table>");
            int max = top.get(0).getValue();
            for (Map.Entry<String, Integer> e : top.subList(0, Math.min(6, top.size()))) {
                sb.append("<tr><td>").append(esc(shown.get(e.getKey()))).append("</td><td>")
                        .append(bar(e.getValue(), max)).append("</td><td>").append(e.getValue()).append("</td></tr>");
            }
            sb.append("</table>");
        }
        sb.append("</div></div>");
        sb.append("<div class='card'><h2>Applications per posting</h2>");
        if (per.isEmpty()) sb.append("<p>Nothing posted yet. <a href='/post'>Post an opening</a></p>");
        else {
            int max = 1;
            for (String[] p : per) max = Math.max(max, Integer.parseInt(p[2]));
            sb.append("<table><tr><th>Posting</th><th>Applications</th><th></th><th>Shortlisted</th><th>In interview</th><th>Selected</th></tr>");
            for (String[] p : per) {
                sb.append("<tr><td>").append(esc(p[1])).append("</td><td>").append(p[2]).append("</td><td>")
                        .append(bar(Integer.parseInt(p[2]), max)).append("</td><td>").append(p[3]).append("</td><td>")
                        .append(p[4]).append("</td><td>").append(p[5]).append("</td></tr>");
            }
            sb.append("</table>");
        }
        sb.append("</div><div class='card'><h2>My Postings</h2>");
        if (list.isEmpty()) sb.append("<p>No postings.</p>");
        else {
            sb.append("<table><tr><th>ID</th><th>Type</th><th>Title</th><th>Location</th><th>Pay</th><th>Deadline</th><th>Status</th><th></th></tr>");
            for (Internship i : list) {
                sb.append("<tr><td>").append(i.getId()).append("</td><td>").append(i.getType()).append("</td><td>")
                        .append(esc(i.getTitle())).append("</td><td>").append(esc(i.getLocation())).append("</td><td>")
                        .append(esc(i.getPay())).append("</td><td>").append(esc(fmtDate(i.getDeadline()))).append("</td><td>")
                        .append(i.getStatus()).append("</td><td>")
                        .append("<a class='btn grey' href='/edit?id=").append(i.getId()).append("'>Edit</a> ");
                if (i.getStatus().equals("OPEN")) {
                    sb.append("<form class='d' method='post' action='/close'>").append(hidden("id", i.getId()))
                            .append("<button class='red'>Close</button></form>");
                } else {
                    sb.append("<form class='d' method='post' action='/reopen'>").append(hidden("id", i.getId()))
                            .append("<button class='green'>Reopen</button></form>");
                }
                sb.append("</td></tr>");
            }
            sb.append("</table>");
        }
        send(r, 200, layout(r, "Dashboard", sb.append("</div>").toString()));
    }

    private static void closePosting(Req r) throws IOException {
        if (!require(r, "RECRUITER")) return;
        redirect(r, "/postings", r.post() ? dao.closePosting(r.getInt("id"), dao.companyIdOf(r.user.getId())) : null);
    }

    private record Row(Application a, String[] profile, Match match, Internship posting) {}

    private static List<Row> applicantRows(int companyId) {
        Map<Integer, Internship> posts = new HashMap<>();
        for (Internship i : dao.postingsOfCompany(companyId)) posts.put(i.getId(), i);
        List<Row> rows = new ArrayList<>();
        for (Application a : dao.applicantsForCompany(companyId)) {
            String[] f = dao.getProfile(a.getStudentId());
            Internship p = posts.get(a.getPostingId());
            rows.add(new Row(a, f, Match.of(f[4], p == null ? "" : p.getSkills(), p == null ? "" : p.getPreferred()), p));
        }
        rows.sort(Comparator.comparingInt((Row x) -> -x.match().percent));
        return rows;
    }

    private static void applicants(Req r) throws IOException {
        if (!require(r, "RECRUITER")) return;
        int cid = dao.companyIdOf(r.user.getId());
        List<Row> all = applicantRows(cid);

        int fPosting = r.getInt("posting"), fMatch = r.getInt("minmatch");
        String fStatus = r.get("status"), fSkill = r.get("skill").toLowerCase(Locale.ROOT);
        double fCgpa = -1;
        try { if (!r.get("mincgpa").isEmpty()) fCgpa = Double.parseDouble(r.get("mincgpa")); } catch (NumberFormatException ignored) { }
        final double minCgpa = fCgpa;
        List<Row> rows = new ArrayList<>();
        for (Row x : all) {
            if (fPosting > 0 && x.a().getPostingId() != fPosting) continue;
            if (!fStatus.isEmpty() && !x.a().getStatus().equals(fStatus)) continue;
            if (fMatch > 0 && x.match().percent < fMatch) continue;
            if (!fSkill.isEmpty() && !x.profile()[4].toLowerCase(Locale.ROOT).contains(fSkill)) continue;
            if (minCgpa >= 0) {
                try { if (Double.parseDouble(x.profile()[6]) < minCgpa) continue; } catch (NumberFormatException e) { continue; }
            }
            rows.add(x);
        }

        StringBuilder sb = new StringBuilder("<div class='card'><h1>Applicants</h1>")
                .append("<form class='inline' method='get' action='/applicants'><div class='grid'>")
                .append("<select name='posting'><option value=''>All postings</option>");
        for (Internship i : dao.postingsOfCompany(cid)) {
            sb.append("<option value='").append(i.getId()).append("'").append(i.getId() == fPosting ? " selected" : "")
                    .append(">").append(esc(i.getTitle())).append("</option>");
        }
        sb.append("</select><select name='status'><option value=''>Any status</option>");
        for (String s : new String[]{"APPLIED", "UNDER_REVIEW", "SHORTLISTED", "INTERVIEW", "SELECTED", "REJECTED", "WITHDRAWN"}) {
            sb.append("<option value='").append(s).append("'").append(s.equals(fStatus) ? " selected" : "").append(">")
                    .append(PortalDao.pretty(s)).append("</option>");
        }
        sb.append("</select><input name='skill' placeholder='Has skill...' value='").append(esc(r.get("skill"))).append("'>")
                .append("<input name='mincgpa' type='number' step='0.1' min='0' max='10' placeholder='Min CGPA' value='")
                .append(esc(r.get("mincgpa"))).append("'>")
                .append("<input name='minmatch' type='number' min='0' max='100' placeholder='Min match %' value='")
                .append(fMatch > 0 ? fMatch : "").append("'>")
                .append("<button>Filter</button></div></form>")
                .append("<p><b>").append(all.size()).append("</b> applicants &rarr; <b>").append(rows.size())
                .append("</b> candidates &nbsp; <small>Ranked by skill match.</small></p>");
        if (rows.isEmpty()) sb.append("<p>No candidates.</p>");
        else {
            sb.append("<table><tr><th>Candidate</th><th>College / CGPA</th><th>Match</th><th>Position</th><th>Status</th><th>Actions</th></tr>");
            for (Row row : rows) {
                Application a = row.a();
                String[] f = row.profile();
                sb.append("<tr><td><a href='/candidate?id=").append(a.getId()).append("'>").append(esc(a.getStudentName()))
                        .append("</a><br><small>").append(esc(f[4])).append("</small></td><td>").append(esc(f[1])).append("<br>")
                        .append("<small>CGPA: ").append(f[6].isBlank() ? "-" : esc(f[6])).append("</small></td><td>")
                        .append(row.match().hasRequirements() ? matchBadge(row.match().percent) : "-")
                        .append(row.match().missing.isEmpty() ? "" : "<br><small class='no'>missing: "
                                + esc(String.join(", ", row.match().missing)) + "</small>")
                        .append("</td><td>").append(esc(a.getTitle())).append("</td><td>")
                        .append(a.getStatus().equals("REJECTED") ? "<span class='tag rej'>Rejected</span>"
                                : "<span class='tag'>" + PortalDao.pretty(a.getStatus()) + "</span>")
                        .append("</td><td>")
                        .append(a.getStatus().equals("WITHDRAWN") ? "" : statusButton(a.getId(), "SHORTLISTED", "Shortlist", "green", false))
                        .append(a.getStatus().equals("WITHDRAWN") ? "" : statusButton(a.getId(), "REJECTED", "Reject", "red", false))
                        .append("<a class='btn grey' href='/candidate?id=").append(a.getId()).append("'>View</a></td></tr>");
            }
            sb.append("</table>");
        }
        send(r, 200, layout(r, "Applicants", sb.append("</div>").toString()));
    }

    private static String statusButton(int appId, String status, String label, String cls, boolean backToCandidate) {
        return "<form class='d' method='post' action='/status'>" + hidden("id", appId) + hidden("status", status)
                + (backToCandidate ? hidden("ret", "c") : "") + "<button class='" + cls + "'>" + label + "</button></form> ";
    }

    private static void candidate(Req r) throws IOException {
        if (!require(r, "RECRUITER")) return;
        int cid = dao.companyIdOf(r.user.getId());
        Row row = null;
        for (Row x : applicantRows(cid)) if (x.a().getId() == r.getInt("id")) row = x;
        if (row == null) { redirect(r, "/applicants", "Application not found."); return; }
        Application a = row.a();
        String[] f = row.profile();
        String[] x = dao.getProfileExtras(a.getStudentId());
        Match m = row.match();
        Internship p = row.posting();

        StringBuilder sb = new StringBuilder("<div class='card'><h1>").append(esc(a.getStudentName())).append("</h1><p>")
                .append(esc(a.getEmail())).append(" &middot; ").append(esc(f[0])).append("</p><p>Applied for <b>")
                .append(esc(a.getTitle())).append("</b></p>")
                .append(a.getStatus().equals("WITHDRAWN") ? "<span class='tag'>Withdrawn by the student</span>" : pipeline(a.getStatus()))
                .append("<br>");
        sb.append("<h3>Match: ").append(m.hasRequirements() ? matchBadge(m.percent) : "-").append("</h3>");
        if (m.hasRequirements()) {
            int reqTotal = m.matched.size() + m.missing.size();
            if (reqTotal > 0) sb.append("<p>Required skills: <b>").append(m.matched.size()).append("/").append(reqTotal).append("</b> ");
            for (String s : m.matched) sb.append("<span class='ok'>&#10003; ").append(esc(s)).append("</span> &nbsp; ");
            for (String s : m.missing) sb.append("<span class='no'>&#10007; ").append(esc(s)).append("</span> &nbsp; ");
            sb.append("</p>");
            int prefTotal = m.prefMatched.size() + m.prefMissing.size();
            if (prefTotal > 0) {
                sb.append("<p>Preferred skills: <b>").append(m.prefMatched.size()).append("/").append(prefTotal).append("</b> ");
                for (String s : m.prefMatched) sb.append("<span class='ok'>&#10003; ").append(esc(s)).append("</span> &nbsp; ");
                for (String s : m.prefMissing) sb.append("<span class='no'>&#10007; ").append(esc(s)).append("</span> &nbsp; ");
                sb.append("</p>");
            }
            sb.append("<p><small>Score = share of required skills the candidate lists"
                    + (prefTotal > 0 ? " (80%) plus preferred skills (20%)" : "") + ".</small></p>");
        }
        sb.append("<h3>Education</h3><p>").append(esc(f[2])).append(" &middot; ").append(esc(f[1])).append(" &middot; ")
                .append(esc(f[3])).append(" &middot; CGPA ").append(f[6].isBlank() ? "-" : esc(f[6])).append("</p>")
                .append("<h3>Skills</h3><p>").append(esc(f[4])).append("</p>")
                .append("<h3>Projects</h3><p style='white-space:pre-wrap'>").append(x[0].isBlank() ? "-" : esc(x[0])).append("</p>")
                .append("<h3>Certifications</h3><p style='white-space:pre-wrap'>").append(x[1].isBlank() ? "-" : esc(x[1])).append("</p>")
                .append("<h3>Experience</h3><p style='white-space:pre-wrap'>").append(x[2].isBlank() ? "Fresher" : esc(x[2])).append("</p>")
                .append(f[5].isBlank() ? "" : "<p><a class='btn' href='/resume?student=" + a.getStudentId() + "' target='_blank'>View resume</a></p>")
                .append("</div>");

        if (a.getStatus().equals("WITHDRAWN")) {
            sb.append("<div class='warn'>The student withdrew this application, so it can no longer be reviewed, "
                    + "scheduled for interview or sent an offer. If they apply again it will return to Applied.</div>");
            send(r, 200, layout(r, a.getStudentName(), sb.toString()));
            return;
        }
        sb.append("<div class='card'><h2>Review</h2>")
                .append(statusButton(a.getId(), "UNDER_REVIEW", "Under review", "grey", true))
                .append(statusButton(a.getId(), "SHORTLISTED", "Shortlist", "green", true))
                .append(statusButton(a.getId(), "REJECTED", "Reject", "red", true)).append("</div>");

        sb.append("<div class='two'><div class='card'><h2>Interview rounds</h2>")
                .append(interviewList(dao.getInterviews(a.getId())))
                .append("<h3>Schedule a round</h3><form method='post' action='/interview'>").append(hidden("id", a.getId()))
                .append(hidden("ret", "c")).append("<label>Round</label><select name='round'>");
        for (String rd : INTERVIEW_ROUNDS) sb.append("<option>").append(rd).append("</option>");
        sb.append("</select><label>Type</label><select name='mode'><option value='ONLINE'>Online</option>"
                        + "<option value='OFFLINE'>Offline</option></select>"
                        + "<label>Date</label><input type='date' name='date' required>"
                        + "<label>Time</label><input type='time' name='time' required>"
                        + "<label>Meeting link or address</label><input name='link'><br><br><button>Schedule</button></form></div>");

        String[] offer = dao.getOffer(a.getId());
        sb.append("<div class='card'><h2>Offer</h2>");
        if (offer != null) {
            sb.append("<div class='offer'>Start: ").append(esc(fmtDate(offer[0]))).append("<br>Pay: ").append(esc(offer[1]))
                    .append("<br>Duration: ").append(esc(offer[2])).append("<br>Status: <b>")
                    .append(PortalDao.pretty(offer[3])).append("</b><br><a href='/offer/letter?id=").append(a.getId())
                    .append("' target='_blank'>View offer letter</a></div>");
        }
        if (offer == null || !offer[3].equals("ACCEPTED")) {
            sb.append("<h3>").append(offer == null ? "Select candidate and send offer" : "Update offer")
                    .append("</h3><form method='post' action='/offer'>").append(hidden("id", a.getId()))
                    .append("<label>Start date</label><input type='date' name='start' required>")
                    .append("<label>Stipend / salary</label><input name='pay' value='").append(esc(p == null ? "" : p.getPay())).append("'>")
                    .append("<label>Duration</label><input name='duration' value='").append(esc(p == null ? "" : p.getDuration())).append("'>")
                    .append("<br><br><button class='green'>Generate offer</button></form>");
        }
        sb.append("</div></div>");
        send(r, 200, layout(r, a.getStudentName(), sb.toString()));
    }

    private static final List<String> INTERVIEW_ROUNDS =
            List.of("HR Round", "Technical Round", "Managerial Round", "Final Round");

    private static String backTo(Req r) {
        return r.get("ret").equals("c") ? "/candidate?id=" + r.getInt("id") : "/applicants";
    }

    private static void updateStatus(Req r) throws IOException {
        if (!require(r, "RECRUITER")) return;
        String s = r.get("status");
        if (!r.post() || !List.of("UNDER_REVIEW", "SHORTLISTED", "REJECTED").contains(s)) {
            redirect(r, backTo(r), "Invalid status.");
            return;
        }
        redirect(r, backTo(r), dao.updateStatus(r.getInt("id"), dao.companyIdOf(r.user.getId()), s));
    }

    private static void scheduleInterview(Req r) throws IOException {
        if (!require(r, "RECRUITER")) return;
        if (!r.post() || !INTERVIEW_ROUNDS.contains(r.get("round"))) { redirect(r, backTo(r), "Invalid request."); return; }
        String link = r.get("link");
        if (link.length() > 250) link = link.substring(0, 250);
        redirect(r, backTo(r), dao.scheduleInterview(r.getInt("id"), dao.companyIdOf(r.user.getId()),
                r.get("round"), r.get("mode"), r.get("date"), r.get("time"), link));
    }

    private static void makeOffer(Req r) throws IOException {
        if (!require(r, "RECRUITER")) return;
        String back = "/candidate?id=" + r.getInt("id");
        if (!r.post()) { redirect(r, back, null); return; }
        redirect(r, back, dao.makeOffer(r.getInt("id"), dao.companyIdOf(r.user.getId()),
                r.get("start"), r.get("pay"), r.get("duration")));
    }

    // ---------- admin ----------

    private static void admin(Req r) throws IOException {
        if (!require(r, "ADMIN")) return;
        StringBuilder sb = new StringBuilder("<div class='card'><h1>Admin Dashboard</h1><p>")
                .append(esc(dao.stats())).append("</p><p><a class='btn' href='/admin/reports'>Review reported postings (")
                .append(dao.openReportCount()).append(" open)</a></p></div>");
        sb.append("<div class='card'><h2>Awaiting approval</h2><p><small>Approving a recruiter also verifies their company, "
                        + "which lets them post openings.</small></p>")
                .append(userTable(dao.listUsers("PENDING"))).append("</div>");
        sb.append("<div class='card'><h2>Companies</h2>").append(companyTable(dao.listCompanies())).append("</div>");
        sb.append("<div class='card'><h2>All users</h2>").append(userTable(dao.listUsers(null))).append("</div>");
        send(r, 200, layout(r, "Admin", sb.toString()));
    }

    private static String companyTable(List<String[]> companies) {
        if (companies.isEmpty()) return "<p>None.</p>";
        StringBuilder sb = new StringBuilder("<table><tr><th>ID</th><th>Company</th><th>Recruiter</th><th>Verified</th><th></th></tr>");
        for (String[] c : companies) {
            boolean v = c[3].equals("1");
            sb.append("<tr><td>").append(c[0]).append("</td><td><a href='/company?id=").append(c[0]).append("'>")
                    .append(esc(c[1])).append("</a></td><td>").append(esc(c[2]))
                    .append("</td><td>").append(v ? "<span class='ver'>&#10003; Verified</span>" : "No")
                    .append("</td><td><form class='d' method='post' action='/admin/company'>").append(hidden("id", c[0]))
                    .append(hidden("verified", v ? "0" : "1"))
                    .append(v ? "<button class='grey'>Remove verification</button>" : "<button class='green'>Verify</button>")
                    .append("</form></td></tr>");
        }
        return sb.append("</table>").toString();
    }

    private static String userTable(List<User> users) {
        if (users.isEmpty()) return "<p>None.</p>";
        StringBuilder sb = new StringBuilder("<table><tr><th>ID</th><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th></th></tr>");
        for (User u : users) {
            sb.append("<tr><td>").append(u.getId()).append("</td><td>").append(esc(u.getName())).append("</td><td>")
                    .append(esc(u.getEmail())).append("</td><td>").append(u.getRole()).append("</td><td>")
                    .append(u.getStatus()).append("</td><td>");
            if (!u.getRole().equals("ADMIN")) {
                sb.append(statusForm(u.getId(), "ACTIVE", "Approve", "green"))
                        .append(statusForm(u.getId(), "BLOCKED", "Block", "red"))
                        .append("<form class='d inline' method='post' action='/admin/password'>").append(hidden("id", u.getId()))
                        .append("<input name='pw' placeholder='New password' minlength='6' required style='width:120px'> ")
                        .append("<button class='grey'>Reset password</button></form>");
            }
            sb.append("</td></tr>");
        }
        return sb.append("</table>").toString();
    }

    private static String statusForm(int id, String status, String label, String cls) {
        return "<form class='d' method='post' action='/admin/user'>" + hidden("id", id) + hidden("status", status)
                + "<button class='" + cls + "'>" + label + "</button></form> ";
    }

    private static void adminUser(Req r) throws IOException {
        if (!require(r, "ADMIN")) return;
        String s = r.get("status");
        if (!r.post() || !(s.equals("ACTIVE") || s.equals("BLOCKED"))) { redirect(r, "/admin", "Invalid request."); return; }
        redirect(r, "/admin", dao.setUserStatus(r.getInt("id"), s));
    }

    private static void adminResetPassword(Req r) throws IOException {
        if (!require(r, "ADMIN")) return;
        if (!r.post()) { redirect(r, "/admin", null); return; }
        redirect(r, "/admin", dao.resetPassword(r.getInt("id"), r.get("pw")));
    }

    private static void adminCompany(Req r) throws IOException {
        if (!require(r, "ADMIN")) return;
        if (!r.post()) { redirect(r, "/admin", null); return; }
        redirect(r, "/admin", dao.setCompanyVerified(r.getInt("id"), r.get("verified").equals("1")));
    }

    private static void adminPostings(Req r) throws IOException {
        if (!require(r, "ADMIN")) return;
        List<Internship> list = dao.allPostings();
        StringBuilder sb = new StringBuilder("<div class='card'><h1>All Postings</h1>");
        if (list.isEmpty()) sb.append("<p>No postings.</p>");
        else {
            sb.append("<table><tr><th>ID</th><th>Type</th><th>Title</th><th>Company</th><th>Status</th><th></th></tr>");
            for (Internship i : list) {
                sb.append("<tr><td>").append(i.getId()).append("</td><td>").append(i.getType()).append("</td><td>")
                        .append(esc(i.getTitle())).append("</td><td>").append(esc(i.getCompany())).append("</td><td>")
                        .append(i.getStatus()).append("</td><td><form class='d' method='post' action='/admin/delete'>")
                        .append(hidden("id", i.getId())).append("<button class='red'>Delete</button></form></td></tr>");
            }
            sb.append("</table>");
        }
        send(r, 200, layout(r, "All Postings", sb.append("</div>").toString()));
    }

    private static void adminDelete(Req r) throws IOException {
        if (!require(r, "ADMIN")) return;
        redirect(r, "/admin/postings", r.post() ? dao.deletePosting(r.getInt("id")) : null);
    }

    private static void adminReports(Req r) throws IOException {
        if (!require(r, "ADMIN")) return;
        List<String[]> list = dao.listReports();
        StringBuilder sb = new StringBuilder("<div class='card'><h1>Reported Postings</h1>");
        if (list.isEmpty()) sb.append("<p>No reports.</p>");
        else {
            sb.append("<table><tr><th>Risk</th><th>Posting</th><th>Company</th><th>Reason</th><th>Reported by</th><th>Status</th><th>Action</th></tr>");
            for (String[] x : list) {
                boolean open = x[7].equals("OPEN");
                sb.append("<tr><td><span class='tag risk-").append(x[8]).append("'>").append(x[8]).append("</span></td><td>")
                        .append(esc(x[2])).append("</td><td>").append(esc(x[3])).append("</td><td>").append(esc(x[5]))
                        .append(x[6].equals("-") ? "" : "<br><small>&ldquo;" + esc(x[6]) + "&rdquo;</small>")
                        .append("</td><td>").append(esc(x[4])).append("</td><td>").append(PortalDao.pretty(x[7])).append("</td><td>");
                if (open) {
                    sb.append(reportForm(x[0], "REMOVE", "Remove job", "red")).append(reportForm(x[0], "WARN", "Warn company", "grey"))
                            .append(reportForm(x[0], "KEEP", "Keep job", "green"));
                }
                sb.append("</td></tr>");
            }
            sb.append("</table>");
        }
        send(r, 200, layout(r, "Reports", sb.append("</div>").toString()));
    }

    private static String reportForm(String id, String action, String label, String cls) {
        return "<form class='d' method='post' action='/admin/report'>" + hidden("id", id) + hidden("action", action)
                + "<button class='" + cls + "'>" + label + "</button></form> ";
    }

    private static void adminReportAction(Req r) throws IOException {
        if (!require(r, "ADMIN")) return;
        if (!r.post()) { redirect(r, "/admin/reports", null); return; }
        redirect(r, "/admin/reports", dao.resolveReport(r.getInt("id"), r.get("action")));
    }
}
