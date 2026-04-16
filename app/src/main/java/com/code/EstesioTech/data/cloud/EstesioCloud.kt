package com.code.EstesioTech.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║                       EstesioCloud.kt                               ║
 * ║  Camada de acesso a dados remotos (Firebase Auth + Firestore).       ║
 * ║                                                                      ║
 * ║  Estrutura do Firestore:                                             ║
 * ║                                                                      ║
 * ║  /users/{crm}_{uf}                                                  ║
 * ║    uid, name, crm, uf, recoveryEmail, role                          ║
 * ║                                                                      ║
 * ║  /patients/{auto_id}                                                ║
 * ║    cpf (hashed via SHA-256), name, age, email,                      ║
 * ║    createdAt, createdBy                                              ║
 * ║                                                                      ║
 * ║  /tests/{sessionId}_{bodyPart}                                      ║
 * ║    sessionId, patientCpf (hashed), patientName,                     ║
 * ║    doctorId, bodyPart, date, gif, hasDeformities, pointsData        ║
 * ║                                                                      ║
 * ║  LGPD:                                                               ║
 * ║  - CPF é armazenado como hash SHA-256 para buscas                   ║
 * ║  - Dados sensíveis não são logados                                   ║
 * ║  - IDs de documentos de pacientes são aleatórios (não expõem CPF)   ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 */
object EstesioCloud {

    // ── Instâncias Firebase (lazy: criadas apenas quando necessárias) ──
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // ── Nomes das Collections ──────────────────────────────────────────
    private const val COL_USERS    = "users"
    private const val COL_PATIENTS = "patients"
    private const val COL_TESTS    = "tests"

    // ── Autenticação ───────────────────────────────────────────────────

