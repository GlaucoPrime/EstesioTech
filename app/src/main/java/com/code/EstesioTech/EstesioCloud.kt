package com.code.EstesioTech

import android.util.Log
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
        db.collection("users").whereEqualTo("crm", crm).whereEqualTo("uf", uf).get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) onError("CRM não encontrado.")
                else {
                    val email = docs.documents[0].getString("recoveryEmail")
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
            val data = hashMapOf("name" to name, "crm" to crm, "uf" to uf, "recoveryEmail" to recoveryEmail, "role" to "medico")
            db.collection("users").document(uid).set(data).addOnSuccessListener { onSuccess() }
        }.addOnFailureListener { onError(it.message ?: "Erro desconhecido") }
    }

    fun sendPasswordResetByCrm(crm: String, uf: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("users").whereEqualTo("crm", crm).whereEqualTo("uf", uf).get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) onError("CRM não encontrado.")
                else {
                    val email = docs.documents[0].getString("recoveryEmail")
                    if (!email.isNullOrEmpty()) {
                        auth.sendPasswordResetEmail(email).addOnSuccessListener { onSuccess() }.addOnFailureListener { onError(it.message ?: "Erro") }
                    } else onError("Sem e-mail cadastrado.")
                }
            }
            .addOnFailureListener { onError("Erro de conexão.") }
    }

    fun logout() = auth.signOut()

    fun getUserName(onResult: (String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { onResult(it.getString("name")?.split(" ")?.firstOrNull() ?: "Doutor(a)") }
                .addOnFailureListener { onResult("Doutor(a)") }
        } else onResult("Doutor(a)")
    }

    // --- PACIENTES ---

    fun checkPatient(cpf: String, onFound: (String, String) -> Unit, onNotFound: () -> Unit, onError: (String) -> Unit) {
        checkPatient(cpf, { name, email, _ -> onFound(name, email) }, onNotFound, onError)
    }

    fun checkPatient(cpf: String, onFound: (String, String, String) -> Unit, onNotFound: () -> Unit, onError: (String) -> Unit) {
        // TENTATIVA 1: ID
        db.collection("patients").document(cpf).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    onFound(doc.getString("name") ?: "", doc.getString("email") ?: "", doc.getString("age") ?: "")
                } else {
                    // TENTATIVA 2: Campo String
                    db.collection("patients").whereEqualTo("cpf", cpf).get()
                        .addOnSuccessListener { q1 ->
                            if (!q1.isEmpty) {
                                val d = q1.documents[0]
                                onFound(d.getString("name") ?: "", d.getString("email") ?: "", d.getString("age") ?: "")
                            } else {
                                // TENTATIVA 3: Campo Number (Para o caso do "2")
                                val cpfNum = cpf.toLongOrNull()
                                if (cpfNum != null) {
                                    db.collection("patients").whereEqualTo("cpf", cpfNum).get()
                                        .addOnSuccessListener { q2 ->
                                            if (!q2.isEmpty) {
                                                val d = q2.documents[0]
                                                onFound(d.getString("name") ?: "", d.getString("email") ?: "", d.getString("age") ?: "")
                                            } else onNotFound()
                                        }
                                        .addOnFailureListener { onNotFound() }
                                } else {
                                    onNotFound()
                                }
                            }
                        }
                        .addOnFailureListener { onError("Erro na busca") }
                }
            }
            .addOnFailureListener { onError("Erro de conexão") }
    }

    fun createPatient(cpf: String, name: String, age: String, email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val data = hashMapOf(
            "cpf" to cpf, "name" to name, "age" to age, "email" to email,
            "createdAt" to Date(), "createdBy" to (auth.currentUser?.uid ?: "unknown")
        )
        db.collection("patients").document(cpf).set(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Erro") }
    }

    fun saveCompleteSession(sessionId: String, patientCpf: String, patientName: String, allResults: Map<String, Map<Int, Int>>, hasDeformities: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onError("Não logado")
        val batch = db.batch()
        val now = Date()

        allResults.forEach { (bodyPart, results) ->
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

                // Ordenação segura (Criando novas listas em vez de tentar modificar a List imutável)
                val finalSortedList = patientsMap.values.map { patient ->
                    val sortedSessions = patient.sessions.sortedByDescending { it.date }
                    patient.copy(sessions = sortedSessions)
                }.sortedByDescending {
                    it.sessions.firstOrNull()?.date ?: Date(0)
                }

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

// CLASSES DE DADOS (IMPORTANTE: MANTENHA AQUI)
data class PatientHistoryData(val name: String, val cpf: String, val sessions: List<SessionData>)
data class SessionData(val sessionId: String, val date: Date, var maxGif: Int)