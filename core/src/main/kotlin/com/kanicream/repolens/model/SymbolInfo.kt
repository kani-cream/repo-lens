package com.kanicream.repolens.model

/**
 * Symbol a finding points at (e.g. `PaymentService.processPayment()`).
 * Tier 0 analyzers produce findings without symbols; structure analyzers fill this in.
 */
data class SymbolInfo(val displayName: String)
