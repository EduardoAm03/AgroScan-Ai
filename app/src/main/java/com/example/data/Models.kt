package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DiagnosisResult(
    val cropName: String,
    val disease: String,
    val confidence: Int,
    val symptoms: String,
    val causes: String,
    val treatmentsChemical: List<String>,
    val treatmentsOrganic: List<String>,
    val treatmentsPreventive: List<String>
)
