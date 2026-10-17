from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak
from reportlab.graphics.shapes import Drawing, Rect, String, Line, Polygon, Ellipse
import math

NAVY = colors.HexColor("#0b6e4f")
base = ParagraphStyle("b", fontName="Helvetica", fontSize=10.5, leading=15, spaceAfter=6)
bl = ParagraphStyle("bl", parent=base, leftIndent=16, bulletIndent=4, spaceAfter=2)
h1 = ParagraphStyle("h1", fontName="Helvetica-Bold", fontSize=16, leading=20, textColor=NAVY, spaceBefore=6, spaceAfter=10)
h2 = ParagraphStyle("h2", fontName="Helvetica-Bold", fontSize=12, leading=16, spaceBefore=10, spaceAfter=4)
cap = ParagraphStyle("cap", parent=base, fontName="Helvetica-Oblique", fontSize=9, alignment=1)
cell = ParagraphStyle("c", fontName="Helvetica", fontSize=8.5, leading=11)
cellb = ParagraphStyle("cb", parent=cell, fontName="Helvetica-Bold", textColor=colors.white)
S = []
def H1(t): S.append(Paragraph(t, h1))
def H2(t): S.append(Paragraph(t, h2))
def P(t): S.append(Paragraph(t, base))
def L(*items):
    for i in items: S.append(Paragraph(i, bl, bulletText="\u2022"))
def TBL(rows, widths):
    data = [[Paragraph(str(c), cellb if r == 0 else cell) for c in row] for r, row in enumerate(rows)]
    t = Table(data, colWidths=widths, repeatRows=1)
    t.setStyle(TableStyle([("BACKGROUND", (0,0), (-1,0), NAVY), ("GRID", (0,0), (-1,-1), 0.4, colors.grey), ("VALIGN", (0,0), (-1,-1), "TOP"),
        ("ROWBACKGROUNDS", (0,1), (-1,-1), [colors.white, colors.HexColor("#eef5f1")]), ("TOPPADDING", (0,0), (-1,-1), 3), ("BOTTOMPADDING", (0,0), (-1,-1), 3)]))
    S.append(t); S.append(Spacer(1, 8))

# ---------- diagram helpers ----------
def box(d, x, y, w, h, text, fill="#e3f2ec", size=8):
    d.add(Rect(x, y, w, h, fillColor=colors.HexColor(fill), strokeColor=colors.HexColor("#0b6e4f"), strokeWidth=1))
    lines = text.split("\n")
    for i, ln in enumerate(lines):
        d.add(String(x + w/2, y + h/2 + (len(lines)/2 - i - 0.75) * (size + 2), ln, fontSize=size, textAnchor="middle", fontName="Helvetica-Bold" if i == 0 else "Helvetica"))
def arrow(d, x1, y1, x2, y2):
    d.add(Line(x1, y1, x2, y2, strokeColor=colors.HexColor("#333333")))
    a = math.atan2(y2-y1, x2-x1); s = 6
    d.add(Polygon([x2, y2, x2 - s*math.cos(a-0.4), y2 - s*math.sin(a-0.4), x2 - s*math.cos(a+0.4), y2 - s*math.sin(a+0.4)], fillColor=colors.HexColor("#333333"), strokeColor=None))
def fig(d, caption):
    S.append(d); S.append(Paragraph(caption, cap)); S.append(Spacer(1, 6))

def d_arch():
    d = Drawing(480, 250)
    box(d, 160, 205, 160, 36, "Presentation layer\nJetpack Compose screens + Navigation", "#d9eef7")
    box(d, 160, 150, 160, 36, "ViewModel layer\nAuth / Member / Admin ViewModels", "#e3f2ec")
    box(d, 160, 95, 160, 36, "Repository (business rules)\nValidation, ledger, notifications", "#fff3d6")
    box(d, 30, 30, 130, 40, "Room DAO + SQLite\nsacco.db", "#f3e0e0")
    box(d, 175, 30, 130, 40, "Util: Validators, LoanCalculator\nPasswordHasher, SessionManager", "#eeeeee")
    box(d, 320, 30, 130, 40, "Android services\nNotificationManager, files", "#eeeeee")
    for y1, y2 in [(205, 186), (150, 131)]: arrow(d, 240, y1, 240, y2)
    arrow(d, 200, 95, 100, 70); arrow(d, 240, 95, 240, 70); arrow(d, 280, 95, 385, 70)
    return d
