package com.sacco.app.util

import java.time.LocalDate
import java.time.Period

/** Pure validation rules (no Android dependencies, so they are unit-testable). Each returns an error message or null. */
object Validators {
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val PHONE = Regex("^(?:\\+254|0)[17]\\d{8}$")
    private val ID = Regex("^[A-Za-z0-9]{6,12}$")
    private val MEMBER_NO = Regex("^[A-Za-z0-9-]{4,12}$")

    fun required(v: String, label: String) = if (v.isBlank()) "$label is required" else null
    fun email(v: String) = when {
        v.isBlank() -> "Email is required"
        !EMAIL.matches(v.trim()) -> "Enter a valid email address"
        else -> null
    }
    fun phone(v: String) = when {
        v.isBlank() -> "Phone number is required"
        !PHONE.matches(v.trim()) -> "Use a valid Kenyan number, e.g. 0712345678"
        else -> null
    }
    fun nationalId(v: String) = when {
        v.isBlank() -> "National ID/passport is required"
        !ID.matches(v.trim()) -> "Enter 6-12 letters or digits"
        else -> null
    }
    fun memberNumber(v: String) = when {
        v.isBlank() -> "Member number is required"
        !MEMBER_NO.matches(v.trim()) -> "Use 4-12 letters, digits or dashes, e.g. SAC-0100"
        else -> null
    }
    fun fullName(v: String) = when {
        v.isBlank() -> "Full name is required"
        v.trim().length < 3 || !v.trim().contains(" ") -> "Enter your first and last name"
        else -> null
    }
    fun password(v: String) = when {
        v.length < 8 -> "At least 8 characters"
        !v.any { it.isUpperCase() } -> "Add an uppercase letter"
        !v.any { it.isLowerCase() } -> "Add a lowercase letter"
        !v.any { it.isDigit() } -> "Add a digit"
        else -> null
    }
    fun confirm(p: String, c: String) = if (p != c) "Passwords do not match" else null

    /** Date must be yyyy-MM-dd, in the past, and the member must be at least 18. */
    fun dateOfBirth(v: String, today: LocalDate = LocalDate.now()): String? {
        if (v.isBlank()) return "Date of birth is required"
        val d = try { LocalDate.parse(v.trim()) } catch (e: Exception) { return "Use the format yyyy-MM-dd" }
        if (!d.isBefore(today)) return "Date of birth must be in the past"
        if (Period.between(d, today).years < 18) return "You must be at least 18 years old"
        return null
    }

    fun amount(v: Double, min: Double, max: Double): String? = when {
        v.isNaN() || v <= 0.0 -> "Enter a valid amount"
        v < min -> "Minimum amount is KES ${"%,.0f".format(min)}"
        v > max -> "Maximum amount is KES ${"%,.0f".format(max)}"
        else -> null
    }

    /** Validates the personal details (no password). Used by registration and by Google sign-in profile completion. */
    fun validateProfileFields(
        memberNumber: String, fullName: String, nationalId: String, phone: String, email: String,
        gender: String, dob: String, address: String
    ): Map<String, String> {
        val e = linkedMapOf<String, String>()
        memberNumber(memberNumber)?.let { e["memberNumber"] = it }
        fullName(fullName)?.let { e["fullName"] = it }
        nationalId(nationalId)?.let { e["nationalId"] = it }
        phone(phone)?.let { e["phone"] = it }
        email(email)?.let { e["email"] = it }
        required(gender, "Gender")?.let { e["gender"] = it }
        dateOfBirth(dob)?.let { e["dob"] = it }
        required(address, "Address")?.let { e["address"] = it }
        return e
    }

    /** Returns field-name to error-message for every invalid registration field. */
    fun validateRegistration(
        memberNumber: String, fullName: String, nationalId: String, phone: String, email: String,
        gender: String, dob: String, address: String, password: String, confirm: String
    ): Map<String, String> {
        val e = LinkedHashMap(validateProfileFields(memberNumber, fullName, nationalId, phone, email, gender, dob, address))
        password(password)?.let { e["password"] = it }
        confirm(password, confirm)?.let { e["confirm"] = it }
        return e
    }
}
