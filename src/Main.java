import java.util.List;
import java.util.Scanner;

public class Main {
    private static final Scanner sc = new Scanner(System.in);
    private static PortalDao dao;

    public static void main(String[] args) {
        try {
            dao = new PortalDao();
        } catch (RuntimeException e) {
            System.out.println("Startup failed: " + e.getMessage());
            return;
        }
        while (true) {
            System.out.println("\n=== Job & Internship Portal ===");
            System.out.println("1. Login");
            System.out.println("2. Register as Student");
            System.out.println("3. Register as Recruiter");
            System.out.println("0. Exit");
            switch (readInt("Choose: ")) {
                case 1 -> login();
                case 2 -> register("STUDENT");
                case 3 -> register("RECRUITER");
                case 0 -> { System.out.println("Goodbye!"); return; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    // ---------- auth ----------

    private static void register(String role) {
        String name = readNonEmpty("Full name: ");
        String email = readNonEmpty("Email: ");
        if (!email.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
            System.out.println("Invalid email format.");
            return;
        }
        String pass = readNonEmpty("Password (min 6 chars): ");
        if (pass.length() < 6) {
            System.out.println("Password too short.");
            return;
        }
        String company = role.equals("RECRUITER") ? readNonEmpty("Company name: ") : null;
        String err = dao.register(name, email, pass, role, company);
        if (err != null) System.out.println(err);
        else if (role.equals("RECRUITER")) System.out.println("Registered. An admin must approve your account before you can log in.");
        else System.out.println("Registered! You can log in now.");
    }

    private static void login() {
        String email = readLine("Email: ");
        String pass = readLine("Password: ");
        User u = dao.login(email, pass);
        if (u == null) {
            System.out.println("Wrong email or password.");
        } else if (u.getStatus().equals("PENDING")) {
            System.out.println("Your account is awaiting admin approval.");
        } else if (u.getStatus().equals("BLOCKED")) {
            System.out.println("Your account has been blocked.");
        } else {
            System.out.println("Welcome, " + u.getName() + " (" + u.getRole() + ")");
            switch (u.getRole()) {
                case "STUDENT" -> studentMenu(u);
                case "RECRUITER" -> recruiterMenu(u);
                default -> adminMenu();
            }
        }
    }

    // ---------- student ----------

    private static void studentMenu(User u) {
        while (true) {
            System.out.println("\n--- Student ---");
            System.out.println("1. Browse all openings");
            System.out.println("2. Search (keyword / type)");
            System.out.println("3. Apply");
            System.out.println("4. My applications");
            System.out.println("5. View profile");
            System.out.println("6. Edit profile");
            System.out.println("0. Logout");
            switch (readInt("Choose: ")) {
                case 1 -> print(dao.searchPostings("", null));
                case 2 -> {
                    String kw = readLine("Keyword (title/company/location/skill, blank = all): ");
                    print(dao.searchPostings(kw, pickType(true)));
                }
                case 3 -> System.out.println(dao.apply(readInt("Posting ID: "), u.getId()));
                case 4 -> print(dao.applicationsOfStudent(u.getId()));
                case 5 -> System.out.println(dao.showProfile(u.getId()));
                case 6 -> {
                    dao.saveProfile(u.getId(), readLine("Phone: "), readLine("College: "), readLine("Degree: "),
                            readInt("Graduation year: "), readCgpa(), readLine("Skills (comma separated): "),
                            readLine("Resume link: "));
                    System.out.println("Profile saved.");
                }
                case 0 -> { return; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    // ---------- recruiter ----------

    private static void recruiterMenu(User u) {
        int companyId = dao.companyIdOf(u.getId());
        while (true) {
            System.out.println("\n--- Recruiter ---");
            System.out.println("1. Post a job / internship");
            System.out.println("2. My postings");
            System.out.println("3. Close a posting");
            System.out.println("4. View applicants");
            System.out.println("5. Update applicant status");
            System.out.println("0. Logout");
            switch (readInt("Choose: ")) {
                case 1 -> {
                    String type = pickType(false);
                    if (type == null) { System.out.println("Invalid type."); break; }
                    String title = readNonEmpty("Title: ");
                    String location = readNonEmpty("Location: ");
                    String pay = readLine(type.equals("JOB") ? "Salary (e.g. 6 LPA): " : "Stipend (e.g. 10000/month): ");
                    String skills = readLine("Skills required: ");
                    String desc = readLine("Description: ");
                    String deadline = readLine("Deadline (YYYY-MM-DD, blank for none): ");
                    System.out.println(dao.postJob(companyId, title, type, location, pay, skills, desc, deadline));
                }
                case 2 -> print(dao.postingsOfCompany(companyId));
                case 3 -> System.out.println(dao.closePosting(readInt("Posting ID: "), companyId));
                case 4 -> print(dao.applicantsForCompany(companyId));
                case 5 -> {
                    int id = readInt("Application ID: ");
                    System.out.println("1. Shortlisted  2. Selected  3. Rejected");
                    String status = switch (readInt("Status: ")) {
                        case 1 -> "SHORTLISTED";
                        case 2 -> "SELECTED";
                        case 3 -> "REJECTED";
                        default -> null;
                    };
                    System.out.println(status == null ? "Invalid status." : dao.updateStatus(id, companyId, status));
                }
                case 0 -> { return; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    // ---------- admin ----------

    private static void adminMenu() {
        while (true) {
            System.out.println("\n--- Admin ---");
            System.out.println("1. Dashboard stats");
            System.out.println("2. Pending recruiters");
            System.out.println("3. Approve a user");
            System.out.println("4. Block a user");
            System.out.println("5. All users");
            System.out.println("6. All postings");
            System.out.println("7. Delete a posting");
            System.out.println("0. Logout");
            switch (readInt("Choose: ")) {
                case 1 -> System.out.println(dao.stats());
                case 2 -> print(dao.listUsers("PENDING"));
                case 3 -> System.out.println(dao.setUserStatus(readInt("User ID: "), "ACTIVE"));
                case 4 -> System.out.println(dao.setUserStatus(readInt("User ID: "), "BLOCKED"));
                case 5 -> print(dao.listUsers(null));
                case 6 -> print(dao.allPostings());
                case 7 -> System.out.println(dao.deletePosting(readInt("Posting ID: ")));
                case 0 -> { return; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    // ---------- helpers ----------

    /** allowAll: blank/3 means no filter (returns ""); otherwise invalid -> null. */
    private static String pickType(boolean allowAll) {
        System.out.println("1. Job  2. Internship" + (allowAll ? "  3. Both" : ""));
        return switch (readInt("Type: ")) {
            case 1 -> "JOB";
            case 2 -> "INTERNSHIP";
            case 3 -> allowAll ? "" : null;
            default -> allowAll ? "" : null;
        };
    }

    private static <T> void print(List<T> items) {
        if (items.isEmpty()) System.out.println("Nothing found.");
        else items.forEach(System.out::println);
    }

    private static String readLine(String prompt) {
        System.out.print(prompt);
        return sc.hasNextLine() ? sc.nextLine().trim() : "";
    }

    private static String readNonEmpty(String prompt) {
        while (true) {
            String s = readLine(prompt);
            if (!s.isEmpty()) return s;
            System.out.println("This field is required.");
        }
    }

    private static double readCgpa() {
        while (true) {
            try {
                double v = Double.parseDouble(readLine("CGPA (0-10): "));
                if (v >= 0 && v <= 10) return v;
            } catch (NumberFormatException ignored) { }
            System.out.println("Enter a number between 0 and 10.");
        }
    }

    private static int readInt(String prompt) {
        while (true) {
            try {
                return Integer.parseInt(readLine(prompt));
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number.");
            }
        }
    }
}