def d_er():
    d = Drawing(480, 300)
    pos = {"users": (190, 130, 100, 60), "savings": (20, 230, 100, 40), "loans": (190, 230, 100, 40), "transactions": (360, 230, 110, 40),
           "shares": (20, 40, 100, 40), "notifications": (190, 20, 100, 40), "loan_repayments": (360, 130, 110, 40), "products": (360, 40, 110, 40)}
    txt = {"users": "users\nid PK, memberNumber, email\nrole, status, passwordHash", "savings": "savings\nuserId FK, type, amount", "loans": "loans\nuserId FK, productId FK\nstatus, principal",
           "transactions": "transactions\nuserId FK, type, amount", "shares": "shares\nuserId FK, shares, amount", "notifications": "notifications\nuserId FK, type, isRead",
           "loan_repayments": "loan_repayments\nloanId FK, userId FK", "products": "products\nrate, min, max, months"}
    for k, (x, y, w, h) in pos.items(): box(d, x, y, w, h, txt[k], "#fff3d6" if k == "users" else "#e3f2ec", 7)
    def c(k): x, y, w, h = pos[k]; return x + w/2, y + h/2
    for k in ["savings", "loans", "transactions", "shares", "notifications", "loan_repayments"]:
        (x1, y1), (x2, y2) = c("users"), c(k); d.add(Line(x1, y1, x2, y2, strokeColor=colors.grey))
        d.add(String((x1*0.3+x2*0.7), (y1*0.3+y2*0.7)+3, "N", fontSize=8)); d.add(String((x1*0.7+x2*0.3), (y1*0.7+y2*0.3)+3, "1", fontSize=8))
    d.add(Line(*c("loans"), *c("loan_repayments"), strokeColor=colors.grey)); d.add(Line(*c("products"), *c("loans"), strokeColor=colors.grey))
    d.add(String(240, 2, "1 user : N rows in each child table; 1 loan : N repayments; 1 product : N loans", fontSize=7, textAnchor="middle"))
    return d
def d_usecase():
    d = Drawing(480, 300)
    d.add(Rect(110, 5, 260, 290, fillColor=None, strokeColor=colors.grey))
    d.add(String(240, 282, "SACCO Connect", fontSize=9, textAnchor="middle", fontName="Helvetica-Bold"))
    member = ["Register / Login", "Manage profile", "Deposit / Withdraw", "View savings & statements", "Apply for loan", "Repay loan", "View transactions", "Buy shares", "View notifications"]
    admin = ["Approve/reject members", "Approve/reject loans", "View repayments", "Manage products", "Post announcements", "Monitor transactions", "View reports"]
    for i, t in enumerate(member):
        y = 258 - i * 27; d.add(Ellipse(185, y, 62, 11, fillColor=colors.HexColor("#e3f2ec"), strokeColor=colors.HexColor("#0b6e4f"))); d.add(String(185, y-3, t, fontSize=7, textAnchor="middle")); d.add(Line(40, 150, 123, y, strokeColor=colors.grey))
    for i, t in enumerate(admin):
        y = 258 - i * 27; d.add(Ellipse(315, y, 62, 11, fillColor=colors.HexColor("#fff3d6"), strokeColor=colors.HexColor("#8a5a00"))); d.add(String(315, y-3, t, fontSize=7, textAnchor="middle")); d.add(Line(440, 150, 377, y, strokeColor=colors.grey))
    d.add(String(40, 135, "Member", fontSize=9, textAnchor="middle", fontName="Helvetica-Bold")); d.add(String(440, 135, "Administrator", fontSize=9, textAnchor="middle", fontName="Helvetica-Bold"))
    return d
def d_activity():
    d = Drawing(480, 330)
    steps = ["Member opens Apply for loan", "Choose product, amount, duration, frequency, purpose", "Validate: amount range, limit (3x savings), purpose",
             "Save loan as PENDING + notify member", "Admin reviews application", "Approve: ACTIVE, disburse, notify", "Member repays installments",
             "Balance reaches 0: FULLY_PAID + notify"]
    for i, t in enumerate(steps):
        y = 295 - i * 40; box(d, 130, y, 220, 26, t, "#e3f2ec", 7)
        if i: arrow(d, 240, y + 40, 240, y + 26)
    box(d, 370, 255, 100, 26, "Invalid input:\nshow error", "#f3e0e0", 7); arrow(d, 350, 268, 370, 268)
    box(d, 370, 175, 100, 26, "Reject: status\nREJECTED + reason", "#f3e0e0", 7); arrow(d, 350, 188, 370, 188)
    return d
