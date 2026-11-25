public class Application {
    private final int id;
    private final int studentId;
    private final int postingId;
    private final String studentName;
    private final String email;
    private final String title;
    private final String company;
    private final String status;

    public Application(int id, int studentId, int postingId, String studentName, String email,
                       String title, String company, String status) {
        this.id = id;
        this.studentId = studentId;
        this.postingId = postingId;
        this.studentName = studentName;
        this.email = email;
        this.title = title;
        this.company = company;
        this.status = status;
    }

    public int getId() { return id; }
    public int getStudentId() { return studentId; }
    public int getPostingId() { return postingId; }
    public String getStudentName() { return studentName; }
    public String getEmail() { return email; }
    public String getTitle() { return title; }
    public String getCompany() { return company; }
    public String getStatus() { return status; }

    @Override
    public String toString() {
        return String.format("#%d %-16s <%s> -> %s @ %s [%s]",
                id, studentName, email, title, company, status);
    }
}
