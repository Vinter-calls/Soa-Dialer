package com.dialer.truedialer.util

object PhoneNumberUtils {

    /**
     * Normalizes a phone number by removing spaces, brackets, hyphens, and non-phone characters.
     */
    fun normalize(phoneNumber: String): String {
        return phoneNumber.replace("[^0-9+]".toRegex(), "")
    }

    /**
     * Extracts national phone digits (standard 10 digits for mobile numbers in India/US, or raw digits if shorter).
     */
    fun extractNationalNumber(phoneNumber: String?): String {
        if (phoneNumber.isNullOrBlank()) return ""
        val digits = phoneNumber.replace("[^0-9]".toRegex(), "")
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    /**
     * Compares two phone numbers to see if they match, taking into account optional country codes.
     * Requires exact match for full numbers, or matching last 10 digits for full mobile numbers.
     * Prevents false collisions caused by comparing only 7-8 digits.
     */
    fun areNumbersEqual(num1: String?, num2: String?): Boolean {
        if (num1.isNullOrBlank() || num2.isNullOrBlank()) return false
        val digits1 = num1.replace("[^0-9]".toRegex(), "")
        val digits2 = num2.replace("[^0-9]".toRegex(), "")
        if (digits1 == digits2) return true

        if (digits1.length >= 10 && digits2.length >= 10) {
            return digits1.takeLast(10) == digits2.takeLast(10)
        }

        return digits1 == digits2
    }

    /**
     * Formats phone number for WhatsApp deep link.
     */
    fun getWhatsAppUri(phoneNumber: String): String {
        var clean = normalize(phoneNumber).replace("+", "")
        if (clean.length == 10) {
            // Prepend India default country code without + if 10 digits
            clean = "91$clean"
        }
        return "https://api.whatsapp.com/send?phone=$clean"
    }

    /**
     * Pretty print formatting for standard numbers.
     */
    fun formatDisplay(phoneNumber: String): String {
        if (phoneNumber.isBlank() || phoneNumber.equals("Unknown Number", ignoreCase = true)) {
            return "Unknown Number"
        }
        val clean = normalize(phoneNumber)
        if (clean.isBlank()) return phoneNumber
        return if (clean.length == 10) {
            "${clean.substring(0, 5)} ${clean.substring(5)}"
        } else {
            clean
        }
    }
}