def d_nav():
    d = Drawing(480, 250)
    box(d, 190, 215, 100, 26, "Splash", "#d9eef7"); box(d, 190, 165, 100, 26, "Login", "#d9eef7")
    box(d, 20, 165, 100, 26, "Register / Forgot password", "#d9eef7", 7); arrow(d, 240, 215, 240, 191); arrow(d, 190, 178, 120, 178)
    box(d, 60, 95, 150, 40, "Member shell\nHome | Savings | Loans | History | Profile", "#e3f2ec", 7)
    box(d, 270, 95, 160, 40, "Admin shell\nDashboard | Members | Loans | Reports", "#fff3d6", 7)
    arrow(d, 215, 165, 140, 135); arrow(d, 265, 165, 340, 135)
    box(d, 20, 20, 190, 50, "Apply loan, Loan detail (schedule, repay),\nShares, Notifications, Edit profile", "#eeeeee", 7)
    box(d, 270, 20, 190, 50, "Member account, Transactions, Repayments,\nProducts, Announcements", "#eeeeee", 7)
    arrow(d, 135, 95, 115, 70); arrow(d, 350, 95, 365, 70)
    return d

# ---------- front matter ----------
S.append(Spacer(1, 90))
S.append(Paragraph("STU 3102: MOBILE APPLICATION DEVELOPMENT", ParagraphStyle("t0", parent=base, alignment=1, fontSize=12)))
S.append(Paragraph("Design and Development of a Mobile-Based SACCO Management and Financial Services Application", ParagraphStyle("t1", parent=h1, alignment=1, fontSize=22, leading=28, spaceBefore=20)))
S.append(Paragraph("<b>SACCO Connect</b> | Project Report", ParagraphStyle("t2", parent=base, alignment=1, fontSize=13)))
S.append(Spacer(1, 40))
S.append(Paragraph("Name: ______________________________ &nbsp; Registration No.: ____________________", ParagraphStyle("t3", parent=base, alignment=1)))
S.append(Paragraph("Lecturer: ______________________________ &nbsp; Date: ____________________", ParagraphStyle("t4", parent=base, alignment=1)))
S.append(Spacer(1, 40))
P("<b>Declaration.</b> I declare that this project is my own individual work. All data used is simulated demonstration data. No real banking credentials, SACCO accounts, national ID numbers or financial transactions are used.")
P("<b>Abstract.</b> Manual member and loan management in SACCOs is slow, error-prone and hard to audit. This project delivers SACCO Connect, an offline-first Android application built with Kotlin, Jetpack Compose and a Room (SQLite) database. Members can register, manage their profile, save, buy shares, apply for loans, view a repayment schedule, make simulated repayments and receive notifications. Administrators can approve members and loans, monitor transactions, manage products, post announcements and view reports. The application uses MVVM architecture, salted PBKDF2 password hashing, role-based access, form validation and idle-session expiry.")
S.append(PageBreak())

H1("Chapter 1: Introduction")
H2("1.1 Background")
P("Savings and Credit Cooperative Organizations (SACCOs) pool member savings and give loans at affordable rates. Many still keep ledgers on paper or spreadsheets. Members must visit the office to see balances, apply for loans or check repayment progress, and officials spend much time reconciling records.")
H2("1.2 Problem statement")
P("Manual SACCO processes cause delays in loan approval, errors in calculating interest and balances, poor transparency for members, weak audit trails and limited reporting for management.")
H2("1.3 Objectives")
L("To design and build a mobile application that lets members manage savings, shares and loans from their phones.",
  "To provide an administrator interface for approving members and loans, monitoring transactions and producing reports.",
  "To store data persistently in a relational database with enforced relationships.",
  "To apply validation and basic security practices (authentication, hashing, role-based access, session control).")
H2("1.4 Scope")
P("The system is an offline prototype for a single device. Payments (deposits, withdrawals, repayments) are simulated and move no real money. It does not integrate with M-Pesa or a bank. It covers members and one administrator role.")
H2("1.5 Significance")
P("The application shows how a modern mobile platform can replace manual SACCO processes: faster loan processing, accurate calculations, instant balances, notifications and management reports.")

