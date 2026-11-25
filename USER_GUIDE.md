# Job & Internship Portal - Complete User Guide

A web site where **students** find and apply for jobs/internships, **recruiters** post openings and run hiring, and **admins** keep the platform trustworthy. It is written in plain Java (built-in HTTP server) with a MySQL database.

---

## 1. Starting the site

```
javac -d out src\*.java
java -cp "out;lib\*" SeedData      # optional: demo data
java -cp "out;lib\*" WebApp        # start the website
```
Open **http://localhost:8080**. Stop with Ctrl+C. (MySQL must be running and `db.properties` filled in; tables are created automatically.)

### Demo logins (after SeedData)
| Role | Email | Password |
|---|---|---|
| Admin | admin@portal.com | admin123 |
| Recruiter | neha@techcorp.com | password123 |
| Student | aarav@student.com | password123 |
| Pending recruiter | karan@startupx.com | password123 (cannot log in until an admin approves) |

---

## 2. The three roles at a glance

| | Student | Recruiter | Admin |
|---|---|---|---|
| Lands on after login | Browse Openings | Dashboard | Admin Dashboard |
| Menu | Browse Openings, My Applications, My Profile | Dashboard, Post Opening, Applicants, Company Profile | Dashboard, All Postings, Reports |
| Needs approval? | No | Yes - admin must approve | - |

Top-right of every page: **Notifications (n unread)** and your **name (role)**, which opens *Change password*.

---

## 3. Everyone (including visitors)

- **Home page** (logged out): **Log in** and **Sign up** buttons, plus a small link to browse openings without an account.
- **Register**: choose *Student* or *Recruiter*. Needs name, valid email, password of at least 6 characters; recruiters also enter a company name.
  - Students can log in immediately.
  - Recruiters are **pending** until an admin approves them.
- **Login / Logout**. Blocked or pending accounts get a clear message instead of logging in.
- **Browse Openings** (`/jobs`): filter by keyword (title/company), location, skill, type (Job / Internship) and work mode (On-site / Remote / Hybrid).
- **Job details** (`/job?id=`): company (with a green "Verified" tick if approved), type, location/work mode, pay, deadline, duration, education, experience, openings, required and preferred skills, description. Logged-out visitors are asked to log in to apply.
- **Company page** (`/company?id=`): public profile (industry, size, website, location, about us) and all its open positions.
- **Change password** (click your name): current password + new password (min 6) + confirm.
- **Forgot password?** There is no email. Click **Forgot password?** on the login page and enter your email. The request appears on the admin dashboard; the admin sets a temporary password and gives it to you. Change it after you log in. (If you remember your password and log in normally, the request is cancelled.)
- **Notifications**: a list of events (new application, status change, interview, offer, report outcomes). Unread ones are highlighted and marked read when you open the page.

---

## 4. Student guide

### 4.1 Complete your profile first
My Profile requires every starred field, or you cannot apply:
Phone, College, Degree, Graduation year, CGPA (0-10), Skills (comma separated) and a **resume PDF** (max 2 MB; must really be a PDF).
Optional: Projects, Certifications, Work experience (blank = fresher). Uploading a new PDF replaces the old one. If you try to apply with an incomplete profile you are sent back here.

### 4.2 Find openings
- **Recommended for you**: when you have skills in your profile and no filters set, the top 5 matching openings appear with a match % and the skills you have / are missing.
- **Match score** (shown on every posting): required skills count 80%, preferred skills 20% (if the posting lists any). On a job page you see green ticks for skills you have and red crosses for ones you lack, plus "Skills to learn".

### 4.3 Apply
Press **Apply now** on an open posting. Rules:
- Posting must be open and its deadline not passed.
- One application per posting. Applying again gives "already applied".
- The recruiter is notified.

### 4.4 Track applications (My Applications)
For each application you see:
- **Progress pipeline**: Applied -> Under review -> Shortlisted -> Interview -> Selected (or Rejected).
- **Interviews**: every round with type (online/offline), date, time and meeting link/address.
- **Offer**: start date, pay, duration, status, and **View offer letter** (PDF). While the offer is *Pending* you can **Accept** or **Decline**.
- **Withdraw**: cancels your application (record kept, interviews/offer removed, recruiter notified). Not allowed after you have accepted an offer. A withdrawn application shows **Apply again**, which revives the same application.

### 4.5 Report a suspicious posting
On a job page open **Report this posting**: choose a reason (Fake company, Asking for money, Suspicious link, Incorrect information, Other), optionally add details (max 250 characters). One report per posting per student. Admins review it.

---

## 5. Recruiter guide

