public class User {
    private final int id;
    private final String name;
    private final String email;
    private final String role;   // STUDENT, RECRUITER, ADMIN
    private final String status; // ACTIVE, PENDING, BLOCKED

    public User(int id, String name, String email, String role, String status) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.status = status;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getStatus() { return status; }

    @Override
    public String toString() {
        return String.format("#%d %-18s <%s> %s [%s]", id, name, email, role, status);
    }
}