H1("Chapter 2: System Analysis")
H2("2.1 Existing system")
P("Members fill paper forms, officials record transactions in ledgers or spreadsheets, and loan committees review applications in meetings. Members learn balances when they visit or call.")
H2("2.2 Problems with the existing system")
L("Slow approvals and repeated manual calculations.", "Errors and duplicated records.", "No instant access to balances or statements.", "Weak security of paper records and no audit trail.", "Difficult reporting.")
H2("2.3 Proposed solution")
P("A mobile application with member and administrator roles backed by a local Room database, automated loan calculations, a generated repayment schedule, a transaction ledger and in-app and system notifications.")
H2("2.4 Functional requirements")
TBL([["ID", "Requirement"],
 ["FR1", "Register, login, logout, reset and change password, manage profile and photo"],
 ["FR2", "Dashboard with balances, limit, repayment status, recent transactions and notifications"],
 ["FR3", "Savings: deposit, withdraw, history, monthly contributions, statement, growth chart"],
 ["FR4", "Loans: apply with live calculation, track status, history, schedule, repay"],
 ["FR5", "Transactions with search and filter; shares module; notifications"],
 ["FR6", "Admin: approve/reject members and loans, view accounts, repayments, manage products, announcements, reports"]],
 [20*mm, 150*mm])
H2("2.5 Non-functional requirements")
L("<b>Security:</b> hashed passwords, role checks, 15-minute idle timeout, no secrets in code.",
  "<b>Usability:</b> Material 3 interface, validation messages, loading and empty states.",
  "<b>Reliability:</b> database transactions keep balances and ledger consistent.",
  "<b>Performance:</b> local database; screens load from reactive flows.",
  "<b>Compatibility:</b> Android 8.0 (API 26) and above.")

H1("Chapter 3: System Design")
H2("3.1 System architecture")
P("The app uses MVVM with a repository. Screens observe state flows from ViewModels. ViewModels call the repository, which enforces every business rule and writes to Room inside database transactions.")
fig(d_arch(), "Figure 3.1: System architecture")
H2("3.2 Use-case diagram")
fig(d_usecase(), "Figure 3.2: Use-case diagram")
H2("3.3 Class diagram")
P("Key classes and their relationships are listed below (full source is in the submitted project).")
TBL([["Layer", "Classes", "Responsibilities"],
 ["Entities", "User, Saving, SaccoProduct, Loan, LoanRepayment, SaccoTransaction, ShareTransaction, AppNotification", "Data records; Loan exposes outstanding balance"],
 ["Data access", "SaccoDao, SaccoDatabase", "Queries and reactive flows"],
 ["Repository", "SaccoRepository", "Registration, login, deposits, withdrawals, shares, loan apply/approve/reject/repay, announcements, seed data"],
 ["Utilities", "Validators, LoanCalculator, PasswordHasher, SessionManager, Notifier", "Validation, loan maths and schedule, hashing, session, system notifications"],
 ["ViewModels", "AuthViewModel, MemberViewModel, AdminViewModel", "Expose UI state; call repository"],
 ["UI", "Auth screens, MemberShell, AdminShell and feature screens", "Compose screens and navigation"]],
 [25*mm, 70*mm, 75*mm])
H2("3.4 ER diagram and database design")
fig(d_er(), "Figure 3.3: Entity-relationship diagram")
P("Members and administrators share the <i>users</i> table; the <i>role</i> column separates them. Foreign keys cascade on delete, except loans.productId which is set to null if a product is removed. Full SQL is in database/schema.sql.")
TBL([["Table", "Main columns", "Relationships"],
 ["users", "memberNumber, fullName, nationalId, phone, email, passwordHash, salt, role, status", "Parent of most tables"],
 ["savings", "type (DEPOSIT/WITHDRAWAL), amount, note, createdAt", "userId -> users"],
 ["loans", "loanNumber, principal, interest, totalRepayable, installment, status, amountRepaid", "userId -> users; productId -> products"],
 ["loan_repayments", "amount, reference, paidAt", "loanId -> loans; userId -> users"],
 ["transactions", "reference, type, amount, description, status", "userId -> users"],
 ["shares", "shares, unitValue, amount", "userId -> users"],
 ["notifications", "title, message, type, isRead", "userId -> users"],
 ["products", "name, interestRate, min/max amount, maxMonths, active", "Parent of loans"]],
 [28*mm, 95*mm, 47*mm])
