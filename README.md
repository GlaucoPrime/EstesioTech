# 🩺 Hansen.IA (EstesioTech)

**Digitalização do exame de Estesiometria para neuropatias (Hanseníase e Diabetes Mellitus) via ecossistema IoMT — hardware embarcado (ESP32) + aplicativo Android nativo.**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com)
[![AGP](https://img.shields.io/badge/AGP-8.7.3-3DDC84?logo=android)]()
[![Min SDK](https://img.shields.io/badge/minSdk-23-success)]()
[![Target SDK](https://img.shields.io/badge/targetSdk-35-success)]()
[![Status](https://img.shields.io/badge/status-em%20desenvolvimento-orange)]()

---

## 📖 Sobre o projeto

O **Hansen.IA** é um ecossistema de saúde digital (IoMT — *Internet of Medical Things*) que substitui o exame analógico de Estesiometria — tradicionalmente realizado em papel com os Monofilamentos de Semmes-Weinstein — por um fluxo digital completo:

```
Estesiômetro Digital (ESP32 + sensor de pressão)
        │  Bluetooth Low Energy (GATT)
        ▼
  App Android Nativo (este repositório)
        │  mapeamento gráfico em tempo real
        │  cálculo de risco/GIF
        │  geração de laudo PDF
        ▼
  Firebase (Auth + Firestore)
```

O exame avalia a perda de sensibilidade tátil-protetora em pacientes com **Hanseníase** e **Neuropatia Diabética**, condições em que a detecção precoce da perda sensitiva é fator crítico na prevenção de incapacidades físicas permanentes e amputações.

Este aplicativo é a camada de coleta de dados clínicos do ecossistema **Hansen.AI**, projeto de pesquisa conduzido no IFPE Campus Recife sob orientação do **Prof. Me. Hilson Gomes Vilar de Andrade**.

## ✨ Funcionalidades

- 🔐 **Autenticação profissional via CRM + UF + senha** — o médico nunca digita um email; internamente, CRM+UF são mapeados para um email sintético consumido apenas pelo Firebase Auth (ver `AuthIdentity` em `LoginActivity.kt`)
- 📝 **Cadastro de novos profissionais**, com email real salvo separadamente como `recoveryEmail` no Firestore
- 📡 **Conexão BLE obrigatória** — o app bloqueia ativamente qualquer avaliação até que o estesiômetro esteja conectado (gate em 3 camadas redundantes, ver `DOCUMENTACAO_TECNICA.md`)
- 🖐️🦶 **Mapeamento anatômico interativo** de mãos e pés com pontos de avaliação clicáveis
- 📊 **Indicador de risco em tempo real** durante o exame, com classificação visual progressiva baseada na escala Semmes-Weinstein
- ☁️ **Persistência em Firebase Firestore** com hash HMAC-SHA256 do CPF do paciente (LGPD — nunca texto plano)
- 📄 **Geração de laudo em PDF** com dados do paciente, resultados por ponto e classificação de risco
- 🌗 **Tema claro/escuro** configurável
- 🎨 **Modos de correção para daltonismo** (protanopia, deuteranopia, tritanopia) — paletas alternativas para a escala clínica
- 🌍 **Internacionalização** (PT/EN/ES) via `LocaleUtils`
- 🔠 **Escala de fonte ajustável** para acessibilidade

## 🏗️ Arquitetura

A organização atual segue separação em camadas por responsabilidade (`data` / `ui` / `utils`), sem ainda uma Clean Architecture estrita com casos de uso e repositórios:

```
┌──────────────────────────────────────────────┐
│                  ui.screens                    │  Activities + Composables
│   (lógica de apresentação E de negócio juntas) │  ⚠️ sem ViewModel ainda
└──────────────────┬─────────────────────────────┘
                    │
        ┌───────────┴────────────┐
        ▼                        ▼
┌───────────────────┐   ┌──────────────────────┐
│  data.bluetooth     │   │   data.cloud          │
│  BleManager          │   │   EstesioCloud         │
│  (Singleton GATT)     │   │   (Singleton Firebase)  │
└───────────────────┘   └──────────────────────┘
```

> 💡 **Recomendação para evolução futura:** extrair a lógica de estado das Activities para `ViewModel`s com `StateFlow`, e introduzir uma camada de `Repository`/casos de uso entre `ui` e `data`. Ver `DOCUMENTACAO_TECNICA.md` para detalhamento completo.

## 🛠️ Stack tecnológica

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin 2.0.21 |
| UI | Jetpack Compose (Material 3) — **100% Compose, sem Views/XML layouts** |
| Build | Gradle Kotlin DSL, AGP 8.7.3, Version Catalog (`libs.versions.toml`) |
| Backend/BaaS | Firebase Authentication + Cloud Firestore (BOM 33.7.0) |
| Comunicação com hardware | Bluetooth Low Energy — `BluetoothGatt` nativo |
| Hardware embarcado | ESP32 (firmware em C++, fora deste repositório) |
| Geração de documentos | iText 5.5.13.4 (`PdfUtil`) |
| Compatibilidade mínima | Android 6.0 (API 23) |
| Compatibilidade alvo | Android 15 (API 35) |
| JDK | 17 (requisito do AGP 8.x) |

> ⚠️ **Nota sobre versões:** o changelog de build mostra o Gradle baixando `aapt2-8.7.3`, confirmando que o AGP efetivo do projeto é **8.7.3** — não 8.13.0 como constava em uma versão anterior deste README (essa versão do AGP não existe publicamente; foi um erro de transcrição). O `compileOptions` do módulo usa `JavaVersion.VERSION_17` (não `VERSION_1_8` como também constava antes) — Kotlin 2.0 + Compose Compiler plugin exigem toolchain 17.

## 📁 Estrutura de pastas

```
app/
├── manifests/
│   └── AndroidManifest.xml
├── kotlin+java/com.code.EstesioTech/
│   ├── data/
│   │   ├── bluetooth/
│   │   │   └── BleManager.kt
│   │   └── cloud/
│   │       └── EstesioCloud.kt
│   ├── ui/
│   │   ├── screens/
│   │   │   ├── MainActivity.kt          (splash + roteamento)
│   │   │   ├── LoginActivity.kt         (login CRM + UF)
│   │   │   ├── RegisterActivity.kt      (cadastro CRM + UF)
│   │   │   ├── HomeActivity.kt          (hub: conexão BLE + CPF)
│   │   │   ├── SelectionActivity.kt     (seleção de membro)
│   │   │   ├── TesteActivity.kt         (execução do exame, gate BLE)
│   │   │   ├── DeviceControlActivity.kt (terminal BLE de diagnóstico)
│   │   │   ├── HistoryActivity.kt       (histórico por CPF)
│   │   │   └── SettingsActivity.kt      (tema, daltonismo, idioma)
│   │   └── theme/
│   │       ├── Color.kt                 (paletas normal + 3 modos daltonismo)
│   │       ├── Theme.kt                 (MaterialTheme + CompositionLocals)
│   │       └── Type.kt                  (tipografia)
│   └── utils/
│       ├── SessionCache.kt              (estado de sessão em memória)
│       ├── ClinicalData.kt              (ClinicalScale, ClinicalResult)
│       ├── Components.kt                (TechTextField, TechStateDropdown, RiskBadge)
│       ├── PdfUtil.kt                   (geração de laudo PDF)
│       ├── LocaleUtils.kt               (internacionalização)
│       ├── MaskUtils.kt                 (máscara/validação de CPF)
│       ├── IbgeProvider.kt              (estados brasileiros via API IBGE)
│       └── ChatMessage.kt               (modelo do terminal BLE)
├── res/
│   ├── values/themes.xml                (tema nativo — ver nota de build abaixo)
│   ├── values-night/themes.xml
│   └── xml/
│       ├── data_extraction_rules.xml
│       ├── backup_rules.xml
│       ├── network_security_config.xml
│       └── file_paths.xml
└── google-services.json

Gradle Scripts/
├── build.gradle.kts (Project)
├── build.gradle.kts (Module :app)
├── libs.versions.toml
├── proguard-rules.pro
├── gradle.properties
└── settings.gradle.kts
```

## 🗄️ Modelo de dados (Firestore)

```
/users/{crm}_{uf}
    uid, name, crm, uf, recoveryEmail, role, createdAt

/patients/{cpfHash}/
    name, updatedAt
    └── evaluations/{evalId}/
          date, doctorId, results, worstLevel, riskLabel, membersCount, pdfUrl
```

**Conformidade LGPD:**
- CPF nunca é armazenado em texto plano — apenas seu hash **HMAC-SHA256 com salt** é persistido (`EstesioCloud.hashCpf`)
- O salt de produção deve vir de `local.properties` → `BuildConfig.SECRET_SALT`, nunca commitado
- O documento do paciente é identificado pelo hash do CPF — não há campo de CPF em texto plano em nenhum lugar do banco
- O email de autenticação real do médico (`recoveryEmail`) nunca é exposto na UI — login é sempre por CRM + UF

## 📶 Protocolo de comunicação BLE

| UUID | Papel |
|---|---|
| `4fafc201-1fb5-459e-8fcc-c5c9c331914b` | Serviço |
| `beb5483e-36e1-4688-b7f5-ea07361b26a8` | Característica Notify (leitura em tempo real) |
| `beb5483e-36e1-4688-b7f5-ea07361b26a9` | Característica Write (envio de comandos) |
| `00002902-0000-1000-8000-00805f9b34fb` | Descriptor CCCD |

**Protocolo de mensagens (texto ASCII):**
- `"1"` a `"6"` → nível de pressão Semmes-Weinstein lido em tempo real
- `"Enviado"` → confirmação física (botão do aparelho) — o último valor numérico recebido antes desta string é persistido como definitivo para aquele ponto

## ⚠️ Build failure conhecido e correção aplicada

Se você encontrar este erro ao rodar `assembleDebug`:

```
error: resource style/Theme.AppCompat.DayNight.NoActionBar not found
error: style attribute 'attr/colorPrimary' not found
error: style attribute 'attr/colorPrimaryVariant' not found
error: style attribute 'attr/windowActionBar' not found
```

**Causa raiz:** `res/values/themes.xml` herdava de `Theme.AppCompat.DayNight.NoActionBar`, mas o projeto é **100% Jetpack Compose** e não declara as dependências do Material Components clássico (`com.google.android.material:material`) nem AppCompat no `build.gradle.kts`. O AAPT2 não encontra o estilo porque a biblioteca que o define nunca foi adicionada ao classpath.

**Correção aplicada:** o `themes.xml` (e sua variante `values-night/`) agora herdam de `android:Theme.DeviceDefault.DayNight.NoActionBar` — um tema nativo da plataforma, sem qualquer dependência externa. Esse tema serve apenas como pré-tela (status bar, splash nativo) antes do `setContent { EstesioTechTheme { ... } }` assumir o controle visual real via Compose.

Não adicione AppCompat/Material de volta como "solução" — isso reintroduziria peso desnecessário no APK para um app que não usa nenhuma View XML.

## ⚙️ Pré-requisitos

- Android Studio compatível com AGP 8.7.3 / Kotlin 2.0.21
- JDK 17
- Dispositivo Android físico com Bluetooth LE (emuladores não suportam BLE real)
- Projeto Firebase com Authentication (E-mail/Senha) e Firestore habilitados
- `google-services.json` próprio (não incluído no repositório)
- Hardware Estesiômetro Digital (ESP32 + firmware compatível com o protocolo BLE acima)

## 🚀 Configuração e execução

```bash
git clone https://github.com/<usuario>/hansen-ia.git
cd hansen-ia
```

1. Adicione seu `google-services.json` em `app/`.
2. Crie `local.properties` na raiz com:
   ```properties
   sdk.dir=/caminho/para/seu/sdk
   secret_salt=SEU_SALT_SECRETO_AQUI
   ```
3. Abra no Android Studio e aguarde o Gradle Sync.
4. Conecte um dispositivo físico com Bluetooth ativado.
5. Execute o módulo `app`.

## 📌 Status do projeto e roadmap

| Componente | Status |
|---|---|
| Login/Cadastro (CRM+UF) | ✅ Implementado |
| Gate de conexão BLE obrigatória | ✅ Implementado (3 camadas redundantes) |
| Execução do exame (TesteActivity) | ✅ Implementado |
| Persistência Firestore com hash LGPD | ✅ Implementado |
| Geração de PDF (multiplataforma Android 9-/10+) | ✅ Implementado |
| Tema com correção de daltonismo | ✅ Implementado |
| Terminal BLE de diagnóstico | ✅ Implementado |
| Upload do PDF para Firebase Storage | 🚧 Estrutura pronta, upload não implementado |
| Camada ViewModel | ❌ Não implementada |
| Testes automatizados | ❌ Não implementados |
| Reset de senha (CRM+UF não suporta nativamente) | ❌ Requer Cloud Function — não implementado |

## 🩹 Dívida técnica conhecida

Resumo — detalhamento completo em [`DOCUMENTACAO_TECNICA.md`](./DOCUMENTACAO_TECNICA.md):

1. Salt do hash de CPF ainda como placeholder em código — precisa ser movido definitivamente para `BuildConfig.SECRET_SALT` em todos os ambientes de build
2. Ausência de camada `ViewModel` — estado vive direto nas Activities
3. Coordenadas anatômicas dos pontos de exame hardcoded como percentuais fixos
4. Reset de senha não funcional para o modelo CRM+UF (email sintético não recebe emails reais) — necessita Cloud Function dedicada
5. Nenhum teste automatizado implementado
6. Validação de CRM é apenas de formato, sem integração com fonte oficial (CFM)

## 📚 Documentação adicional

- [`DOCUMENTACAO_TECNICA.md`](./DOCUMENTACAO_TECNICA.md) — arquitetura, decisões de design, fluxos de dados e protocolos BLE detalhados
- [`PROMPT_PROXIMA_IA.md`](./PROMPT_PROXIMA_IA.md) — contexto completo para retomar o desenvolvimento com qualquer assistente de IA
- [`EXPLICACAO_APP.md`](./EXPLICACAO_APP.md) — explicação funcional do app para usuários finais e stakeholders não técnicos

## 👥 Equipe

- **Desenvolvimento:** Glauco — discente de Análise e Desenvolvimento de Sistemas, IFPE Campus Recife
- **Orientação:** Prof. Me. Hilson Gomes Vilar de Andrade
- **Projeto vinculado:** Ecossistema Hansen.AI — IFPE Campus Recife

## Licença

Projeto proprietário — EstesioTech. Todos os direitos reservados.

---

<p align="center">Feito com 🩺 e ☕ para apoiar profissionais de saúde no combate à Hanseníase e Neuropatia Diabética.</p>