### 5.1 Get verified
After registering, an admin must approve your account (which also verifies your company). Until the company is verified you cannot post, edit or reopen openings; the dashboard shows a "Verification pending" warning.

### 5.2 Company Profile
Industry, size (1-10 ... 500+), website (must start with http:// or https://), location and "About the company". This becomes your public company page.

### 5.3 Post an opening (Post Opening)
Fields: Type (Job/Internship), Title*, Location*, Work mode, Salary/Stipend, Duration, Required skills, Preferred skills (optional), Required education, Experience (Fresher / 0-1 / 1-3 / 3+ years), Number of openings, Description, Application deadline.
Skills drive student matching, so list them comma separated.

### 5.4 Dashboard (`/postings`)
- Counters: Active postings, Applications, Shortlisted, Interviewed, Offers made, Offers accepted.
- **Hiring funnel** bars and **Most common applicant skills**.
- **Applications per posting** table.
- **My Postings** table with **Edit**, **Close** and **Reopen**. Editing notifies applicants about what changed. Closed postings stop accepting applications.

### 5.5 Applicants (`/applicants`)
Everyone who applied to your company, **ranked by skill match**. Filters: posting, status, skill, minimum CGPA, minimum match %. Quick buttons: **Shortlist**, **Reject**, **View**.

### 5.6 Candidate page
- Contact, education, CGPA, skills, projects, certifications, experience, and **View resume** (PDF).
- Match breakdown: required skills x/y and preferred skills x/y with ticks and crosses.
- **Review** buttons: Under review / Shortlist / Reject (the student is notified).
- **Interview rounds**: schedule HR / Technical / Managerial / Final rounds with Online/Offline, date, time and link/address. Past dates are refused. Scheduling moves the application to *Interview* and notifies the student.
- **Offer**: set start date (not in the past), pay and duration -> **Generate offer**. This marks the application *Selected*, notifies the student and creates the offer-letter PDF. You can update a pending/declined offer; an accepted offer is locked.
- Withdrawn applications are shown read-only: no review, interview or offer is possible.

You are notified when a student applies, withdraws, accepts or declines an offer.

---

## 6. Admin guide

**Admin Dashboard** (`/admin`):
- Platform stats and a link to **Reported postings** (shows how many are open).
- **Password reset requests** (only shown when someone has asked): set a temporary password of at least 6 characters and give it to the user. Only users who asked can be reset, and admin accounts never can.
- **Awaiting approval**: approve (activates the user and verifies a recruiter's company) or block.
- **Companies**: Verify / Remove verification.
- **All users**: Approve or Block.

**All Postings** (`/admin/postings`): view every posting and **Delete** any of them.

**Reported postings** (`/admin/reports`): each report shows a **risk level**:
- HIGH - reason "Asking for money", or 3+ reports on the posting
- MEDIUM - "Fake company" / "Suspicious link", or 2 reports
- LOW - everything else

Actions: **Remove job** (deletes the posting and tells the company), **Warn company** (notifies them, resolves reports), **Keep job** (dismisses reports).

---

## 7. Typical end-to-end flow

1. Recruiter registers -> admin approves.
2. Recruiter completes the company profile and posts an internship.
3. Student registers, completes profile + resume, sees the posting (with match %), applies.
4. Recruiter opens Applicants, reviews the candidate, shortlists, schedules interview rounds.
5. Recruiter generates an offer; the student gets a notification and downloads the offer-letter PDF.
6. Student accepts (or declines); the recruiter's dashboard funnel updates.
7. Anyone suspicious can be reported; admin removes, warns or keeps.

---

## 8. Data and security notes

- **Database** (MySQL, 9 tables): users, student_profiles, companies, postings, applications, interviews, offers, notifications, reports. See `sql/schema.sql`.
- Passwords are stored as salted hashes, never plain text.
- Sessions use an HttpOnly cookie. Pages check your role, so a student cannot open recruiter or admin pages.
- A recruiter can only see and change their own company's postings and applicants. Resumes are visible only to the student, an admin, or a recruiter the student applied to.
- Database queries use prepared statements.
- Resumes are saved in the `uploads/` folder as `<userId>.pdf`.

## 9. Project layout

| File | Purpose |
|---|---|
| `src/WebApp.java` | Web server, routes, pages |
| `src/PortalDao.java` | All database access (JDBC) |
| `src/Auth.java` | Salted password hashing |
| `src/Match.java` | Skill-match score |
| `src/Pdf.java` | Offer-letter PDF |
| `src/User.java`, `Internship.java`, `Application.java` | Model classes |
| `src/Main.java` | Console version |
| `src/SeedData.java` | Demo data |
| `USER_GUIDE.md` | This guide |
| `sql/schema.sql` | Database schema |