H2("3.5 Activity diagram: loan lifecycle")
fig(d_activity(), "Figure 3.4: Loan application and repayment activity")
H2("3.6 Navigation structure")
fig(d_nav(), "Figure 3.5: Navigation structure")
H2("3.7 UI design")
P("The interface uses Material 3 with a green brand colour, cards, rounded buttons, bottom navigation, icons, dialogs, lazy lists, loading indicators and empty-state screens. Status is always shown as an icon plus text, never colour alone. Dark mode follows the system setting.")

H1("Chapter 4: Implementation")
H2("4.1 Development environment and technology")
TBL([["Item", "Choice"],
 ["IDE", "Android Studio (latest stable), Gradle Kotlin DSL"], ["Language", "Kotlin 2.0"], ["UI", "Jetpack Compose, Material 3, Navigation Compose"],
 ["Architecture", "MVVM + repository, Kotlin coroutines and Flow"], ["Database", "Room 2.6 over SQLite (via KSP)"], ["Minimum SDK", "API 26 (Android 8.0)"]],
 [40*mm, 130*mm])
H2("4.2 Authentication and security")
L("Registration validates all fields and stores a salted PBKDF2-HMAC-SHA256 hash. Plain passwords are never stored.",
  "Login accepts email, phone or member number. Failed logins return one generic message.",
  "The session stores only the user id and last-activity time and expires after 15 minutes of inactivity.",
  "Roles (MEMBER, ADMIN) route users to separate interfaces. Members cannot reach admin screens. Rejected members cannot log in.",
  "Password reset requires the registered email plus ID number. Change password requires the current password.",
  "allowBackup is disabled, the national ID is masked in the profile, and only simulated data is used.")
H2("4.3 Major modules")
L("<b>Dashboard:</b> savings, loan balance, available limit (3 x savings minus open loans), total contributions, repayment status, recent transactions, notifications.",
  "<b>Savings:</b> deposit and withdraw dialogs, filters, monthly contributions, running-balance statement and a growth line chart drawn with Compose Canvas.",
  "<b>Loans:</b> live quote, application, status tracking, schedule, repayment and history.",
  "<b>Shares:</b> KES 100 per share, minimum 50 shares, progress and purchase history.",
  "<b>Admin:</b> member approval, loan approval/rejection with reason, product management, announcements, reminders, transactions monitor and reports with charts.",
  "<b>Notifications:</b> stored in the database for events and also shown as Android system notifications when permitted.")
H2("4.4 Loan calculation")
P("Flat rate: interest = principal x annual rate x months / 12. Total repayment = principal + interest. Installments = months (monthly) or months x 4 (weekly). Example: KES 20,000 at 10% for 6 months gives interest KES 1,000, total KES 21,000 and six installments of KES 3,500.")
H2("4.5 Important implementation decisions")
L("Room (offline) was chosen so the project runs without a server, which keeps the demo reliable.",
  "All writes go through the repository inside database transactions so balances, ledger and notifications cannot get out of step.",
  "Pure Kotlin objects (Validators, LoanCalculator) were kept free of Android code so they can be unit tested.",
  "New members start as PENDING. They can log in and deposit, but loan applications and withdrawals need administrator approval.")

