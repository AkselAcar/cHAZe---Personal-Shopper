package com.epfl.esl.chaze.features.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object Idle : AuthState()
    object InProgress : AuthState()
    data class Success(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
    data class UserAlreadyExists(val email: String) : AuthState()
}

data class UserProfile(
    val uid: String,
    val fullName: String,
    val username: String,
    val email: String
)

class LoginProfileViewModel : ViewModel() {

    private val auth: FirebaseAuth = Firebase.auth
    private val db = Firebase.firestore

    private val _emailOrPhone = MutableLiveData("")
    val emailOrPhone: LiveData<String> = _emailOrPhone

    private val _password = MutableLiveData("")
    val password: LiveData<String> = _password

    private val _confirmPassword = MutableLiveData("")
    val confirmPassword: LiveData<String> = _confirmPassword

    private val _fullName = MutableLiveData("")
    val fullName: LiveData<String> = _fullName

    private val _username = MutableLiveData("")
    val username: LiveData<String> = _username

    private val _agreeToConditions = MutableLiveData(false)
    val agreeToConditions: LiveData<Boolean> = _agreeToConditions

    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    fun onEmailOrPhoneChanged(value: String) {
        _emailOrPhone.value = value
    }

    fun onPasswordChanged(value: String) {
        _password.value = value
    }

    fun onConfirmPasswordChanged(value: String) {
        _confirmPassword.value = value
    }

    fun onFullNameChanged(value: String) {
        _fullName.value = value
    }

    fun onUsernameChanged(value: String) {
        _username.value = value
    }

    fun onAgreeToConditionsChanged(value: Boolean) {
        _agreeToConditions.value = value
    }

    fun signUp() {
        val email = emailOrPhone.value ?: ""
        val password = password.value ?: ""
        val confirmPassword = confirmPassword.value ?: ""
        val fullName = fullName.value ?: ""
        val username = username.value ?: ""
        val agreeToConditions = agreeToConditions.value ?: false

        if (email.isBlank() || password.isBlank() || fullName.isBlank() || username.isBlank()) {
            _authState.value = AuthState.Error("Please fill all fields.")
            return
        }
        if (password != confirmPassword) {
            _authState.value = AuthState.Error("Passwords do not match.")
            return
        }
        if (!agreeToConditions) {
            _authState.value = AuthState.Error("You must agree to the user conditions.")
            return
        }

        _authState.value = AuthState.InProgress
        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                result.user?.let { user ->
                    val userProfile = UserProfile(user.uid, fullName, username, email)
                    db.collection("users").document(user.uid).set(userProfile).await()
                    _authState.value = AuthState.Success(user)
                } ?: run {
                    _authState.value = AuthState.Error("Sign up failed.")
                }
            } catch (_: FirebaseAuthUserCollisionException) {
                _authState.value = AuthState.UserAlreadyExists(email)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "An unknown error occurred.")
            }
        }
    }

    fun logInWithEmailPassword() {
        val email = emailOrPhone.value ?: ""
        val password = password.value ?: ""

        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Please enter email and password.")
            return
        }
        _authState.value = AuthState.InProgress
        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                result.user?.let { user ->
                    _authState.value = AuthState.Success(user)
                } ?: run {
                    _authState.value = AuthState.Error("Login failed.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "An unknown error occurred.")
            }
        }
    }
    
    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }
}
