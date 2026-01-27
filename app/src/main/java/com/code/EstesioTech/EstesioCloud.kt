package com.code.EstesioTech

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date

object EstesioCloud {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun getCurrentUserId(): String? = auth.currentUser?.uid
    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    // --- AUTENTICAÇÃO ---

    fun login(crm: String, uf: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("users")
            .whereEqualTo("crm", crm)
            .whereEqualTo("uf", uf)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    onError("CRM não encontrado.")
                } else {
                    val email = docs.documents[0].getString("recoveryEmail")
                    if (email != null) {
                        auth.signInWithEmailAndPassword(email, pass)
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { onError("Senha incorreta.") }
                    } else {
                        onError("Cadastro inválido.")
                    }
                }
            }
            .addOnFailureListener { onError("Erro de conexão.") }
    }

    fun register(crm: String, uf: String, pass: String, name: String, recoveryEmail: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(recoveryEmail, pass).addOnSuccessListener { res ->
            val uid = res.user?.uid ?: return@addOnSuccessListener
            val data = hashMapOf(
                "name" to name, "crm" to crm, "uf" to uf, "recoveryEmail" to recoveryEmail, "role" to "medico"
            )
            db.collection("users").document(uid).set(data).addOnSuccessListener { onSuccess() }
        }.addOnFailureListener { onError("Erro: ${it.message}") }
    }

    fun sendPasswordResetByCrm(crm: String, uf: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("users")
            .whereEqualTo("crm", crm)
            .whereEqualTo("uf", uf)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    onError("CRM não encontrado na base de dados.")
                } else {
                    val email = docs.documents[0].getString("recoveryEmail")
                    if (!email.isNullOrEmpty()) {
                        auth.sendPasswordResetEmail(email)
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { onError("Erro no envio: ${it.message}") }
                    } else {
                        onError("Este médico não possui e-mail cadastrado.")
                    }
                }
            }
            .addOnFailureListener {
                onError("Erro de conexão. Verifique sua internet.")
            }
    }

    fun sendPasswordResetByEmail(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Erro ao enviar.") }
    }

    fun logout() = auth.signOut()

    fun getUserName(onResult: (String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener {
                val fullName = it.getString("name") ?: "Doutor(a)"
                onResult(fullName.split(" ").firstOrNull() ?: fullName)
            }.addOnFailureListener { onResult("Doutor(a)") }
        } else {
            onResult("Doutor(a)")
        }
    }

    // --- PACIENTES E EXAMES ---

    fun checkPatient(cpf: String, onFound: (String, String) -> Unit, onNotFound: () -> Unit, onError: (String) -> Unit) {
        checkPatient(cpf, { name, email, _ -> onFound(name, email) }, onNotFound, onError)
    }

    fun checkPatient(cpf: String, onFound: (String, String, String) -> Unit, onNotFound: () -> Unit, onError: (String) -> Unit) {
        db.collection("patients").document(cpf).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    onFound(doc.getString("name") ?: "", doc.getString("email") ?: "", doc.getString("age") ?: "")
                } else {
                    onNotFound()
                }
            }
            .addOnFailureListener { onError("Erro ao buscar paciente.") }
    }

    fun createPatient(cpf: String, name: String, age: String, email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val data = hashMapOf(
            "cpf" to cpf, "name" to name, "age" to age, "email" to email,
            "createdAt" to Date(), "createdBy" to (auth.currentUser?.uid ?: "unknown")
        )
        db.collection("patients").document(cpf).set(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError("Erro ao salvar paciente.") }
    }

    // Salva teste individual (usado pelo DeviceControl para salvar progresso temporário se necessário, mas o principal é o Batch)
    fun saveTestSession(sessionId: String, patientCpf: String, patientName: String, bodyPart: String, results: Map<Int, Int>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        // Implementação simplificada mantida para compatibilidade, mas o fluxo principal agora usa saveCompleteSession
        onSuccess()
    }

    fun deleteTest(sessionId: String, bodyPart: String, onSuccess: () -> Unit) {
        val testId = "${sessionId}_${bodyPart}"
        db.collection("tests").document(testId).delete().addOnSuccessListener { onSuccess() }
    }

    fun getSessionStatus(sessionId: String, onResult: (Map<String, Boolean>, Map<String, Int>) -> Unit) {
        db.collection("tests").whereEqualTo("sessionId", sessionId).get()
            .addOnSuccessListener { docs ->
                val statusMap = mutableMapOf<String, Boolean>()
                val gifMap = mutableMapOf<String, Int>()
                docs.forEach {
                    val p = it.getString("bodyPart") ?: return@forEach
                    statusMap[p] = true
                    gifMap[p] = (it.getLong("gif") ?: 0).toInt()
                }
                onResult(statusMap, gifMap)
            }
    }

    fun getFullSessionData(sessionId: String, onSuccess: (List<Map<String, Any>>) -> Unit, onError: (String) -> Unit) {
        db.collection("tests").whereEqualTo("sessionId", sessionId).get()
            .addOnSuccessListener { docs ->
                val list = docs.documents.mapNotNull { it.data }
                onSuccess(list)
            }
            .addOnFailureListener { onError(it.message ?: "Erro ao baixar dados") }
    }

    // LÓGICA DE SALVAMENTO CORRIGIDA COM GIF
    fun saveCompleteSession(
        sessionId: String,
        patientCpf: String,
        patientName: String,
        allResults: Map<String, Map<Int, Int>>,
        hasDeformities: Boolean, // Novo parâmetro para Grau 2
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onError("Não logado")
        val batch = db.batch()
        val now = Date()

        allResults.forEach { (bodyPart, results) ->
            val testId = "${sessionId}_${bodyPart}"
            val docRef = db.collection("tests").document(testId)

            val maxLevel = results.values.maxOrNull() ?: 0

            // CÁLCULO OFICIAL DO GIF (Para Hanseníase/Diabetes)
            // Grau 2: Deformidades visíveis (input manual) OU úlceras.
            // Grau 1: Perda de sensibilidade protetora (não sente 10g/Laranja ou pior) SEM deformidade.
            // Grau 0: Sente tudo (até o violeta/azul) SEM deformidade.

            val risk = if (hasDeformities) {
                2 // Deformidade sobrepõe a sensibilidade para classificação de risco
            } else if (maxLevel >= 5) {
                1 // Perda de sensibilidade (Laranja ou Vermelho)
            } else {
                0
            }

            val data = hashMapOf(
                "sessionId" to sessionId,
                "patientCpf" to patientCpf,
                "patientName" to patientName,
                "doctorId" to uid,
                "bodyPart" to bodyPart,
                "date" to now,
                "gif" to risk,
                "hasDeformities" to hasDeformities, // Salva o flag
                "pointsData" to results.entries.associate { (k,v) -> k.toString() to v }
            )
            batch.set(docRef, data)
        }

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Erro ao salvar sessão") }
    }

    fun getGroupedHistory(onSuccess: (List<PatientHistoryData>) -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onError("Não logado")

        db.collection("tests")
            .whereEqualTo("doctorId", uid)
            .get()
            .addOnSuccessListener { result ->
                val patientsMap = mutableMapOf<String, PatientHistoryData>()

                for (doc in result.documents) {
                    val cpf = doc.getString("patientCpf") ?: continue
                    val name = doc.getString("patientName") ?: "Desconhecido"
                    val sessionId = doc.getString("sessionId") ?: continue
                    val date = doc.getTimestamp("date")?.toDate() ?: Date()
                    val gif = (doc.getLong("gif") ?: 0).toInt()

                    val patientData = patientsMap.getOrPut(cpf) {
                        PatientHistoryData(name, cpf, mutableListOf())
                    }

                    val sessions = patientData.sessions as MutableList<SessionData>
                    var session = sessions.find { it.sessionId == sessionId }
                    if (session == null) {
                        session = SessionData(sessionId, date, 0)
                        sessions.add(session)
                    }
                    if (gif > session.maxGif) session.maxGif = gif
                }

                patientsMap.values.forEach { (it.sessions as MutableList).sortByDescending { s -> s.date } }
                val sortedList = patientsMap.values.sortedByDescending { it.sessions.firstOrNull()?.date ?: Date(0) }

                onSuccess(sortedList)
            }
            .addOnFailureListener { onError(it.message ?: "Erro ao buscar histórico") }
    }

    fun getRecentPatients(limit: Int = 3, onSuccess: (List<Map<String, Any>>) -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onError("Não logado")
        db.collection("tests").whereEqualTo("doctorId", uid).get()
            .addOnSuccessListener { result ->
                val recentList = mutableListOf<Map<String, Any>>()
                val addedCpfs = mutableSetOf<String>()
                val sortedDocs = result.documents.sortedByDescending { it.getTimestamp("date")?.toDate() ?: Date(0) }

                for (doc in sortedDocs) {
                    val cpf = doc.getString("patientCpf") ?: continue
                    if (addedCpfs.contains(cpf)) continue

                    recentList.add(mapOf(
                        "name" to (doc.getString("patientName") ?: "Paciente"),
                        "cpf" to cpf,
                        "lastExam" to (doc.getTimestamp("date")?.toDate() ?: Date())
                    ))
                    addedCpfs.add(cpf)
                    if (recentList.size >= limit) break
                }
                onSuccess(recentList)
            }
            .addOnFailureListener { onError("Erro") }
    }

    fun getMyPatients(onSuccess: (List<PatientHistoryData>) -> Unit, onError: (String) -> Unit) {
        getGroupedHistory(onSuccess, onError)
    }
}

// Classes de Dados
data class PatientHistoryData(val name: String, val cpf: String, val sessions: List<SessionData>)
data class SessionData(val sessionId: String, val date: Date, var maxGif: Int)