H1("Chapter 5: Testing")
P("Unit tests are in <i>app/src/test</i> (ValidatorsTest, LoanCalculatorTest, PasswordHasherTest). The manual test cases below follow the demonstration workflow. <b>The Actual result and Pass/Fail columns must be filled in after you run the app</b>, and screenshots attached in Appendix A.")
tests = [
 ("T1", "Register with valid details", "Success dialog; member appears as Pending for admin"),
 ("T2", "Register with weak password / mismatched passwords / bad email", "Field-level error messages; no account created"),
 ("T3", "Register with an existing member number or email", "Message that it is already registered"),
 ("T4", "Login with jane@sacco.demo / Member@123", "Member dashboard opens"),
 ("T5", "Login with wrong password", "Generic invalid-credentials message"),
 ("T6", "Forgot password with correct email + ID", "Password updated; login works with new password"),
 ("T7", "Deposit KES 2,000", "Balance increases; transaction and notification created"),
 ("T8", "Withdraw more than the balance", "Insufficient balance error"),
 ("T9", "Pending member applies for a loan", "Blocked with approval-required message"),
 ("T10", "Apply for loan with amount above limit / below minimum", "Validation error, nothing saved"),
 ("T11", "Apply for KES 10,000 Emergency loan, 6 months monthly", "Summary shows interest 500, total 10,500, installment 1,750; status Pending"),
 ("T12", "Admin approves the pending loan", "Status Active; disbursement transaction; member notified"),
 ("T13", "Admin rejects a loan without a reason", "Reason required error"),
 ("T14", "Member repays an installment", "Outstanding falls; schedule row marked Paid; repayment in history"),
 ("T15", "Repay more than outstanding", "Maximum amount error"),
 ("T16", "Repay until balance is zero", "Status Fully paid; notification sent"),
 ("T17", "Search/filter transactions", "List filters correctly; empty state when none match"),
 ("T18", "Buy shares (e.g. 20)", "Share total, status and history update"),
 ("T19", "Admin approves John Otieno", "Status Active; member notified"),
 ("T20", "Admin posts announcement", "All members receive a notification"),
 ("T21", "Edit profile and change photo and password", "Changes saved; old password rejected when wrong"),
 ("T22", "Logout, then press Back / reopen app", "Login screen shown; no access without signing in"),
 ("T23", "Leave app idle for 15 minutes", "Session expires and login is required"),
 ("T24", "Reports screen", "Totals match the data; charts display"),
]
TBL([["ID", "Test case", "Expected result", "Actual result", "Pass/Fail"]] + [[a, b, c, "", ""] for a, b, c in tests], [10*mm, 55*mm, 62*mm, 28*mm, 15*mm])
H2("5.2 Unit tests")
TBL([["Test class", "What it checks"],
 ["ValidatorsTest", "Email, phone, password strength, confirm password, date of birth, amount range, full registration validation"],
 ["LoanCalculatorTest", "Interest and total, weekly installment count, schedule statuses, partial payments, outstanding balance"],
 ["PasswordHasherTest", "Hash verifies, wrong password fails, salts make hashes differ"]], [45*mm, 125*mm])
H2("5.3 Errors found and corrections")
P("Record here the defects you find while testing and how you fixed them (for example: a validation message that was unclear, or a screen that did not refresh). ______________________________________________")

H1("Chapter 6: Conclusion")
H2("6.1 Achievements")
P("The project delivers a working offline SACCO application with member and administrator roles, a relational database, loan calculation and scheduling, simulated payments, notifications, validation, security controls and reports.")
H2("6.2 Challenges")
L("Keeping balances, ledger entries and notifications consistent, solved with database transactions.", "Designing loan limits and a schedule that tracks partial payments.", "Designing a clear interface for two roles in one app.")
H2("6.3 Recommendations and future enhancements")
L("Add a server (REST API with PostgreSQL) so many devices share one database.", "Integrate mobile money (for example M-Pesa) for real payments.", "Add biometric login and encrypted database storage.",
  "Add guarantors, loan penalties, dividends, statements as PDF, and SMS/push notifications.", "Add instrumented UI tests.")

H1("Appendix A: Screenshots")
P("Insert the 19 full-page screenshots from the running app here (see tools/SCREENSHOT_CHECKLIST.md). They must show the application running, not source code.")
H1("Appendix B: Demo accounts")
TBL([["Role", "Login", "Password"], ["Member (active)", "jane@sacco.demo", "Member@123"], ["Member (pending)", "john@sacco.demo", "Member@123"], ["Administrator", "admin@sacco.demo", "Admin@123"]], [45*mm, 70*mm, 55*mm])

def foot(c, d):
    c.setFont("Helvetica", 8); c.setFillColor(colors.grey); c.drawCentredString(A4[0]/2, 12*mm, "SACCO Connect - Project Report | Page %d" % d.page)
doc = SimpleDocTemplate("docs/SACCO_Project_Documentation.pdf", pagesize=A4, leftMargin=20*mm, rightMargin=20*mm, topMargin=18*mm, bottomMargin=20*mm, title="SACCO Connect Project Report")
doc.build(S, onFirstPage=foot, onLaterPages=foot)
