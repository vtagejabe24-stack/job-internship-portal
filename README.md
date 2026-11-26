# Job & Internship Portal (Java + MySQL)

A web application where students browse and apply to jobs and internships, recruiters post openings and manage applicants, and an admin approves recruiters and moderates the site. Built with plain Java (built-in HTTP server) and JDBC + MySQL.

## Project report
- [Read the report (PDF)](Job%20Internship%20Portal%20-%20Mini%20Project%20Report.pdf)
- [Download the report (Word)](Job%20Internship%20Portal%20-%20Mini%20Project%20Report.docx)

## Setup
1. Install MySQL Server and start it.
2. Download MySQL Connector/J and put the jar in `lib/` (already included if you cloned this folder).
3. Copy `db.properties.example` to `db.properties` and set your MySQL user/password.
   The database and tables are created automatically on first run (see `sql/schema.sql`).

## Build and run (PowerShell, from the project folder)
```
javac -d out src\*.java
java -cp "out;lib\*" SeedData      # optional: adds demo data
java -cp "out;lib\*" WebApp        # starts the website
```
Open http://localhost:8080 . Stop with Ctrl+C.

A console version of the same app is available with `java -cp "out;lib\*" Main`.

## Tests
```
java -cp "out;lib\*" Tests
```
Runs 23 automated checks of skill matching, password hashing, the offer-letter PDF and status names, and prints PASS or
FAIL for each. They do not need the database.

## Demo logins (after running SeedData)
| Role | Email | Password |
|---|---|---|
| Admin | admin@portal.com | admin123 |
| Recruiter | neha@techcorp.com | password123 |
| Student | aarav@student.com | password123 |
| Pending recruiter (approve as admin) | karan@startupx.com | password123 |

## Features
- **Students:** profile with PDF resume upload (required before applying), search with filters (keyword, location,
  skill, type, work mode), skill-match score on every posting, "Recommended for you", job details with matched/missing
  skills, application pipeline (Applied -> Under review -> Shortlisted -> Interview -> Selected/Rejected), interview
  details, notifications, and reporting of suspicious postings.
- **Recruiters:** company must be verified before posting; public company profile page; dashboard with hiring funnel,
  per-posting numbers and most common applicant skills; post jobs/internships with duration, education, experience,
  openings and preferred skills; applicants ranked by skill match and filterable (posting, status, skill, min CGPA,
  min match %); candidate page explaining the match; multi-round interviews (HR / Technical / Managerial / Final);
  offers with a downloadable PDF letter that the student accepts or declines; notifications.
- **Admins:** approve/block users, verify companies, review reported postings (risk level; remove / warn / keep),
  manage postings, dashboard stats.
- **Everyone:** change your own password (click your name in the header). There is no email, so a forgotten password is
  handled by request: click "Forgot password?" on the login page, and the request appears on the admin dashboard,
  where the admin sets a temporary password.
- **Student extras:** withdraw an application (the record is kept, and you can apply again later).
- **Recruiter extras:** edit a posting (applicants are notified of what changed), close and reopen postings.
- **Matching:** skill-based - the share of a posting's required skills that appear in the student's skill list.

## Project layout
- `src/WebApp.java` - web server, routes and pages
- `src/PortalDao.java` - all database access (JDBC, PreparedStatement)
- `src/Auth.java` - salted password hashing
- `src/Match.java` - skill matching (score, matched and missing skills)
- `src/Pdf.java` - builds the offer-letter PDF
- `src/User.java`, `Internship.java`, `Application.java` - model classes
- `src/Main.java` - console version
- `src/SeedData.java` - demo data
- `src/Tests.java` - automated unit tests
- `sql/schema.sql` - database schema (9 tables)
