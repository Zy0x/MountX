package app.mihon.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mihon.data.model.UpdateInfo
import app.mihon.util.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val updateChecker: UpdateChecker
) : ViewModel() {

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _checkError = MutableStateFlow<String?>(null)
    val checkError: StateFlow<String?> = _checkError.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            _isChecking.value = true
            _checkError.value = null
            val result = updateChecker.checkForUpdate()
            _isChecking.value = false
            if (result.isSuccess) {
                _updateInfo.value = result.getOrNull()
            } else {
                _checkError.value = result.exceptionOrNull()?.message ?: "Check failed"
            }
        }
    }
}
