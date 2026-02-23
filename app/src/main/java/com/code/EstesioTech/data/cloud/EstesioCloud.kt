package com.code.EstesioTech.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

object EstesioCloud {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun getCurrentUserId(): String? = auth.currentUser?.uid
    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    // --- AUTENTICAÇÃO ---
    fun login(crm: String, uf: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val docId = "${crm}_${uf}" // Busca pela nova organização limpa
        db.collection("users").document(docId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onError("CRM não encontrado.")
                } else {
                    val email = doc.getString("recoveryEmail")
                    if (email != null) {
                        auth.signInWithEmailAndPassword(email, pass)
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { onError("Senha incorreta.") }
                    } else onError("Cadastro inválido.")
                }
            }
            .addOnFailureListener { onError("Erro de conexão.") }
    }

    fun register(crm: String, uf: String, pass: String, name: String, recoveryEmail: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(recoveryEmail, pass).addOnSuccessListener { res ->
            val uid = res.user?.uid ?: return@addOnSuccessListener
            val docId = "${crm}_${uf}" // IDENTIFICAÇÃO BONITA NO BANCO (Ex: 123456_PE)

            val data = hashMapOf(
                "uid" to uid, // Guarda o UID por segurança internamente
                "name" to name, "crm" to crm, "uf" to uf,
                "recoveryEmail" to recoveryEmail, "role" to "medico"
            )
            db.collection("users").document(docId).set(data).addOnSuccessListener { onSuccess() }
        }.addOnFailureListener { onError(it.message ?: "Erro desconhecido") }
    }

    fun sendPasswordResetByCrm(crm: String, uf: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val docId = "${crm}_${uf}"
        db.collection("users").document(docId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) onError("CRM não encontrado.")
                else {
                    val email = doc.getString("recoveryEmail")
                    if (!email.isNullOrEmpty()) {
                        auth.sendPasswordResetEmail(email).addOnSuccessListener { onSuccess() }.addOnFailureListener { onError(it.message ?: "Erro") }
                    } else onError("Sem e-mail cadastrado.")
                }
            }
            .addOnFailureListener { onError("Erro de conexão.") }
    }

    fun logout() = auth.signOut()

    fun getUserName(onResult: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult("Doutor(a)")
        // Busca o médico pelo UID salvo internamente
        db.collection("users").whereEqualTo("uid", uid).get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val fullName = docs.documents[0].getString("name") ?: "Doutor(a)"
                    onResult(fullName.split(" ").firstOrNull() ?: fullName)
                } else onResult("Doutor(a)")
            }
            .addOnFailureListener { onResult("Doutor(a)") }
    }

    // --- PACIENTES ---

    fun checkPatient(cpf: String, onFound: (String, String) -> Unit, onNotFound: () -> Unit, onError: (String) -> Unit) {
        checkPatient(cpf, { name, email, _ -> onFound(name, email) }, onNotFound, onError)
    }

    fun checkPatient(cpf: String, onFound: (String, String, String) -> Unit, onNotFound: () -> Unit, onError: (String) -> Unit) {
        // Busca 100% segura apenas pelo campo, sem expor ID do documento
        db.collection("patients").whereEqualTo("cpf", cpf).get()
            .addOnSuccessListener { query ->
                if (!query.isEmpty) {
                    val d = query.documents[0]
                    onFound(d.getString("name") ?: "", d.getString("email") ?: "", d.getString("age") ?: "")
                } else {
                    onNotFound()
                }
            }
            .addOnFailureListener { onError("Erro de conexão na busca.") }
    }

    fun createPatient(cpf: String, name: String, age: String, email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val data = hashMapOf(
            "cpf" to cpf, "name" to name, "age" to age, "email" to email,
            "createdAt" to Date(), "createdBy" to (auth.currentUser?.uid ?: "unknown")
        )
        // Usa .add() para gerar um ID seguro e aleatório, escondendo o CPF (LGPD)
        db.collection("patients").add(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Erro ao salvar") }
    }

    fun saveCompleteSession(sessionId: String, patientCpf: String, patientName: String, allResults: Map<String, Map<Int, Int>>, hasDeformities: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onError("Não logado")
        val batch = db.batch()
        val now = Date()

        allResults.forEach { (bodyPart, results) ->
            // O sessionId agora é limpo (Ex: 050325_1430), criando um teste: 050325_1430_mao_direita
            val testId = "${sessionId}_${bodyPart}"
            val docRef = db.collection("tests").document(testId)
            val maxLevel = results.values.maxOrNull() ?: 0
            val risk = if (hasDeformities) 2 else if (maxLevel >= 5) 1 else 0

            val data = hashMapOf(
                "sessionId" to sessionId, "patientCpf" to patientCpf, "patientName" to patientName,
                "doctorId" to uid, "bodyPart" to bodyPart, "date" to now, "gif" to risk,
                "hasDeformities" to hasDeformities,
                "pointsData" to results.entries.associate { (k,v) -> k.toString() to v }
            )
            batch.set(docRef, data)
        }
        batch.commit().addOnSuccessListener { onSuccess() }.addOnFailureListener { onError(it.message ?: "Erro") }
    }

    fun deleteSession(sessionId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("tests").whereEqualTo("sessionId", sessionId).get()
            .addOnSuccessListener { q ->
                val batch = db.batch()
                q.documents.forEach { batch.delete(it.reference) }
                batch.commit().addOnSuccessListener { onSuccess() }.addOnFailureListener { onError("Erro ao deletar") }
            }
            .addOnFailureListener { onError("Erro de conexão") }
    }

    fun getGroupedHistory(onSuccess: (List<PatientHistoryData>) -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onError("Não logado")
        db.collection("tests").whereEqualTo("doctorId", uid).get()
            .addOnSuccessListener { result ->
                val patientsMap = mutableMapOf<String, PatientHistoryData>()
                for (doc in result.documents) {
                    val cpf = doc.getString("patientCpf") ?: continue
                    val name = doc.getString("patientName") ?: "Desconhecido"
                    val sessionId = doc.getString("sessionId") ?: continue
                    val date = doc.getTimestamp("date")?.toDate() ?: Date()
                    val gif = (doc.getLong("gif") ?: 0).toInt()

                    val p = patientsMap.getOrPut(cpf) { PatientHistoryData(name, cpf, mutableListOf()) }
                    val sessions = p.sessions as MutableList<SessionData>
                    var s = sessions.find { it.sessionId == sessionId }
                    if (s == null) { s = SessionData(sessionId, date, 0); sessions.add(s) }
                    if (gif > s.maxGif) s.maxGif = gif
                }

                val finalSortedList = patientsMap.values.map { patient ->
                    patient.copy(sessions = patient.sessions.sortedByDescending { it.date })
                }.sortedByDescending { it.sessions.firstOrNull()?.date ?: Date(0) }

                onSuccess(finalSortedList)
            }
            .addOnFailureListener { onError(it.message ?: "Erro ao buscar") }
    }

    fun getRecentPatients(limit: Int = 3, onSuccess: (List<Map<String, Any>>) -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onError("Não logado")
        db.collection("tests").whereEqualTo("doctorId", uid).get()
            .addOnSuccessListener { result ->
                val recent = mutableListOf<Map<String, Any>>()
                val seen = mutableSetOf<String>()
                val sorted = result.documents.sortedByDescending { it.getTimestamp("date")?.toDate() ?: Date(0) }

                for (doc in sorted) {
                    val cpf = doc.getString("patientCpf") ?: continue
                    if (seen.contains(cpf)) continue
                    recent.add(mapOf("name" to (doc.getString("patientName") ?: "Paciente"), "cpf" to cpf, "lastExam" to (doc.getTimestamp("date")?.toDate() ?: Date())))
                    seen.add(cpf)
                    if (recent.size >= limit) break
                }
                onSuccess(recent)
            }
            .addOnFailureListener { onError("Erro") }
    }

    fun getFullSessionData(sessionId: String, onSuccess: (List<Map<String, Any>>) -> Unit, onError: (String) -> Unit) {
        db.collection("tests").whereEqualTo("sessionId", sessionId).get()
            .addOnSuccessListener { onSuccess(it.documents.mapNotNull { d -> d.data }) }
            .addOnFailureListener { onError(it.message ?: "Erro ao baixar dados") }
    }
}

// CLASSES DE DADOS MANTIDAS
data class PatientHistoryData(val name: String, val cpf: String, val sessions: List<SessionData>)
data class SessionData(val sessionId: String, val date: Date, var maxGif: Int)