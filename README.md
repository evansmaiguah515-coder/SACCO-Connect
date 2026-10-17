# SACCO Connect (STU 3102 Mobile Application Development)

Offline SACCO app for members and administrators. Android, Kotlin, Jetpack Compose (Material 3), MVVM, Room (SQLite).
All data is simulated demo data. No real money, IDs or bank accounts are used.

## Run it
1. Install the latest stable Android Studio.
2. File > Open > select this `SaccoConnect` folder. Let Gradle sync (needs internet the first time).
3. Run on an emulator (API 26+) or phone.
4. APK: Build > Build Bundle(s) / APK(s) > Build APK(s). The file is in `app/build/outputs/apk/debug/app-debug.apk`.
5. Unit tests: right-click `app/src/test` > Run Tests (or `./gradlew test`).

## Demo accounts (created on first launch)
| Role | Login | Password |
|---|---|---|
| Member (active, has savings and a loan) | jane@sacco.demo | Member@123 |
| Member (awaiting approval) | john@sacco.demo | Member@123 |
| Administrator | admin@sacco.demo | Admin@123 |

## Demo workflow
Register > (Admin: approve member) > Login > Dashboard > Savings deposit > Apply for loan > (Admin: approve) > Loan detail > Repay > Transactions > Notifications > Logout.
To reset the demo data: Settings > Apps > SACCO Connect > Storage > Clear data.

## Structure
- `data/` Room entities, DAO, database, repository (all business rules)
- `util/` validators, loan calculator, password hashing, session, notifications
- `viewmodel/` Auth, Member and Admin view models
- `ui/` theme, reusable components, navigation, screens
