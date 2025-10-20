package com.ips.dataacquisition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ips.dataacquisition.data.model.Bonus
import com.ips.dataacquisition.data.repository.BonusRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BonusViewModel(
    private val bonusRepository: BonusRepository
) : ViewModel() {
    
    private val _bonuses = MutableStateFlow<List<Bonus>>(emptyList())
    val bonuses: StateFlow<List<Bonus>> = _bonuses.asStateFlow()
    
    private val _totalBonus = MutableStateFlow(0.0)
    val totalBonus: StateFlow<Double> = _totalBonus.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    init {
        loadBonuses()
    }
    
    private fun loadBonuses() {
        viewModelScope.launch {
            _isLoading.value = true
            bonusRepository.getAllBonuses()
                .collect { bonusList ->
                    _bonuses.value = bonusList
                    _totalBonus.value = bonusList.sumOf { it.amount }
                    // Set loading to false after first emission
                    _isLoading.value = false
                }
        }
    }
    
    fun refreshBonuses() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                bonusRepository.fetchBonusesFromServer()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

