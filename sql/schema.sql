-- Schema for the Job & Internship Portal (MySQL 8).
-- The app creates all of this automatically on first run; this file is for the report / manual setup.

CREATE DATABASE IF NOT EXISTS internship_portal;
USE internship_portal;

CREATE TABLE users (
  user_id       INT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  email         VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,                 -- salted SHA-256, never plain text
  role          ENUM('STUDENT','RECRUITER','ADMIN') NOT NULL,
  status        ENUM('ACTIVE','PENDING','BLOCKED') NOT NULL DEFAULT 'ACTIVE',
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE student_profiles (
  user_id     INT PRIMARY KEY,
  phone       VARCHAR(15),
  college     VARCHAR(150),
  degree      VARCHAR(100),
  grad_year   INT,
  skills      VARCHAR(255),                            -- comma separated, used for skill matching
  resume_link VARCHAR(255),                            -- path of the uploaded PDF, e.g. uploads/7.pdf
  cgpa        DECIMAL(4,2),                            -- 0.00 - 10.00
  projects       TEXT,                                 -- optional
  certifications TEXT,                                 -- optional
  experience     TEXT,                                 -- optional, blank = fresher
  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE companies (
  company_id  INT AUTO_INCREMENT PRIMARY KEY,
  user_id     INT NOT NULL UNIQUE,                     -- the recruiter who owns the company
  name        VARCHAR(150) NOT NULL,
  website     VARCHAR(150),
  location    VARCHAR(100),
  description TEXT,
  verified    BOOLEAN NOT NULL DEFAULT FALSE,          -- set by an admin; unverified companies cannot post
  industry     VARCHAR(80),
  company_size VARCHAR(40),
  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE postings (
  posting_id      INT AUTO_INCREMENT PRIMARY KEY,
  company_id      INT NOT NULL,
  title           VARCHAR(150) NOT NULL,
  type            ENUM('JOB','INTERNSHIP') NOT NULL,
  location        VARCHAR(100) NOT NULL,
  work_mode       ENUM('ONSITE','REMOTE','HYBRID') NOT NULL DEFAULT 'ONSITE',
  pay             VARCHAR(50),
  skills_required VARCHAR(255),                        -- comma separated, used for skill matching
  preferred_skills VARCHAR(255),                       -- optional, counts for 20% of the match score
  duration        VARCHAR(50),
  education       VARCHAR(100),
  experience      VARCHAR(50),
  openings        INT,
  description     TEXT,
  deadline        DATE,
  status          ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',
  posted_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (company_id) REFERENCES companies(company_id) ON DELETE CASCADE
);

CREATE TABLE applications (
  application_id INT AUTO_INCREMENT PRIMARY KEY,
  posting_id     INT NOT NULL,
  student_id     INT NOT NULL,
  status         ENUM('APPLIED','UNDER_REVIEW','SHORTLISTED','INTERVIEW','SELECTED','REJECTED','WITHDRAWN')
                 NOT NULL DEFAULT 'APPLIED',      -- WITHDRAWN: the student withdrew; the row is kept as a record
  applied_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_apply (posting_id, student_id),         -- one application per student per posting
  FOREIGN KEY (posting_id) REFERENCES postings(posting_id) ON DELETE CASCADE,
  FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE interviews (
  interview_id   INT AUTO_INCREMENT PRIMARY KEY,
  application_id INT NOT NULL,                          -- several rounds per application
  round_name     VARCHAR(40) NOT NULL DEFAULT 'Interview', -- HR Round, Technical Round, ...
  mode           ENUM('ONLINE','OFFLINE') NOT NULL,
  interview_date DATE NOT NULL,
  interview_time TIME NOT NULL,
  meeting_link   VARCHAR(255),                          -- link or address
  KEY idx_iv_app (application_id),
  FOREIGN KEY (application_id) REFERENCES applications(application_id) ON DELETE CASCADE
);

CREATE TABLE offers (
  offer_id       INT AUTO_INCREMENT PRIMARY KEY,
  application_id INT NOT NULL UNIQUE,                   -- one offer per application
  start_date     DATE NOT NULL,
  pay            VARCHAR(50),
  duration       VARCHAR(50),
  status         ENUM('PENDING','ACCEPTED','DECLINED') NOT NULL DEFAULT 'PENDING',
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (application_id) REFERENCES applications(application_id) ON DELETE CASCADE
);

CREATE TABLE notifications (
  notification_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id         INT NOT NULL,
  message         VARCHAR(300) NOT NULL,
  is_read         BOOLEAN NOT NULL DEFAULT FALSE,
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE reports (
  report_id   INT AUTO_INCREMENT PRIMARY KEY,
  posting_id  INT NOT NULL,
  reporter_id INT NOT NULL,
  reason      VARCHAR(40) NOT NULL,                     -- Fake company, Asking for money, ...
  details     VARCHAR(255),
  status      ENUM('OPEN','RESOLVED') NOT NULL DEFAULT 'OPEN',
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_report (posting_id, reporter_id),       -- one report per user per posting
  FOREIGN KEY (posting_id) REFERENCES postings(posting_id) ON DELETE CASCADE,
  FOREIGN KEY (reporter_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Relationships:
--   users 1--1 student_profiles          users 1--1 companies (recruiters)
--   companies 1--N postings              postings N--M users (students) via applications
--   applications 1--N interviews         applications 1--0..1 offers
--   users 1--N notifications
--   postings N--M users (reporters) via reports