    /** @return UID do usuário logado, ou null se não autenticado */
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    /** @return true se há um usuário autenticado na sessão atual */
    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    /**
     * Autentica o médico usando CRM + UF + senha.
     *
     * Fluxo:
     * 1. Busca o documento do médico em /users/{crm}_{uf}
     * 2. Extrai o recoveryEmail (email usado no Firebase Auth)
     * 3. Chama signInWithEmailAndPassword com o email e senha
     *
     * O email de autenticação é interno e nunca exposto na UI —
     * o médico sempre faz login com CRM + UF, respeitando a UX médica.
     *
     * @param crm    CRM numérico do médico
     * @param uf     UF de registro (ex: "PE", "SP")
     * @param pass   Senha do usuário
     */
    fun login(
        crm: String,
        uf: String,
        pass: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val docId = "${crm}_${uf}"
        db.collection(COL_USERS).document(docId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onError("CRM não encontrado. Verifique o CRM e a UF.")
                    return@addOnSuccessListener
                }
                val email = doc.getString("recoveryEmail")
                if (email.isNullOrEmpty()) {
                    onError("Cadastro inválido. Contate o suporte.")
                    return@addOnSuccessListener
                }
                auth.signInWithEmailAndPassword(email, pass)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError("Senha incorreta.") }
            }
            .addOnFailureListener { onError("Erro de conexão. Verifique sua internet.") }
    }

    /**
     * Cria uma nova conta de médico no Firebase Auth e Firestore.
     *
     * O documento é indexado por {crm}_{uf} para permitir login sem expor
     * o email internamente. O UID gerado pelo Auth é salvo no documento
     * para operações futuras que requerem o UID (ex: queries por médico).
     *
     * @param crm            CRM numérico
     * @param uf             UF de registro
     * @param pass           Senha escolhida pelo médico
     * @param name           Nome completo
     * @param recoveryEmail  Email real usado como credencial do Firebase Auth
     */
    fun register(
        crm: String,
        uf: String,
        pass: String,
        name: String,
        recoveryEmail: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(recoveryEmail, pass)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: run {
                    onError("Falha ao obter UID. Tente novamente.")
                    return@addOnSuccessListener
                }
                val docId = "${crm}_${uf}"
                val data = hashMapOf(
                    "uid"           to uid,
                    "name"          to name,
                    "crm"           to crm,
                    "uf"            to uf,
                    "recoveryEmail" to recoveryEmail,
                    "role"          to "medico",
                    "createdAt"     to Date()
                )
                db.collection(COL_USERS).document(docId).set(data)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError("Erro ao salvar perfil: ${it.message}") }
            }
            .addOnFailureListener { e ->
                // Mapeia erros comuns do Firebase Auth para mensagens amigáveis
                val msg = when {
                    e.message?.contains("email address is already in use") == true ->
                        "Este e-mail já está cadastrado."
                    e.message?.contains("weak-password") == true ->
                        "Senha muito fraca. Use ao menos 6 caracteres."
                    else -> e.message ?: "Erro desconhecido ao criar conta."
                }
                onError(msg)
            }
    }

    /**
     * Envia email de redefinição de senha para o médico identificado por CRM + UF.
     *
     * O email de destino é recuperado do Firestore (não solicitado ao usuário),
     * garantindo que apenas o email cadastrado no sistema receba o link de reset.
     */
    fun sendPasswordResetByCrm(
        crm: String,
        uf: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val docId = "${crm}_${uf}"
        db.collection(COL_USERS).document(docId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onError("CRM não encontrado.")
                    return@addOnSuccessListener
                }
                val email = doc.getString("recoveryEmail")
                if (email.isNullOrEmpty()) {
                    onError("Sem e-mail cadastrado para este CRM.")
                    return@addOnSuccessListener
                }
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it.message ?: "Erro ao enviar e-mail.") }
            }
            .addOnFailureListener { onError("Erro de conexão.") }
    }

    /** Encerra a sessão atual do Firebase Auth */
    fun logout() = auth.signOut()

    /**
     * Busca o primeiro nome do médico logado.
     *
     * Usa query por UID interno para encontrar o documento,
     * já que o documento é indexado por CRM_UF (não pelo UID diretamente).
     *
     * @param onResult Callback com o primeiro nome, ou "Doutor(a)" como fallback
     */
    fun getUserName(onResult: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            onResult("Doutor(a)")
            return
        }
        db.collection(COL_USERS).whereEqualTo("uid", uid).limit(1).get()
            .addOnSuccessListener { docs ->
                val fullName = docs.documents.firstOrNull()?.getString("name") ?: "Doutor(a)"
                // Retorna apenas o primeiro nome para cumprimento personalizado
                onResult(fullName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: fullName)
            }
            .addOnFailureListener { onResult("Doutor(a)") }
    }

    // ── Pacientes ──────────────────────────────────────────────────────

    /**
     * Verifica se um paciente existe no Firestore pelo CPF (hasheado).
     *
     * ⚠️  LGPD: O CPF é hasheado via [hashCpf] antes de qualquer operação
     * de leitura/escrita. O hash SHA-256 permite buscas por igualdade sem
     * armazenar o CPF em texto plano no servidor.
     *
     * @param cpf CPF numérico (11 dígitos, sem máscara)
     * @param onFound    Chamado com (nome, email, idade) se encontrado
     * @param onNotFound Chamado se não existe
     * @param onError    Chamado com mensagem em caso de falha de rede
     */
    fun checkPatient(
        cpf: String,
        onFound: (name: String, email: String, age: String) -> Unit,
        onNotFound: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cpfHash = hashCpf(cpf)
        db.collection(COL_PATIENTS).whereEqualTo("cpfHash", cpfHash).limit(1).get()
            .addOnSuccessListener { query ->
                if (!query.isEmpty) {
                    val doc = query.documents[0]
                    onFound(
                        doc.getString("name")  ?: "",
                        doc.getString("email") ?: "",
                        doc.getString("age")   ?: ""
                    )
                } else {
                    onNotFound()
                }
            }
            .addOnFailureListener { onError("Erro de conexão ao buscar paciente.") }
    }

    /**
     * Cria um novo registro de paciente no Firestore.
     *
     * LGPD:
     * - O documento usa ID aleatório gerado pelo Firestore (.add()), não o CPF
     * - O CPF real é armazenado criptografado (apenas hash para busca)
     * - O campo `createdBy` registra o UID do médico responsável
     *
     * @param cpf   CPF numérico (11 dígitos)
     * @param name  Nome completo do paciente
     * @param age   Idade em anos
     * @param email Email do paciente (pode ser vazio)
     */
    fun createPatient(
        cpf: String,
        name: String,
        age: String,
        email: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cpfHash = hashCpf(cpf)
        val data = hashMapOf(
            "cpfHash"   to cpfHash,   // Hash para buscas (LGPD)
            "name"      to name,
            "age"       to age,
            "email"     to email,
            "createdAt" to Date(),
            "createdBy" to (auth.currentUser?.uid ?: "unknown")
            // ⚠️  O CPF em texto plano NÃO é armazenado aqui.
            // Se necessário para laudos, o médico deve manter controle local.
        )
        db.collection(COL_PATIENTS).add(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Erro ao cadastrar paciente.") }
    }

    /**
     * Salva todos os resultados de uma sessão de exame no Firestore usando batch write.
     *
     * Cada parte do corpo gera um documento separado em /tests/{sessionId}_{bodyPart}.
     * O batch garante atomicidade: ou todos salvam, ou nenhum.
     *
     * Cálculo de risco (campo "gif"):
     * - 0 = Sensibilidade preservada (todos os pontos nível ≤ 4)
     * - 1 = Risco moderado (algum ponto nível ≥ 5)
     * - 2 = Alto risco (deformidades presentes)
     *
     * @param sessionId    ID da sessão (formato: ddMMyy_HHmm)
     * @param patientCpf   CPF do paciente (será hasheado antes de salvar)
     * @param patientName  Nome do paciente (armazenado para exibição)
     * @param allResults   Map de parte do corpo → Map de ponto → nível
     * @param hasDeformities Se o médico marcou presença de deformidades
     */
    fun saveCompleteSession(
        sessionId: String,
        patientCpf: String,
        patientName: String,
        allResults: Map<String, Map<Int, Int>>,
        hasDeformities: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError("Usuário não autenticado.")
            return
        }
        val cpfHash = hashCpf(patientCpf)
        val batch = db.batch()
        val now = Date()

        allResults.forEach { (bodyPart, results) ->
            val testId = "${sessionId}_${bodyPart}"
            val docRef = db.collection(COL_TESTS).document(testId)
            val maxLevel = results.values.maxOrNull() ?: 0

            // Cálculo da classificação de risco conforme protocolo clínico
            val riskLevel = when {
                hasDeformities -> 2              // Deformidade = risco máximo
                maxLevel >= 5  -> 1              // Perda profunda = risco moderado
                else           -> 0              // Sensibilidade preservada
            }

            val data = hashMapOf(
                "sessionId"      to sessionId,
                "patientCpf"     to cpfHash,    // Hash (LGPD)
                "patientName"    to patientName, // Nome mantido para exibição em laudos
                "doctorId"       to uid,
                "bodyPart"       to bodyPart,
                "date"           to now,
                "gif"            to riskLevel,
                "hasDeformities" to hasDeformities,
                "pointsData"     to results.entries.associate { (k, v) -> k.toString() to v }
            )
            batch.set(docRef, data)
        }

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Erro ao salvar sessão.") }
    }

    /**
     * Exclui todos os documentos de uma sessão pelo sessionId.
     *
     * Usado quando o médico decide deletar um atendimento no histórico.
     * Busca todos os documentos com o sessionId e os deleta em batch.
     */
    fun deleteSession(
        sessionId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection(COL_TESTS).whereEqualTo("sessionId", sessionId).get()
            .addOnSuccessListener { query ->
                if (query.isEmpty) {
                    onSuccess() // Já deletado, considera sucesso
                    return@addOnSuccessListener
                }
                val batch = db.batch()
                query.documents.forEach { batch.delete(it.reference) }
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError("Erro ao excluir sessão.") }
            }
            .addOnFailureListener { onError("Erro de conexão ao excluir.") }
    }

    /**
     * Busca o histórico completo de atendimentos do médico logado,
     * agrupados por paciente e ordenados por data decrescente.
     *
     * Estrutura retornada: Lista de [PatientHistoryData] onde cada item
     * contém o paciente e suas sessões ordenadas da mais recente à mais antiga.
     *
     * Performance: Faz uma única query em /tests filtrando por doctorId,
     * depois agrupa em memória. Para médicos com > 1000 testes, considere
     * paginação futura.
     */
    fun getGroupedHistory(
        onSuccess: (List<PatientHistoryData>) -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError("Usuário não autenticado.")
            return
        }

        db.collection(COL_TESTS)
            .whereEqualTo("doctorId", uid)
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                // Agrupamento em memória por hash do CPF
                val patientsMap = mutableMapOf<String, PatientHistoryData>()

                for (doc in result.documents) {
                    val cpfHash    = doc.getString("patientCpf")  ?: continue
                    val name       = doc.getString("patientName") ?: "Desconhecido"
                    val sessionId  = doc.getString("sessionId")   ?: continue
                    val date       = doc.getTimestamp("date")?.toDate() ?: Date()
                    val gif        = (doc.getLong("gif") ?: 0L).toInt()

                    val patient = patientsMap.getOrPut(cpfHash) {
                        PatientHistoryData(name, cpfHash, mutableListOf())
                    }
                    val sessions = patient.sessions as MutableList<SessionData>
                    var session = sessions.find { it.sessionId == sessionId }
                    if (session == null) {
                        session = SessionData(sessionId, date, 0)
                        sessions.add(session)
                    }
                    // Mantém o maior nível de risco da sessão
                    if (gif > session.maxGif) session.maxGif = gif
                }

                // Ordena sessões e pacientes por data decrescente
                val sortedList = patientsMap.values.map { patient ->
                    patient.copy(sessions = patient.sessions.sortedByDescending { it.date })
                }.sortedByDescending { it.sessions.firstOrNull()?.date ?: Date(0) }

                onSuccess(sortedList)
            }
            .addOnFailureListener { onError(it.message ?: "Erro ao buscar histórico.") }
    }

    /**
     * Busca os N atendimentos mais recentes do médico para exibição na Home.
     *
     * Retorna pacientes únicos (sem repetição), ordenados pela data do
     * atendimento mais recente. Cada item contém nome, cpfHash e data.
     *
     * @param limit Máximo de pacientes únicos a retornar (padrão: 3)
     */
    fun getRecentPatients(
        limit: Int = 3,
        onSuccess: (List<Map<String, Any>>) -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError("Usuário não autenticado.")
            return
        }

        db.collection(COL_TESTS)
            .whereEqualTo("doctorId", uid)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit((limit * 10).toLong()) // Busca mais para garantir pacientes únicos
            .get()
            .addOnSuccessListener { result ->
                val recent = mutableListOf<Map<String, Any>>()
                val seen = mutableSetOf<String>()

                for (doc in result.documents) {
                    val cpfHash = doc.getString("patientCpf") ?: continue
                    if (seen.contains(cpfHash)) continue

                    recent.add(mapOf(
                        "name"     to (doc.getString("patientName") ?: "Paciente"),
                        "cpfHash"  to cpfHash,
                        "lastExam" to (doc.getTimestamp("date")?.toDate() ?: Date())
                    ))
                    seen.add(cpfHash)
                    if (recent.size >= limit) break
                }
                onSuccess(recent)
            }
            .addOnFailureListener { onError("Erro ao buscar pacientes recentes.") }
    }

    /**
     * Busca todos os dados de uma sessão pelo sessionId para geração de PDF.
     *
     * @return Lista de Maps com todos os campos de cada documento de teste da sessão
     */
    fun getFullSessionData(
        sessionId: String,
        onSuccess: (List<Map<String, Any>>) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection(COL_TESTS).whereEqualTo("sessionId", sessionId).get()
            .addOnSuccessListener { result ->
                onSuccess(result.documents.mapNotNull { it.data })
            }
            .addOnFailureListener { onError(it.message ?: "Erro ao buscar dados da sessão.") }
    }

    // ── Utilitários Internos ───────────────────────────────────────────

    /**
     * Gera um hash SHA-256 do CPF para armazenamento seguro (LGPD).
     *
     * O hash é determinístico: o mesmo CPF sempre gera o mesmo hash,
     * permitindo buscas por igualdade sem armazenar o CPF em texto plano.
     *
     * ⚠️  SHA-256 puro sem salt é vulnerável a rainbow tables.
     * Para produção, considere adicionar um salt fixo por aplicação
     * armazenado no servidor (não no app), ou usar HMAC-SHA256.
     *
     * @param cpf CPF numérico (11 dígitos)
     * @return Hash hexadecimal SHA-256 do CPF
     */
    private fun hashCpf(cpf: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(cpf.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Fallback seguro: nunca armazena o CPF raw em caso de erro
            "hash_error_${cpf.length}"
        }
    }
}

// ── Data Classes ───────────────────────────────────────────────────────

/**
 * Representa um paciente com seu histórico de sessões de exame.
 *
 * @param name     Nome completo do paciente
 * @param cpfHash  Hash SHA-256 do CPF (identificador seguro, não o CPF real)
 * @param sessions Lista de sessões ordenadas por data decrescente
 */
data class PatientHistoryData(
    val name: String,
    val cpfHash: String,
    val sessions: List<SessionData>
)

/**
 * Representa uma sessão de exame (um atendimento completo).
 *
 * @param sessionId ID único da sessão (formato: ddMMyy_HHmm)
 * @param date      Data e hora do atendimento
 * @param maxGif    Maior classificação de risco encontrada na sessão (0, 1 ou 2)
 */
data class SessionData(
    val sessionId: String,
    val date: Date,
    var maxGif: Int
)