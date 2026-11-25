/** A job or internship posting. (Class name kept from the first version.) */
public class Internship {
    private final int id;
    private final int companyId;
    private final String company;
    private final String title;
    private final String type;      // JOB or INTERNSHIP
    private final String location;
    private final String pay;       // salary / stipend text
    private final String skills;    // required skills
    private final String preferred; // preferred skills
    private final String deadline;
    private final String status;    // OPEN or CLOSED
    private final String description;
    private final String workMode;  // ONSITE, REMOTE or HYBRID
    private final boolean verified; // company verified by admin
    private final String duration;
    private final String education;
    private final String experience;
    private final int openings;     // 0 = not specified

    public Internship(int id, int companyId, String company, String title, String type, String location,
                      String pay, String skills, String preferred, String deadline, String status,
                      String description, String workMode, boolean verified,
                      String duration, String education, String experience, int openings) {
        this.id = id;
        this.companyId = companyId;
        this.company = company;
        this.title = title;
        this.type = type;
        this.location = location;
        this.pay = pay;
        this.skills = skills;
        this.preferred = preferred;
        this.deadline = deadline;
        this.status = status;
        this.description = description;
        this.workMode = workMode;
        this.verified = verified;
        this.duration = duration;
        this.education = education;
        this.experience = experience;
        this.openings = openings;
    }

    public int getId() { return id; }
    public int getCompanyId() { return companyId; }
    public String getCompany() { return company; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public String getLocation() { return location; }
    public String getPay() { return pay; }
    public String getSkills() { return skills; }
    public String getPreferred() { return preferred; }
    public String getDeadline() { return deadline; }
    public String getStatus() { return status; }
    public String getDescription() { return description; }
    public String getWorkMode() { return workMode; }
    public boolean isVerified() { return verified; }
    public String getDuration() { return duration; }
    public String getEducation() { return education; }
    public String getExperience() { return experience; }
    public int getOpenings() { return openings; }

    @Override
    public String toString() {
        return String.format("[%d] %-10s %-24s | %-16s | %-12s | %-14s | skills: %s | due %s | %s",
                id, type, title, company, location, pay, skills, deadline, status);
    }
}
