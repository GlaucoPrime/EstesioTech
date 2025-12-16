package com.code.EstesioTech

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
            .addOnFailureListener { e ->
                val msg = when {
                    e.message?.contains("user-not-found") == true -> "CRM não encontrado."
                    e.message?.contains("wrong-password") == true -> "Senha incorreta."
                    else -> "Erro: ${e.message}"
                }
                onError(msg)
            }
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
            .addOnFailureListener { e -> onError(e.message ?: "Erro no cadastro") }
    }

    // --- RECUPERAR SENHA ---
    fun sendPasswordReset(crm: String, uf: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val fakeEmail = "${crm}_${uf}$APP_DOMAIN"
        auth.sendPasswordResetEmail(fakeEmail)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Erro ao enviar") }
    }

    fun logout() {
        auth.signOut()
    }

    fun getUserName(onResult: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val fullName = document.getString("name") ?: "Doutor(a)"
                onResult(fullName.split(" ").firstOrNull() ?: fullName)
            }
            .addOnFailureListener { onResult("Doutor(a)") }
    }

    // --- SALVAR TESTE ---
    fun saveTestResult(
        patientId: String,
        patientName: String,
        bodyPart: String,
        results: Map<Int, Int>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: return onError("Usuário desconectado.")

        val validValues = results.values.filter { it > 0 }
        val average = if (validValues.isNotEmpty()) validValues.average() else 0.0
        val maxLevel = results.values.maxOrNull() ?: 0
        val gif = if (maxLevel >= 4) 1 else 0

        // Converte chaves para String para o Firebase aceitar
        val pointsDataConverted = results.entries.associate { (key, value) ->
            key.toString() to value
        }

        val testMap = hashMapOf(
            "doctorId" to userId,
            "patientId" to patientId,
            "patientName" to patientName,
            "bodyPart" to bodyPart,
            "date" to Date(),
            "averageLevel" to average,
            "gif" to gif,
            "pointsData" to pointsDataConverted,
            "status" to "completed"
        )

        db.collection("tests")
            .add(testMap)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Erro ao salvar") }
    }

    // --- BUSCAR HISTÓRICO (NOVO) ---
    fun getDoctorHistory(onSuccess: (List<Map<String, Any>>) -> Unit, onError: (String) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onError("Usuário não logado.")

        // Busca testes onde o 'doctorId' é o usuário atual
        db.collection("tests")
            .whereEqualTo("doctorId", userId)
            .get()
            .addOnSuccessListener { result ->
                val tests = result.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    // Convertendo Timestamp para Date se necessário para ordenação
                    data["id"] = doc.id
                    data
                }.sortedByDescending {
                    // Ordenação em memória para evitar erro de índice no Firebase
                    (it["date"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(0)
                }
                onSuccess(tests)
            }
            .addOnFailureListener { e -> onError(e.message ?: "Erro ao buscar histórico") }
    }
}