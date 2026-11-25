/**
 * Fills the database with demo data. Safe to run more than once (does nothing if postings already exist).
 * Run: java -cp "out;lib\*" SeedData
 * All demo accounts use the password: password123
 */
public class SeedData {
    private static final String PW = "password123";

    /** Writes a small real PDF to uploads/<id>.pdf and returns the path stored in the profile. */
    private static String demoResume(int userId, String text) {
        String[] objs = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 5 0 R /Resources << /Font << /F1 4 0 R >> >> >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                null};
        String stream = "BT /F1 16 Tf 72 720 Td (Resume: " + text + ") Tj ET";
        objs[4] = "<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream";
        StringBuilder sb = new StringBuilder("%PDF-1.4\n");
        int[] off = new int[5];
        for (int k = 0; k < 5; k++) {
            off[k] = sb.length();
            sb.append(k + 1).append(" 0 obj\n").append(objs[k]).append("\nendobj\n");
        }
        int xref = sb.length();
        sb.append("xref\n0 6\n0000000000 65535 f \n");
        for (int o : off) sb.append(String.format("%010d 00000 n \n", o));
        sb.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("uploads"));
            java.nio.file.Files.write(java.nio.file.Path.of("uploads", userId + ".pdf"),
                    sb.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        } catch (java.io.IOException e) {
            throw new RuntimeException("Could not write demo resume: " + e.getMessage(), e);
        }
        return "uploads/" + userId + ".pdf";
    }

    public static void main(String[] args) {
        PortalDao dao = new PortalDao();
        if (!dao.allPostings().isEmpty()) {
            System.out.println("Database already has postings - skipping sample data.");
            return;
        }

        String[][] recruiters = {
                {"Neha Sharma", "neha@techcorp.com", "TechCorp Solutions"},
                {"Rahul Verma", "rahul@infonova.com", "InfoNova Systems"},
                {"Priya Nair", "priya@greenleaf.com", "GreenLeaf Analytics"},
        };
        for (String[] r : recruiters) {
            dao.register(r[0], r[1], PW, "RECRUITER", r[2]);
            dao.setUserStatus(dao.login(r[1], PW).getId(), "ACTIVE");
        }
        // one recruiter left pending so the admin approval flow can be demonstrated
        dao.register("Karan Mehta", "karan@startupx.com", PW, "RECRUITER", "StartupX");

        String[][] students = {
                {"Aarav Gupta", "aarav@student.com"}, {"Diya Singh", "diya@student.com"},
                {"Ishaan Rao", "ishaan@student.com"}, {"Meera Joshi", "meera@student.com"},
        };
        for (String[] s : students) dao.register(s[0], s[1], PW, "STUDENT", null);
        int aaravId = dao.login("aarav@student.com", PW).getId();
        int diyaId = dao.login("diya@student.com", PW).getId();
        int ishaanId = dao.login("ishaan@student.com", PW).getId();
        dao.saveProfile(aaravId, "9876500001", "Delhi Technological University", "B.Tech CSE", 2027, 8.6,
                "Java, SQL, Spring", demoResume(aaravId, "Aarav Gupta - B.Tech CSE - Java, SQL, Spring"));
        dao.saveProfile(diyaId, "9876500002", "IIIT Hyderabad", "B.Tech IT", 2026, 9.1,
                "Python, Data Analysis, SQL", demoResume(diyaId, "Diya Singh - B.Tech IT - Python, Data Analysis, SQL"));

        dao.saveProfile(ishaanId, "9876500003", "NIT Trichy", "B.Tech ECE", 2027, 7.8,
                "Java, Testing", demoResume(ishaanId, "Ishaan Rao - B.Tech ECE - Java, Testing"));
        // Meera is left with an empty profile on purpose, to show that applying is blocked.

        int tech =dao.companyIdOf(dao.login("neha@techcorp.com", PW).getId());
        int info = dao.companyIdOf(dao.login("rahul@infonova.com", PW).getId());
        int green = dao.companyIdOf(dao.login("priya@greenleaf.com", PW).getId());

        dao.postJob(tech, "Java Backend Developer", "JOB", "Bengaluru", "8 LPA", "Java, Spring Boot, MySQL",
                "Build and maintain REST services for our fintech products.", "2027-03-31");
        dao.postJob(tech, "Software Engineering Intern", "INTERNSHIP", "Remote", "REMOTE", "15000/month", "Java, DSA",
                "6-month internship working with the platform team.", "2027-02-28");
        dao.postJob(info, "Full Stack Developer", "JOB", "Pune", "6.5 LPA", "JavaScript, React, Node.js",
                "Join our product team building customer dashboards.", "2027-04-15");
        dao.postJob(info, "QA Automation Intern", "INTERNSHIP", "Pune", "10000/month", "Selenium, Java, Testing",
                "Write automated tests for web applications.", "2027-03-10");
        dao.postJob(green, "Data Analyst", "JOB", "Hyderabad", "7 LPA", "SQL, Python, Excel",
                "Turn sustainability data into dashboards and reports.", "2027-05-01");
        dao.postJob(green, "Data Science Intern", "INTERNSHIP", "Remote", "REMOTE", "12000/month", "Python, Pandas, SQL",
                "Support the analytics team with modelling and reporting.", "2027-03-20");
        dao.postJob(green, "UI/UX Design Intern", "INTERNSHIP", "Hyderabad", "8000/month", "Figma, HTML, CSS",
                "Design screens for our client portal.", "2027-01-31");

        int aarav = dao.login("aarav@student.com", PW).getId();
        int diya = dao.login("diya@student.com", PW).getId();
        int ishaan = dao.login("ishaan@student.com", PW).getId();
        dao.apply(1, aarav);
        dao.apply(2, aarav);
        dao.apply(5, diya);
        dao.apply(6, diya);
        dao.apply(1, ishaan);
        dao.updateStatus(1, tech, "SHORTLISTED");
        dao.updateStatus(3, green, "SELECTED");
        dao.updateStatus(5, tech, "REJECTED");
        dao.updateStatus(2, tech, "UNDER_REVIEW");
        dao.scheduleInterview(1, tech, "HR Round", "ONLINE", "2027-01-15", "10:00", "https://meet.example.com/techcorp-hr");
        dao.scheduleInterview(1, tech, "Technical Round", "ONLINE", "2027-01-20", "11:00", "https://meet.example.com/techcorp-tech");

        // TechCorp and InfoNova are verified; GreenLeaf is left unverified for the demo.
        dao.setCompanyVerified(tech, true);
        dao.setCompanyVerified(info, true);

        // Two reports on the same posting so the admin report screen shows a HIGH-risk item.
        dao.reportPosting(7, diya, "Asking for money", "Recruiter asked for a Rs 5000 registration fee.");
        dao.reportPosting(7, ishaan, "Suspicious link", "");

        // Company profiles shown on the public company pages.
        dao.saveCompanyProfile(tech, "Software & IT", "201-500", "https://techcorp.example.com", "Bengaluru",
                "TechCorp builds fintech platforms used by banks across India. We hire interns and graduates for backend, "
                        + "data and platform engineering.");
        dao.saveCompanyProfile(info, "IT Services", "51-200", "https://infonova.example.com", "Pune",
                "InfoNova Systems delivers web and QA solutions for retail and logistics clients.");
        dao.saveCompanyProfile(green, "Analytics & Sustainability", "11-50", "https://greenleaf.example.com", "Hyderabad",
                "GreenLeaf Analytics turns environmental data into dashboards and reports.");

        // Optional student details shown to recruiters on the candidate page.
        dao.saveProfileExtras(aaravId, "Library Management System (Java, MySQL)\nPersonal portfolio website",
                "Oracle Java SE certification", "");
        dao.saveProfileExtras(diyaId, "Sales forecasting dashboard (Python, Pandas)", "Google Data Analytics Certificate",
                "Data intern at a startup (3 months)");
        dao.saveProfileExtras(ishaanId, "Test automation framework (Selenium)", "ISTQB Foundation", "");

        // A posting with every field filled in, including preferred skills.
        dao.postJob(tech, "Machine Learning Intern", "INTERNSHIP", "Bengaluru", "HYBRID", "20000/month",
                "Python, Pandas, Machine Learning", "Git, TensorFlow", "Work on model training and evaluation pipelines.",
                "2027-03-15", "6 months", "B.Tech / BCA / MCA", "Fresher", 3);

        // Diya was selected for the Data Analyst job, so she has an offer waiting for her to accept or decline.
        dao.makeOffer(3, green, "2027-02-01", "7 LPA", "Full-time");
        // GreenLeaf is left unverified so the demo can show that unverified companies cannot post.
        dao.setCompanyVerified(green, false);

        System.out.println("Sample data added. " + dao.stats());
        System.out.println("Demo logins (password for all: " + PW + "):");
        System.out.println("  Admin:     admin@portal.com / admin123");
        System.out.println("  Recruiter: neha@techcorp.com");
        System.out.println("  Student:   aarav@student.com");
        System.out.println("  Pending recruiter (approve as admin): karan@startupx.com");
    }
}
