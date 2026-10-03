package com.mamblxon.autosense.domain.usecase
sealed interface ValidationResult {

    data object Valid : ValidationResult

    data class Invalid(val error: String) : ValidationResult

    val isValid: Boolean get() = this is Valid
}