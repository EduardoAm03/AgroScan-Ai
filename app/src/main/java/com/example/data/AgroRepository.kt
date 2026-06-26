package com.example.data

import kotlinx.coroutines.flow.Flow

class AgroRepository(
    private val diagnosisDao: DiagnosisDao,
    private val portfolioDao: PortfolioDao
) {
    // --- Diagnoses ---
    val allDiagnoses: Flow<List<DiagnosisEntity>> = diagnosisDao.getAllDiagnoses()

    suspend fun insertDiagnosis(diagnosis: DiagnosisEntity): Long {
        return diagnosisDao.insertDiagnosis(diagnosis)
    }

    suspend fun deleteDiagnosisById(id: Long) {
        diagnosisDao.deleteDiagnosisById(id)
    }

    suspend fun clearAllDiagnoses() {
        diagnosisDao.clearAllDiagnoses()
    }

    // --- Portfolio ---
    val allPortfolioItems: Flow<List<PortfolioItemEntity>> = portfolioDao.getAllPortfolioItems()

    suspend fun insertPortfolioItem(item: PortfolioItemEntity): Long {
        return portfolioDao.insertPortfolioItem(item)
    }

    suspend fun deletePortfolioItemById(id: Long) {
        portfolioDao.deletePortfolioItemById(id)
    }

    suspend fun clearAllPortfolioItems() {
        portfolioDao.clearAllPortfolioItems()
    }
}
