import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Database layer for the Job & Internship Portal (MySQL via JDBC). */
public class PortalDao {
    private final String url;
    private final String user;
    private final String password;

    public PortalDao() {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream("db.properties")) {
            p.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Missing db.properties - copy db.properties.example and fill it in.", e);
        }
        String host = p.getProperty("host", "localhost");
        String port = p.getProperty("port", "3306");
        String db = p.getProperty("database", "internship_portal");
        user = p.getProperty("user", "root");
        password = p.getProperty("password", "");
        url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&allowPublicKeyRetrieval=true";

        String serverUrl = "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection c = DriverManager.getConnection(serverUrl, user, password);
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE IF NOT EXISTS `" + db + "`");
        } catch (SQLException e) {
            throw new RuntimeException("Could not connect to MySQL: " + e.getMessage(), e);
        }
        createTables();
        seedAdmin();
    }

    private void createTables() {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "user_id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "name VARCHAR(100) NOT NULL,"
                    + "email VARCHAR(100) NOT NULL UNIQUE,"
                    + "password_hash VARCHAR(255) NOT NULL,"
                    + "role ENUM('STUDENT','RECRUITER','ADMIN') NOT NULL,"
                    + "status ENUM('ACTIVE','PENDING','BLOCKED') NOT NULL DEFAULT 'ACTIVE',"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE IF NOT EXISTS student_profiles ("
                    + "user_id INT PRIMARY KEY,"
                    + "phone VARCHAR(15), college VARCHAR(150), degree VARCHAR(100),"
                    + "grad_year INT, skills VARCHAR(255), resume_link VARCHAR(255),"
                    + "cgpa DECIMAL(4,2),"
                    + "projects TEXT, certifications TEXT, experience TEXT,"
                    + "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE)");
            s.execute("CREATE TABLE IF NOT EXISTS companies ("
                    + "company_id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "user_id INT NOT NULL UNIQUE,"
                    + "name VARCHAR(150) NOT NULL,"
                    + "website VARCHAR(150), location VARCHAR(100), description TEXT,"
                    + "verified BOOLEAN NOT NULL DEFAULT FALSE,"
                    + "industry VARCHAR(80), company_size VARCHAR(40),"
                    + "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE)");
            s.execute("CREATE TABLE IF NOT EXISTS postings ("
                    + "posting_id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "company_id INT NOT NULL,"
                    + "title VARCHAR(150) NOT NULL,"
                    + "type ENUM('JOB','INTERNSHIP') NOT NULL,"
                    + "location VARCHAR(100) NOT NULL,"
                    + "work_mode ENUM('ONSITE','REMOTE','HYBRID') NOT NULL DEFAULT 'ONSITE',"
                    + "pay VARCHAR(50),"
                    + "skills_required VARCHAR(255),"
                    + "preferred_skills VARCHAR(255),"
                    + "duration VARCHAR(50), education VARCHAR(100), experience VARCHAR(50), openings INT,"
                    + "description TEXT,"
                    + "deadline DATE,"
                    + "status ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',"
                    + "posted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "FOREIGN KEY (company_id) REFERENCES companies(company_id) ON DELETE CASCADE)");
            s.execute("CREATE TABLE IF NOT EXISTS applications ("
                    + "application_id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "posting_id INT NOT NULL,"
                    + "student_id INT NOT NULL,"
                    + "status ENUM(" + APP_STATUSES + ") NOT NULL DEFAULT 'APPLIED',"
                    + "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "UNIQUE KEY uq_apply (posting_id, student_id),"
                    + "FOREIGN KEY (posting_id) REFERENCES postings(posting_id) ON DELETE CASCADE,"
                    + "FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE)");
            s.execute("CREATE TABLE IF NOT EXISTS interviews ("
                    + "interview_id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "application_id INT NOT NULL,"
                    + "round_name VARCHAR(40) NOT NULL DEFAULT 'Interview',"
                    + "mode ENUM('ONLINE','OFFLINE') NOT NULL,"
                    + "interview_date DATE NOT NULL,"
                    + "interview_time TIME NOT NULL,"
                    + "meeting_link VARCHAR(255),"
                    + "KEY idx_iv_app (application_id),"
                    + "FOREIGN KEY (application_id) REFERENCES applications(application_id) ON DELETE CASCADE)");
            s.execute("CREATE TABLE IF NOT EXISTS offers ("
                    + "offer_id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "application_id INT NOT NULL UNIQUE,"
                    + "start_date DATE NOT NULL,"
                    + "pay VARCHAR(50), duration VARCHAR(50),"
                    + "status ENUM('PENDING','ACCEPTED','DECLINED') NOT NULL DEFAULT 'PENDING',"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "FOREIGN KEY (application_id) REFERENCES applications(application_id) ON DELETE CASCADE)");
            s.execute("CREATE TABLE IF NOT EXISTS notifications ("
                    + "notification_id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "user_id INT NOT NULL,"
                    + "message VARCHAR(300) NOT NULL,"
                    + "is_read BOOLEAN NOT NULL DEFAULT FALSE,"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE)");
            s.execute("CREATE TABLE IF NOT EXISTS reports ("
                    + "report_id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "posting_id INT NOT NULL,"
                    + "reporter_id INT NOT NULL,"
                    + "reason VARCHAR(40) NOT NULL,"
                    + "details VARCHAR(255),"
                    + "status ENUM('OPEN','RESOLVED') NOT NULL DEFAULT 'OPEN',"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "UNIQUE KEY uq_report (posting_id, reporter_id),"
                    + "FOREIGN KEY (posting_id) REFERENCES postings(posting_id) ON DELETE CASCADE,"
                    + "FOREIGN KEY (reporter_id) REFERENCES users(user_id) ON DELETE CASCADE)");
            // Upgrade databases created by earlier versions of the app.
            tryAlter(s, "ALTER TABLE student_profiles ADD COLUMN cgpa DECIMAL(4,2)");
            tryAlter(s, "ALTER TABLE student_profiles ADD COLUMN projects TEXT");
            tryAlter(s, "ALTER TABLE student_profiles ADD COLUMN certifications TEXT");
            tryAlter(s, "ALTER TABLE student_profiles ADD COLUMN experience TEXT");
            tryAlter(s, "ALTER TABLE companies ADD COLUMN industry VARCHAR(80)");
            tryAlter(s, "ALTER TABLE companies ADD COLUMN company_size VARCHAR(40)");
            tryAlter(s, "ALTER TABLE postings ADD COLUMN preferred_skills VARCHAR(255)");
            tryAlter(s, "ALTER TABLE postings ADD COLUMN duration VARCHAR(50)");
            tryAlter(s, "ALTER TABLE postings ADD COLUMN education VARCHAR(100)");
            tryAlter(s, "ALTER TABLE postings ADD COLUMN experience VARCHAR(50)");
            tryAlter(s, "ALTER TABLE postings ADD COLUMN openings INT");
            // interviews used to allow one row per application; allow several rounds now
            tryAlter(s, "ALTER TABLE interviews ADD COLUMN round_name VARCHAR(40) NOT NULL DEFAULT 'Interview'");
            tryAlter(s, "ALTER TABLE interviews ADD INDEX idx_iv_app (application_id)");
            tryAlter(s, "ALTER TABLE interviews DROP INDEX application_id");
            tryAlter(s, "ALTER TABLE companies ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE");
            tryAlter(s, "ALTER TABLE postings ADD COLUMN work_mode ENUM('ONSITE','REMOTE','HYBRID') "
                    + "NOT NULL DEFAULT 'ONSITE' AFTER location");
            s.execute("ALTER TABLE applications MODIFY status ENUM(" + APP_STATUSES + ") NOT NULL DEFAULT 'APPLIED'");
        } catch (SQLException e) {
            throw new RuntimeException("Could not initialise tables: " + e.getMessage(), e);
        }
    }

    private static final String APP_STATUSES =
            "'APPLIED','UNDER_REVIEW','SHORTLISTED','INTERVIEW','SELECTED','REJECTED','WITHDRAWN'";

    /**
     * Runs an ALTER that may already have been applied. Ignores: 1060 duplicate column,
     * 1061 duplicate index name, 1091 index/column does not exist.
     */
    private static void tryAlter(Statement s, String sql) throws SQLException {
        try {
            s.execute(sql);
        } catch (SQLException e) {
            int code = e.getErrorCode();
            if (code != 1060 && code != 1061 && code != 1091) throw e;
        }
    }

    /** Creates a default admin (admin@portal.com / admin123) on first run. */
    private void seedAdmin() {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO users(name,email,password_hash,role,status) VALUES (?,?,?,'ADMIN','ACTIVE')")) {
            ps.setString(1, "Administrator");
            ps.setString(2, "admin@portal.com");
            ps.setString(3, Auth.hash("admin123"));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not seed admin: " + e.getMessage(), e);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    // ---------- Auth ----------

    /** @return null on success, otherwise an error message. companyName only used for RECRUITER. */
    public String register(String name, String email, String pass, String role, String companyName) {
        String status = role.equals("RECRUITER") ? "PENDING" : "ACTIVE";
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users(name,email,password_hash,role,status) VALUES (?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, email.toLowerCase());
                ps.setString(3, Auth.hash(pass));
                ps.setString(4, role);
                ps.setString(5, status);
                ps.executeUpdate();
                int id;
                try (ResultSet k = ps.getGeneratedKeys()) {
                    k.next();
                    id = k.getInt(1);
                }
                if (role.equals("RECRUITER")) {
                    try (PreparedStatement cp = c.prepareStatement(
                            "INSERT INTO companies(user_id,name) VALUES (?,?)")) {
                        cp.setInt(1, id);
                        cp.setString(2, companyName);
                        cp.executeUpdate();
                    }
                } else {
                    try (PreparedStatement sp = c.prepareStatement(
                            "INSERT INTO student_profiles(user_id) VALUES (?)")) {
                        sp.setInt(1, id);
                        sp.executeUpdate();
                    }
                }
                c.commit();
                return null;
            } catch (SQLIntegrityConstraintViolationException e) {
                c.rollback();
                return "That email is already registered.";
            } catch (SQLException e) {
                c.rollback();
                return "Error: " + e.getMessage();
            }
        } catch (SQLException e) {
            return "Error: " + e.getMessage();
        }
    }

    /** @return the user, or null if email/password is wrong. */
    public User login(String email, String pass) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE email = ?")) {
            ps.setString(1, email.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && Auth.verify(pass, rs.getString("password_hash"))) {
                    return new User(rs.getInt("user_id"), rs.getString("name"), rs.getString("email"),
                            rs.getString("role"), rs.getString("status"));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return null;
    }

    // ---------- Student ----------

    /** cgpa may be null if not given. */
    public void saveProfile(int userId, String phone, String college, String degree,
                            int gradYear, Double cgpa, String skills, String resume) {
        run("UPDATE student_profiles SET phone=?, college=?, degree=?, grad_year=?, cgpa=?, skills=?, resume_link=? "
                        + "WHERE user_id=?",
                phone, college, degree, gradYear > 0 ? gradYear : null, cgpa, skills, resume, userId);
    }

    /** A profile is complete when every field, including the resume, is filled in. */
    public boolean isProfileComplete(int userId) {
        for (String f : getProfile(userId)) {
            if (f.isBlank()) return false;
        }
        return true;
    }

    /** True if the student has applied to at least one posting of this company. */
    public boolean companyHasApplicant(int companyId, int studentId) {
        String sql = "SELECT 1 FROM applications a JOIN postings p ON p.posting_id = a.posting_id "
                + "WHERE p.company_id = ? AND a.student_id = ? LIMIT 1";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, companyId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
            return false;
        }
    }

    /** @return {phone, college, degree, gradYear, skills, resumeLink, cgpa}; empty strings if unset. */
    public String[] getProfile(int userId) {
        String[] out = {"", "", "", "", "", "", ""};
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM student_profiles WHERE user_id=?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String[] cols = {"phone", "college", "degree", "grad_year", "skills", "resume_link", "cgpa"};
                    for (int i = 0; i < cols.length; i++) {
                        String v = rs.getString(cols[i]);
                        out[i] = v == null ? "" : v;
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return out;
    }

    public String showProfile(int userId) {
        String sql = "SELECT u.name, u.email, p.* FROM users u JOIN student_profiles p ON p.user_id = u.user_id "
                + "WHERE u.user_id = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return "Name: " + rs.getString("name") + "\nEmail: " + rs.getString("email")
                            + "\nPhone: " + nz(rs.getString("phone")) + "\nCollege: " + nz(rs.getString("college"))
                            + "\nDegree: " + nz(rs.getString("degree")) + "\nGrad year: " + nz(rs.getString("grad_year"))
                            + "\nCGPA: " + nz(rs.getString("cgpa"))
                            + "\nSkills: " + nz(rs.getString("skills")) + "\nResume: " + nz(rs.getString("resume_link"));
                }
            }
        } catch (SQLException e) {
            return "Error: " + e.getMessage();
        }
        return "No profile found.";
    }

    /** Open postings whose deadline has not passed. type may be null/"" for both. */
    public List<Internship> searchPostings(String keyword, String type) {
        return searchPostings(keyword, type, "", "", "");
    }

    /** Any filter may be null/empty. mode is ONSITE, REMOTE or HYBRID. */
    public List<Internship> searchPostings(String keyword, String type, String location, String skill, String mode) {
        String like = "%" + (keyword == null ? "" : keyword) + "%";
        StringBuilder sql = new StringBuilder(POSTING_SELECT
                + "WHERE p.status='OPEN' AND (p.deadline IS NULL OR p.deadline >= CURDATE()) "
                + "AND (p.title LIKE ? OR c.name LIKE ? OR p.location LIKE ? OR p.skills_required LIKE ?) ");
        List<Object> params = new ArrayList<>(List.of(like, like, like, like));
        if (type != null && !type.isEmpty()) { sql.append("AND p.type = ? "); params.add(type); }
        if (location != null && !location.isEmpty()) { sql.append("AND p.location LIKE ? "); params.add("%" + location + "%"); }
        if (skill != null && !skill.isEmpty()) { sql.append("AND p.skills_required LIKE ? "); params.add("%" + skill + "%"); }
        if (mode != null && !mode.isEmpty()) { sql.append("AND p.work_mode = ? "); params.add(mode); }
        sql.append("ORDER BY p.posting_id");
        return queryPostings(sql.toString(), params.toArray());
    }

    public Internship getPosting(int postingId) {
        List<Internship> l = queryPostings(POSTING_SELECT + "WHERE p.posting_id = ?", postingId);
        return l.isEmpty() ? null : l.get(0);
    }

    /** @return message describing the outcome */
    public String apply(int postingId, int studentId) {
        if (!isProfileComplete(studentId)) {
            return "Please complete your profile and upload your resume before applying.";
        }
        String check = "SELECT 1 FROM postings WHERE posting_id=? AND status='OPEN' "
                + "AND (deadline IS NULL OR deadline >= CURDATE())";
        try (Connection c = connect(); PreparedStatement ck = c.prepareStatement(check)) {
            ck.setInt(1, postingId);
            try (ResultSet rs = ck.executeQuery()) {
                if (!rs.next()) return "No open posting with ID " + postingId;
            }
            // a student who withdrew earlier is re-applying: revive the same row (it is unique per student and posting)
            if (run("UPDATE applications SET status='APPLIED', applied_at=NOW() "
                    + "WHERE posting_id=? AND student_id=? AND status='WITHDRAWN'", postingId, studentId) > 0) {
                notifyRecruiterOfApplication(postingId, studentId);
                return "Application submitted!";
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO applications(posting_id, student_id) VALUES (?,?)")) {
                ps.setInt(1, postingId);
                ps.setInt(2, studentId);
                ps.executeUpdate();
                notifyRecruiterOfApplication(postingId, studentId);
                return "Application submitted!";
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            return "You have already applied to this posting.";
        } catch (SQLException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Marks the student's own application as WITHDRAWN (the record stays) and removes its interview rounds and offer.
     * The student can apply again later; the same row is revived by {@link #apply}.
     */
    public String withdrawApplication(int applicationId, int studentId) {
        String status = null;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT status FROM applications WHERE application_id=? AND student_id=?")) {
            ps.setInt(1, applicationId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) status = rs.getString(1);
            }
        } catch (SQLException e) {
            return "Error: " + e.getMessage();
        }
        if (status == null) return "No such application.";
        if (status.equals("WITHDRAWN")) return "This application is already withdrawn.";
        if (count("SELECT COUNT(*) FROM offers WHERE application_id=? AND status='ACCEPTED'", applicationId) > 0) {
            return "You accepted an offer for this application, so it cannot be withdrawn.";
        }
        notifyCompany(applicationId, "withdrew their application");
        run("DELETE FROM interviews WHERE application_id=?", applicationId);
        run("DELETE FROM offers WHERE application_id=?", applicationId);
        run("UPDATE applications SET status='WITHDRAWN' WHERE application_id=? AND student_id=?", applicationId, studentId);
        return "Application withdrawn. You can apply again from the job page.";
    }

    private boolean isWithdrawn(int applicationId) {
        return count("SELECT COUNT(*) FROM applications WHERE application_id=? AND status='WITHDRAWN'", applicationId) > 0;
    }

    public String changePassword(int userId, String oldPass, String newPass) {
        if (newPass.length() < 6) return "The new password must be at least 6 characters.";
        String stored = null;
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT password_hash FROM users WHERE user_id=?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stored = rs.getString(1);
            }
        } catch (SQLException e) {
            return "Error: " + e.getMessage();
        }
        if (stored == null || !Auth.verify(oldPass, stored)) return "Your current password is wrong.";
        run("UPDATE users SET password_hash=? WHERE user_id=?", Auth.hash(newPass), userId);
        return "Password changed.";
    }

    /** Admin sets a temporary password for a user who forgot theirs (admins change their own via changePassword). */
    public String resetPassword(int userId, String newPass) {
        if (newPass.length() < 6) return "The password must be at least 6 characters.";
        int rows = run("UPDATE users SET password_hash=? WHERE user_id=? AND role <> 'ADMIN'", Auth.hash(newPass), userId);
        if (rows > 0) notifyUser(userId, "An administrator reset your password. Please change it after you log in.");
        return rows > 0 ? "Password reset for user " + userId + "." : "No such (non-admin) user.";
    }

    public List<Application> applicationsOfStudent(int studentId) {
        return queryApplications(APP_SELECT + "WHERE a.student_id = ? ORDER BY a.application_id", studentId);
    }

    // ---------- Recruiter ----------

    public int companyIdOf(int recruiterUserId) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT company_id FROM companies WHERE user_id=?")) {
            ps.setInt(1, recruiterUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return -1;
    }

    public String postJob(int companyId, String title, String type, String location, String pay,
                          String skills, String description, String deadline) {
        return postJob(companyId, title, type, location, "ONSITE", pay, skills, description, deadline);
    }

    public String postJob(int companyId, String title, String type, String location, String workMode,
                          String pay, String skills, String description, String deadline) {
        return postJob(companyId, title, type, location, workMode, pay, skills, "", description, deadline,
                "", "", "", 0);
    }

    public String postJob(int companyId, String title, String type, String location, String workMode,
                          String pay, String skills, String preferred, String description, String deadline,
                          String duration, String education, String experience, int openings) {
        try {
            int rows = runThrowing("INSERT INTO postings(company_id,title,type,location,work_mode,pay,"
                            + "skills_required,preferred_skills,description,deadline,duration,education,experience,openings) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    companyId, title, type, location, workMode, pay, skills, preferred, description,
                    deadline.isEmpty() ? null : Date.valueOf(deadline), duration, education, experience,
                    openings > 0 ? openings : null);
            return rows > 0 ? "Posted." : "Nothing saved.";
        } catch (IllegalArgumentException e) {
            return "Deadline must be in YYYY-MM-DD format.";
        } catch (SQLException e) {
            return "Error: " + e.getMessage();
        }
    }

    public List<Internship> postingsOfCompany(int companyId) {
        return queryPostings(POSTING_SELECT + "WHERE p.company_id = ? ORDER BY p.posting_id", companyId);
    }

    public String closePosting(int postingId, int companyId) {
        int rows = run("UPDATE postings SET status='CLOSED' WHERE posting_id=? AND company_id=?", postingId, companyId);
        return rows > 0 ? "Posting closed." : "No posting with ID " + postingId + " in your company.";
    }

    /** Tells everyone with a live application that the posting changed, and what changed. */
    private void notifyApplicantsOfChange(int postingId, String title, List<String> changes) {
        String sql = "SELECT a.student_id, c.name FROM applications a JOIN postings p ON p.posting_id = a.posting_id "
                + "JOIN companies c ON c.company_id = p.company_id "
                + "WHERE a.posting_id = ? AND a.status NOT IN ('WITHDRAWN','REJECTED')";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, postingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    notifyUser(rs.getInt(1), rs.getString(2) + " - " + title + ": the posting was updated ("
                            + String.join(", ", changes) + "). Open the posting to see the new details.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public String reopenPosting(int postingId, int companyId) {
        int rows = run("UPDATE postings SET status='OPEN' WHERE posting_id=? AND company_id=?", postingId, companyId);
        if (rows == 0) return "No posting with ID " + postingId + " in your company.";
        if (count("SELECT COUNT(*) FROM postings WHERE posting_id=? AND deadline < CURDATE()", postingId) > 0) {
            return "Posting reopened, but its deadline has already passed, so students cannot see it yet. "
                    + "Edit the posting and set a new deadline.";
        }
        return "Posting reopened.";
    }

    private static void diff(List<String> changes, String label, String before, String after) {
        String a = before == null || before.equals("-") ? "" : before.trim();
        String b = after == null ? "" : after.trim();
        if (!a.equals(b)) changes.add(label);
    }

    /** Edits a posting; the WHERE clause makes sure it belongs to the company. */
    public String updatePosting(int postingId, int companyId, String title, String type, String location,
                                String workMode, String pay, String skills, String preferred, String description,
                                String deadline, String duration, String education, String experience, int openings) {
        Internship old = getPosting(postingId);
        try {
            int rows = runThrowing("UPDATE postings SET title=?, type=?, location=?, work_mode=?, pay=?, skills_required=?, "
                            + "preferred_skills=?, description=?, deadline=?, duration=?, education=?, experience=?, openings=? "
                            + "WHERE posting_id=? AND company_id=?",
                    title, type, location, workMode, pay, skills, preferred, description,
                    deadline.isEmpty() ? null : Date.valueOf(deadline), duration, education, experience,
                    openings > 0 ? openings : null, postingId, companyId);
            if (rows > 0 && old != null) {
                List<String> changes = new ArrayList<>();
                diff(changes, "title", old.getTitle(), title);
                diff(changes, "type", old.getType(), type);
                diff(changes, "location", old.getLocation(), location);
                diff(changes, "work mode", old.getWorkMode(), workMode);
                diff(changes, "pay", old.getPay(), pay);
                diff(changes, "required skills", old.getSkills(), skills);
                diff(changes, "preferred skills", old.getPreferred(), preferred);
                diff(changes, "description", old.getDescription(), description);
                diff(changes, "deadline", old.getDeadline(), deadline);
                diff(changes, "duration", old.getDuration(), duration);
                diff(changes, "education", old.getEducation(), education);
                diff(changes, "experience", old.getExperience(), experience);
                diff(changes, "openings", old.getOpenings() > 0 ? String.valueOf(old.getOpenings()) : "",
                        openings > 0 ? String.valueOf(openings) : "");
                if (!changes.isEmpty()) notifyApplicantsOfChange(postingId, title, changes);
            }
            return rows > 0 ? "Posting updated." : "No posting with ID " + postingId + " in your company.";
        } catch (IllegalArgumentException e) {
            return "Deadline must be in YYYY-MM-DD format.";
        } catch (SQLException e) {
            return "Error: " + e.getMessage();
        }
    }

    public List<Application> applicantsForCompany(int companyId) {
        return queryApplications(APP_SELECT + "WHERE p.company_id = ? ORDER BY a.application_id", companyId);
    }

    /** Only updates if the application belongs to one of this company's postings. */
    public String updateStatus(int applicationId, int companyId, String status) {
        if (!APP_STATUSES.contains("'" + status + "'")) return "Invalid status.";
        int rows = run("UPDATE applications a JOIN postings p ON p.posting_id = a.posting_id "
                + "SET a.status = ? WHERE a.application_id = ? AND p.company_id = ? AND a.status <> 'WITHDRAWN'",
                status, applicationId, companyId);
        if (rows > 0) notifyStudent(applicationId, "your application is now " + pretty(status));
        return rows > 0 ? "Status updated to " + pretty(status) : "No such application for your postings.";
    }

    public static String pretty(String status) {
        String s = status.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Schedules (or reschedules) an interview and moves the application to the INTERVIEW stage. */
    public String scheduleInterview(int applicationId, int companyId, String mode, String date,
                                    String time, String link) {
        return scheduleInterview(applicationId, companyId, "Interview", mode, date, time, link);
    }

    /** Adds one interview round. round is e.g. "HR Round", "Technical Round". */
    public String scheduleInterview(int applicationId, int companyId, String round, String mode, String date,
                                    String time, String link) {
        if (round.isBlank() || round.length() > 40) return "Choose an interview round.";
        if (!mode.equals("ONLINE") && !mode.equals("OFFLINE")) return "Choose online or offline.";
        java.time.LocalDate d;
        java.time.LocalTime t;
        try {
            d = java.time.LocalDate.parse(date);
            t = java.time.LocalTime.parse(time);
        } catch (java.time.format.DateTimeParseException e) {
            return "Enter a valid interview date and time.";
        }
        if (d.isBefore(java.time.LocalDate.now())) return "Interview date cannot be in the past.";
        if (count("SELECT COUNT(*) FROM applications a JOIN postings p ON p.posting_id = a.posting_id "
                + "WHERE a.application_id = ? AND p.company_id = ?", applicationId, companyId) == 0) {
            return "No such application for your postings.";
        }
        if (isWithdrawn(applicationId)) return "The student withdrew this application.";
        run("INSERT INTO interviews(application_id, round_name, mode, interview_date, interview_time, meeting_link) "
                        + "VALUES (?,?,?,?,?,?)",
                applicationId, round, mode, Date.valueOf(d), Time.valueOf(t), link);
        run("UPDATE applications SET status='INTERVIEW' WHERE application_id=? AND status <> 'SELECTED'", applicationId);
        notifyStudent(applicationId, round + " scheduled (" + mode.toLowerCase() + ") on " + d + " at "
                + t.toString().substring(0, 5));
        return round + " scheduled.";
    }

    /** @return {mode, date, time, link} or null if none scheduled. */
    public String[] getInterview(int applicationId) {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT mode, interview_date, interview_time, meeting_link FROM interviews WHERE application_id=?")) {
            ps.setInt(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String time = rs.getString(3);
                    return new String[]{rs.getString(1), rs.getString(2), time.substring(0, 5), nz(rs.getString(4))};
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return null;
    }

    /** @return {open postings, total applicants, shortlisted, applicants interviewed, offers made, offers accepted} */
    public int[] recruiterStats(int companyId) {
        String base = "SELECT COUNT(*) FROM applications a JOIN postings p ON p.posting_id = a.posting_id "
                + "WHERE p.company_id = ? AND a.status <> 'WITHDRAWN' ";
        return new int[]{
                count("SELECT COUNT(*) FROM postings WHERE company_id = ? AND status='OPEN'", companyId),
                count(base, companyId),
                count(base + "AND a.status='SHORTLISTED'", companyId),
                count("SELECT COUNT(DISTINCT i.application_id) FROM interviews i "
                        + "JOIN applications a ON a.application_id = i.application_id "
                        + "JOIN postings p ON p.posting_id = a.posting_id WHERE p.company_id = ?", companyId),
                count(OFFER_COUNT, companyId),
                count(OFFER_COUNT + "AND o.status='ACCEPTED'", companyId)};
    }

    private static final String OFFER_COUNT = "SELECT COUNT(*) FROM offers o "
            + "JOIN applications a ON a.application_id = o.application_id "
            + "JOIN postings p ON p.posting_id = a.posting_id WHERE p.company_id = ? ";

    /** @return rows of {postingId, title, applications, shortlisted, in interview, selected}. */
    public List<String[]> perPostingStats(int companyId) {
        List<String[]> out = new ArrayList<>();
        String sql = "SELECT p.posting_id, p.title, COALESCE(SUM(a.status <> 'WITHDRAWN'),0), "
                + "COALESCE(SUM(a.status='SHORTLISTED'),0), COALESCE(SUM(a.status='INTERVIEW'),0), "
                + "COALESCE(SUM(a.status='SELECTED'),0) "
                + "FROM postings p LEFT JOIN applications a ON a.posting_id = p.posting_id "
                + "WHERE p.company_id = ? GROUP BY p.posting_id, p.title ORDER BY p.posting_id";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, companyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6)});
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return out;
    }

    // ---------- interview rounds and offers ----------

    /** @return rows of {round, mode, date, time, link}, in schedule order. */
    public List<String[]> getInterviews(int applicationId) {
        List<String[]> out = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT round_name, mode, interview_date, interview_time, meeting_link FROM interviews "
                        + "WHERE application_id=? ORDER BY interview_date, interview_time, interview_id")) {
            ps.setInt(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4).substring(0, 5), nz(rs.getString(5))});
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return out;
    }

    /** Sends (or updates) an offer and marks the application SELECTED. */
    public String makeOffer(int applicationId, int companyId, String startDate, String pay, String duration) {
        java.time.LocalDate d;
        try {
            d = java.time.LocalDate.parse(startDate);
        } catch (java.time.format.DateTimeParseException e) {
            return "Enter a valid start date.";
        }
        if (d.isBefore(java.time.LocalDate.now())) return "Start date cannot be in the past.";
        if (count("SELECT COUNT(*) FROM applications a JOIN postings p ON p.posting_id = a.posting_id "
                + "WHERE a.application_id = ? AND p.company_id = ?", applicationId, companyId) == 0) {
            return "No such application for your postings.";
        }
        if (isWithdrawn(applicationId)) return "The student withdrew this application.";
        if (count("SELECT COUNT(*) FROM offers WHERE application_id=? AND status='ACCEPTED'", applicationId) > 0) {
            return "This offer was already accepted.";
        }
        run("INSERT INTO offers(application_id, start_date, pay, duration) VALUES (?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE start_date=VALUES(start_date), pay=VALUES(pay), "
                        + "duration=VALUES(duration), status='PENDING'",
                applicationId, Date.valueOf(d), pay, duration);
        run("UPDATE applications SET status='SELECTED' WHERE application_id=?", applicationId);
        notifyStudent(applicationId, "congratulations - you have received an offer! Open My Applications to view it.");
        return "Offer sent.";
    }

    /** @return {startDate, pay, duration, status} or null. */
    public String[] getOffer(int applicationId) {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT start_date, pay, duration, status FROM offers WHERE application_id=?")) {
            ps.setInt(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new String[]{rs.getString(1), nz(rs.getString(2)), nz(rs.getString(3)), rs.getString(4)};
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return null;
    }

    public String respondToOffer(int applicationId, int studentId, boolean accept) {
        if (count("SELECT COUNT(*) FROM applications a JOIN offers o ON o.application_id = a.application_id "
                + "WHERE a.application_id = ? AND a.student_id = ? AND o.status='PENDING'", applicationId, studentId) == 0) {
            return "There is no pending offer to respond to.";
        }
        run("UPDATE offers SET status=? WHERE application_id=?", accept ? "ACCEPTED" : "DECLINED", applicationId);
        notifyCompany(applicationId, accept ? "accepted the offer" : "declined the offer");
        return accept ? "Offer accepted - congratulations!" : "Offer declined.";
    }

    /** @return {student, title, company, startDate, pay, duration, status, studentId, companyId} or null. */
    public String[] offerLetterData(int applicationId) {
        String sql = "SELECT u.name, p.title, c.name, o.start_date, o.pay, o.duration, o.status, a.student_id, p.company_id "
                + "FROM offers o JOIN applications a ON a.application_id = o.application_id "
                + "JOIN postings p ON p.posting_id = a.posting_id JOIN companies c ON c.company_id = p.company_id "
                + "JOIN users u ON u.user_id = a.student_id WHERE o.application_id = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            nz(rs.getString(5)), nz(rs.getString(6)), rs.getString(7), rs.getString(8), rs.getString(9)};
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return null;
    }

    private void notifyCompany(int applicationId, String text) {
        String sql = "SELECT c.user_id, u.name, p.title FROM applications a JOIN postings p ON p.posting_id = a.posting_id "
                + "JOIN companies c ON c.company_id = p.company_id JOIN users u ON u.user_id = a.student_id "
                + "WHERE a.application_id = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) notifyUser(rs.getInt(1), rs.getString(2) + " " + text + " for " + rs.getString(3));
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ---------- company profile and extra student details ----------

    /** @return {name, industry, size, website, location, description, "1" if verified, open positions} or null. */
    public String[] getCompany(int companyId) {
        String sql = "SELECT c.name, c.industry, c.company_size, c.website, c.location, c.description, c.verified, "
                + "(SELECT COUNT(*) FROM postings p WHERE p.company_id = c.company_id AND p.status='OPEN') "
                + "FROM companies c WHERE c.company_id = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, companyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new String[]{rs.getString(1), blank(rs.getString(2)), blank(rs.getString(3)),
                            blank(rs.getString(4)), blank(rs.getString(5)), blank(rs.getString(6)),
                            rs.getBoolean(7) ? "1" : "0", rs.getString(8)};
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return null;
    }

    public void saveCompanyProfile(int companyId, String industry, String size, String website,
                                   String location, String description) {
        run("UPDATE companies SET industry=?, company_size=?, website=?, location=?, description=? WHERE company_id=?",
                industry, size, website, location, description, companyId);
    }

    public boolean isCompanyVerified(int companyId) {
        return count("SELECT COUNT(*) FROM companies WHERE company_id=? AND verified=TRUE", companyId) > 0;
    }

    /** @return {projects, certifications, experience}; empty strings if unset. */
    public String[] getProfileExtras(int userId) {
        String[] out = {"", "", ""};
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT projects, certifications, experience FROM student_profiles WHERE user_id=?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) for (int i = 0; i < 3; i++) out[i] = blank(rs.getString(i + 1));
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return out;
    }

    public void saveProfileExtras(int userId, String projects, String certifications, String experience) {
        run("UPDATE student_profiles SET projects=?, certifications=?, experience=? WHERE user_id=?",
                projects, certifications, experience, userId);
    }

    // ---------- notifications ----------

    private void notifyUser(int userId, String message) {
        run("INSERT INTO notifications(user_id, message) VALUES (?,?)", userId, message);
    }

    private void notifyStudent(int applicationId, String text) {
        String sql = "SELECT a.student_id, p.title, c.name FROM applications a "
                + "JOIN postings p ON p.posting_id = a.posting_id JOIN companies c ON c.company_id = p.company_id "
                + "WHERE a.application_id = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    notifyUser(rs.getInt(1), rs.getString(3) + " - " + rs.getString(2) + ": " + text);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void notifyRecruiterOfApplication(int postingId, int studentId) {
        String sql = "SELECT c.user_id, p.title, u.name FROM postings p JOIN companies c ON c.company_id = p.company_id "
                + "JOIN users u ON u.user_id = ? WHERE p.posting_id = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, postingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) notifyUser(rs.getInt(1), "New application from " + rs.getString(3) + " for " + rs.getString(2));
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /** @return rows of {message, created_at, "1" if read else "0"}, newest first. */
    public List<String[]> notificationsOf(int userId) {
        List<String[]> out = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT message, created_at, is_read FROM notifications WHERE user_id=? "
                        + "ORDER BY notification_id DESC LIMIT 50")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new String[]{rs.getString(1), rs.getString(2), rs.getBoolean(3) ? "1" : "0"});
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return out;
    }

    public int unreadCount(int userId) {
        return count("SELECT COUNT(*) FROM notifications WHERE user_id=? AND is_read=FALSE", userId);
    }

    public void markNotificationsRead(int userId) {
        run("UPDATE notifications SET is_read=TRUE WHERE user_id=?", userId);
    }

    // ---------- reports and company verification ----------

    public String reportPosting(int postingId, int reporterId, String reason, String details) {
        if (count("SELECT COUNT(*) FROM postings WHERE posting_id=?", postingId) == 0) return "No such posting.";
        try {
            runThrowing("INSERT INTO reports(posting_id, reporter_id, reason, details) VALUES (?,?,?,?)",
                    postingId, reporterId, reason, details);
            return "Thanks - the posting was reported and an admin will review it.";
        } catch (SQLIntegrityConstraintViolationException e) {
            return "You have already reported this posting.";
        } catch (SQLException e) {
            return "Error: " + e.getMessage();
        }
    }

    /** @return rows of {reportId, postingId, title, company, reporter, reason, details, status, risk}. */
    public List<String[]> listReports() {
        List<String[]> out = new ArrayList<>();
        String sql = "SELECT r.report_id, r.posting_id, p.title, c.name, u.name, r.reason, r.details, r.status, "
                + "(SELECT COUNT(*) FROM reports x WHERE x.posting_id = r.posting_id) AS cnt "
                + "FROM reports r JOIN postings p ON p.posting_id = r.posting_id "
                + "JOIN companies c ON c.company_id = p.company_id JOIN users u ON u.user_id = r.reporter_id "
                + "ORDER BY r.status ASC, r.report_id DESC";
        try (Connection c = connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                String reason = rs.getString(6);
                int cnt = rs.getInt(9);
                String risk = reason.equals("Asking for money") || cnt >= 3 ? "HIGH"
                        : cnt >= 2 || reason.equals("Fake company") || reason.equals("Suspicious link") ? "MEDIUM" : "LOW";
                out.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), reason, nz(rs.getString(7)), rs.getString(8), risk});
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return out;
    }

    public int openReportCount() {
        return count("SELECT COUNT(*) FROM reports WHERE status='OPEN'");
    }

    /** action: REMOVE (delete the posting), WARN (warn the company), KEEP (dismiss). */
    public String resolveReport(int reportId, String action) {
        int postingId = -1, ownerId = -1;
        String title = "";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT r.posting_id, c.user_id, p.title FROM reports r JOIN postings p ON p.posting_id = r.posting_id "
                        + "JOIN companies c ON c.company_id = p.company_id WHERE r.report_id = ?")) {
            ps.setInt(1, reportId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "No such report.";
                postingId = rs.getInt(1);
                ownerId = rs.getInt(2);
                title = rs.getString(3);
            }
        } catch (SQLException e) {
            return "Error: " + e.getMessage();
        }
        switch (action) {
            case "REMOVE" -> {
                notifyUser(ownerId, "Your posting \"" + title + "\" was removed by an admin after a report.");
                run("DELETE FROM postings WHERE posting_id=?", postingId);   // reports cascade
                return "Posting removed.";
            }
            case "WARN" -> {
                notifyUser(ownerId, "Warning: your posting \"" + title + "\" was reported. Please review it.");
                run("UPDATE reports SET status='RESOLVED' WHERE posting_id=?", postingId);
                return "Company warned.";
            }
            case "KEEP" -> {
                run("UPDATE reports SET status='RESOLVED' WHERE posting_id=?", postingId);
                return "Report dismissed - posting kept.";
            }
            default -> { return "Invalid action."; }
        }
    }

    /** @return rows of {companyId, company, recruiter, "1" if verified else "0"}. */
    public List<String[]> listCompanies() {
        List<String[]> out = new ArrayList<>();
        String sql = "SELECT c.company_id, c.name, u.name, c.verified FROM companies c "
                + "JOIN users u ON u.user_id = c.user_id ORDER BY c.company_id";
        try (Connection c = connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) out.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4) ? "1" : "0"});
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return out;
    }

    public String setCompanyVerified(int companyId, boolean verified) {
        int rows = run("UPDATE companies SET verified=? WHERE company_id=?", verified, companyId);
        return rows > 0 ? (verified ? "Company verified." : "Verification removed.") : "No such company.";
    }

    // ---------- Admin ----------

    public List<User> listUsers(String statusFilter) {
        List<User> out = new ArrayList<>();
        String sql = "SELECT * FROM users " + (statusFilter == null ? "" : "WHERE status = ? ") + "ORDER BY user_id";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (statusFilter != null) ps.setString(1, statusFilter);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new User(rs.getInt("user_id"), rs.getString("name"), rs.getString("email"),
                            rs.getString("role"), rs.getString("status")));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return out;
    }

    public String setUserStatus(int userId, String status) {
        int rows = run("UPDATE users SET status=? WHERE user_id=? AND role <> 'ADMIN'", status, userId);
        // Approving a recruiter also verifies their company, so they can start posting.
        if (rows > 0 && status.equals("ACTIVE")) run("UPDATE companies SET verified=TRUE WHERE user_id=?", userId);
        return rows > 0 ? "User " + userId + " is now " + status : "No such (non-admin) user.";
    }

    public String deletePosting(int postingId) {
        return run("DELETE FROM postings WHERE posting_id=?", postingId) > 0 ? "Posting deleted." : "No such posting.";
    }

    public List<Internship> allPostings() {
        return queryPostings(POSTING_SELECT + "ORDER BY p.posting_id");
    }

    public String stats() {
        String sql = "SELECT (SELECT COUNT(*) FROM users WHERE role='STUDENT') students,"
                + "(SELECT COUNT(*) FROM users WHERE role='RECRUITER') recruiters,"
                + "(SELECT COUNT(*) FROM postings WHERE type='JOB') jobs,"
                + "(SELECT COUNT(*) FROM postings WHERE type='INTERNSHIP') internships,"
                + "(SELECT COUNT(*) FROM applications WHERE status <> 'WITHDRAWN') apps";
        try (Connection c = connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return "Students: " + rs.getInt("students") + " | Recruiters: " + rs.getInt("recruiters")
                    + " | Jobs: " + rs.getInt("jobs") + " | Internships: " + rs.getInt("internships")
                    + " | Applications: " + rs.getInt("apps");
        } catch (SQLException e) {
            return "Error: " + e.getMessage();
        }
    }

    // ---------- helpers ----------

    private static final String POSTING_SELECT =
            "SELECT p.posting_id, p.company_id, c.name AS company, c.verified, p.title, p.type, p.location, "
                    + "p.work_mode, p.pay, p.skills_required, p.preferred_skills, p.description, p.deadline, p.status, "
                    + "p.duration, p.education, p.experience, p.openings "
                    + "FROM postings p JOIN companies c ON c.company_id = p.company_id ";

    private static final String APP_SELECT =
            "SELECT a.application_id, a.student_id, a.posting_id, u.name, u.email, p.title, c.name AS company, a.status "
                    + "FROM applications a JOIN postings p ON p.posting_id = a.posting_id "
                    + "JOIN companies c ON c.company_id = p.company_id JOIN users u ON u.user_id = a.student_id ";

    private List<Internship> queryPostings(String sql, Object... params) {
        List<Internship> out = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Internship(rs.getInt("posting_id"), rs.getInt("company_id"), rs.getString("company"),
                            rs.getString("title"), rs.getString("type"), rs.getString("location"),
                            nz(rs.getString("pay")), nz(rs.getString("skills_required")),
                            blank(rs.getString("preferred_skills")), nz(rs.getString("deadline")), rs.getString("status"),
                            nz(rs.getString("description")), rs.getString("work_mode"), rs.getBoolean("verified"),
                            blank(rs.getString("duration")), blank(rs.getString("education")),
                            blank(rs.getString("experience")), rs.getInt("openings")));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return out;
    }

    private List<Application> queryApplications(String sql, Object... params) {
        List<Application> out = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Application(rs.getInt("application_id"), rs.getInt("student_id"), rs.getInt("posting_id"), rs.getString("name"),
                            rs.getString("email"), rs.getString("title"), rs.getString("company"),
                            rs.getString("status")));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return out;
    }

    private int count(String sql, Object... params) {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
            return 0;
        }
    }

    private static String blank(String s) {
        return s == null ? "" : s;
    }

    private static String nz(String s) {
        return s == null ? "-" : s;
    }

    private int runThrowing(String sql, Object... params) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps.executeUpdate();
        }
    }

    private int run(String sql, Object... params) {
        try {
            return runThrowing(sql, params);
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
            return 0;
        }
    }
}
