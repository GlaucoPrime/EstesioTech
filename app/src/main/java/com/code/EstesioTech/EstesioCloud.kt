package com.code.EstesioTech

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

object EstesioCloud {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private const val APP_DOMAIN = "@estesiotech.app"

    fun isUserLoggedIn(): Boolean = auth.currentUser != null
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    // --- LOGIN ---
    fun login(crm: String, uf: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val cleanCrm = crm.trim()
        if (cleanCrm.isEmpty() || uf.isEmpty()) {
            onError("CRM e Estado são obrigatórios.")
            return
        }
        val fakeEmail = "${cleanCrm}_${uf}$APP_DOMAIN"
        auth.signInWithEmailAndPassword(fakeEmail, pass)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Erro no login: ${e.message}") }
    }

    // --- RECUPERAÇÃO DE SENHA ---
    // ATENÇÃO: Como usamos "fake email" (crm@estesiotech), o email de reset vai para o limbo.
    // Para funcionar de verdade, o usuário precisaria ter cadastrado um email REAL no Auth.
    // Implementei a lógica, mas ela só enviará o email se o "fakeEmail" fosse real.
    // Sugestão: Criar um campo "Email de Recuperação" no cadastro futuro.
    fun sendPasswordReset(emailReal: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (emailReal.isEmpty()) {
            onError("Digite seu e-mail.")
            return
        }
        auth.sendPasswordResetEmail(emailReal)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("Erro ao enviar: ${e.message}") }
    }

    // --- OBTER NOME DO USUÁRIO ---
    fun getUserName(onResult: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("name") ?: "Doutor(a)"
                    // Pega só o primeiro nome
                    onResult(name.split(" ").firstOrNull() ?: name)
                }
            }
            .addOnFailureListener { onResult("Doutor(a)") }
    }

    // --- REGISTRO ---
    fun register(crm: String, uf: String, pass: String, name: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val cleanCrm = crm.trim()
        val fakeEmail = "${cleanCrm}_${uf}$APP_DOMAIN"

        auth.createUserWithEmailAndPassword(fakeEmail, pass)
            .addOnSuccessListener { result ->
                val userId = result.user?.uid
                if (userId != null) {
                    val userMap = hashMapOf(
                        "name" to name,
                        "crm" to cleanCrm,
                        "uf" to uf,
                        "role" to "profissional_saude",
                        "createdAt" to Date()
                    )
                    db.collection("users").document(userId).set(userMap)
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onSuccess() }
                }
            }
            .addOnFailureListener { e -> onError("Erro no cadastro: ${e.message}") }
    }

    fun logout() {
        auth.signOut()
    }

    fun saveTestResult(bodyPart: String, results: Map<Int, Int>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onError("Usuário desconectado.")
        val validValues = results.values.filter { it > 0 }
        val average = if (validValues.isNotEmpty()) validValues.average() else 0.0

        val testMap = hashMapOf(
            "userId" to userId,
            "bodyPart" to bodyPart,
            "date" to Date(),
            "averageLevel" to average,
            "pointsData" to results,
            "status" to "completed"
        )

        db.collection("tests").add(testMap)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Erro ao salvar") }
    }
}