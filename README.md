# 🩺 EstesioTech

**Digitalização do exame de Estesiometria para neuropatias (Hanseníase e Diabetes Mellitus) via ecossistema IoMT — hardware embarcado (ESP32) + aplicativo Android nativo.**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com)
[![Min SDK](https://img.shields.io/badge/minSdk-24-success)]()
[![Target SDK](https://img.shields.io/badge/targetSdk-34-success)]()
[![Status](https://img.shields.io/badge/status-em%20desenvolvimento-orange)]()

---

> ⚠️ **Nota de transparência:** este README foi gerado a partir dos arquivos de código já compartilhados pelo autor (Pack 1 e Pack 2 de 5). Seções marcadas com 🚧 referem-se a telas/componentes ainda não documentados em detalhe e serão atualizadas conforme os packs restantes forem analisados.

## 📋 Sumário

- [Sobre o projeto](#-sobre-o-projeto)
- [Funcionalidades](#-funcionalidades)
- [Arquitetura](#-arquitetura)
- [Stack tecnológica](#-stack-tecnológica)
- [Estrutura de pastas](#-estrutura-de-pastas)
- [Modelo de dados (Firestore)](#-modelo-de-dados-firestore)
- [Protocolo de comunicação BLE](#-protocolo-de-comunicação-ble)
- [Pré-requisitos](#-pré-requisitos)
- [Configuração e execução](#-configuração-e-execução)
- [Status do projeto e roadmap](#-status-do-projeto-e-roadmap)
- [Dívida técnica conhecida](#-dívida-técnica-conhecida)
- [Equipe](#-equipe)

---

## 📖 Sobre o projeto

O **EstesioTech** é um ecossistema de saúde digital (IoMT — *Internet of Medical Things*) que substitui o exame analógico de Estesiometria — tradicionalmente realizado em papel com os Monofilamentos de Semmes-Weinstein — por um fluxo digital completo:

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

Este aplicativo é a camada de coleta de dados clínicos do ecossistema **Hansen.AI**, projeto de pesquisa mais amplo conduzido no IFPE Campus Recife sob orientação do **Prof. Me. Hilson Gomes Vilar de Andrade**.

## ✨ Funcionalidades

Confirmadas no código-fonte analisado até o momento:

- 🔐 **Autenticação profissional** via CRM + UF + senha (mapeado internamente para e-mail no Firebase Auth, sem expor credenciais técnicas ao usuário)
- 📝 **Cadastro de novos profissionais** com validação de senha e dados obrigatórios
- 📡 **Radar Bluetooth animado** para descoberta de dispositivos BLE próximos (estilo sonar, sem filtro de nome para suportar firmwares ESP32 que não anunciam nome)
- 🖐️🦶 **Mapeamento anatômico interativo** de mãos e pés com pontos de avaliação clicáveis
- 📊 **Indicador de risco em tempo real** durante o exame, com classificação visual progressiva (cinza → verde → amarelo → vermelho)
- 🚩 **Marcação de deformidades**, que força classificação de Grau de Incapacidade (GIF) máxima automaticamente
- ☁️ **Persistência em Firebase Firestore** com hash SHA-256 do CPF do paciente (não armazenamento em texto plano)
- 📄 **Geração e compartilhamento de laudo em PDF** via `FileProvider`
- 🌗 **Tema claro/escuro** configurável
- 🎨 **Modos de correção para daltonismo** (parâmetro `colorBlindMode` no tema) 🚧 *detalhamento pendente — aguardando arquivos `Theme.kt`/`Color.kt`*
- 🌍 **Internacionalização** via `LocaleUtils` 🚧 *idiomas suportados a confirmar*
- 🔠 **Escala de fonte ajustável** (`fontScale`) para acessibilidade

## 🏗️ Arquitetura

A organização atual do código segue uma **separação em camadas por responsabilidade** (`data` / `ui` / `utils`), e não ainda uma Clean Architecture estrita com casos de uso e repositórios — é importante documentar isso com precisão, porque é o ponto de maior oportunidade de evolução do projeto:

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

> 💡 **Recomendação registrada para evolução futura:** extrair a lógica de estado das Activities para `ViewModel`s com `StateFlow`, e introduzir uma camada de `Repository`/casos de uso entre `ui` e `data`, aproximando o projeto de uma Clean Architecture real com testabilidade de unidade. Ver `DOCUMENTACAO_TECNICA.md` para detalhamento.

## 🛠️ Stack tecnológica

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin 2.0.21 |
| UI | Jetpack Compose (Material 3) |
| Build | Gradle (AGP 8.13.0), Version Catalog (`libs.versions.toml`) |
| Backend/BaaS | Firebase Authentication + Cloud Firestore |
| Comunicação com hardware | Bluetooth Low Energy — `BluetoothGatt` nativo |
| Hardware embarcado | ESP32 (firmware em C++, fora deste repositório) |
| Geração de documentos | `PdfDocument`/Canvas nativo (via `PdfUtil`) 🚧 |
| Compatibilidade mínima | Android 7.0 (API 24) |
| Compatibilidade alvo | Android 14 (API 34) |

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
│   │   │   ├── LoginActivity.kt
│   │   │   ├── RegisterActivity.kt
│   │   │   ├── MainActivity.kt          (radar Bluetooth)
│   │   │   ├── SelectionActivity.kt     (seleção de membros)
│   │   │   ├── TesteActivity.kt         (execução do exame)
│   │   │   ├── HomeActivity.kt          🚧
│   │   │   ├── DeviceControlActivity.kt 🚧
│   │   │   ├── HistoryActivity.kt       🚧
│   │   │   └── SettingsActivity.kt      🚧
│   │   └── theme/
│   │       ├── Color.kt    🚧
│   │       ├── Theme.kt    🚧
│   │       └── Type.kt     🚧
│   └── utils/
│       ├── LocaleUtils.kt    🚧
│       ├── MaskUtils.kt      🚧
│       ├── ChatMessage.kt    🚧
│       ├── ClinicalData.kt   🚧  (ClinicalScale, ClinicalResult)
│       ├── Components.kt     🚧  (TechTextField, TechStateDropdown, RiskBadge)
│       ├── IbgeProvider.kt   🚧
│       ├── PdfUtil.kt        🚧
│       └── SessionCache.kt
├── res/
└── google-services.json
```

🚧 = arquivo referenciado no código mas ainda não recebido para documentação detalhada.

## 🗄️ Modelo de dados (Firestore)

```
/users/{crm}_{uf}
    uid, name, crm, uf, recoveryEmail, role, createdAt

/patients/{auto_id}
    cpfHash (SHA-256), name, age, email, createdAt, createdBy

/tests/{sessionId}_{bodyPart}
    sessionId, patientCpf (hash), patientName, doctorId,
    bodyPart, date, gif (0|1|2), hasDeformities, pointsData
```

**Conformidade LGPD observada no código:**
- CPF nunca é armazenado em texto plano — apenas seu hash SHA-256 é persistido
- IDs de documentos de pacientes são aleatórios (não derivados do CPF)
- O e-mail de autenticação (`recoveryEmail`) é interno e nunca exposto na UI — o login do profissional é sempre por CRM + UF

> ⚠️ Ver `DOCUMENTACAO_TECNICA.md` para a observação sobre o hash sem salt.

## 📶 Protocolo de comunicação BLE

O app implementa um cliente GATT compatível com o padrão **Nordic UART Service**:

| UUID | Papel |
|---|---|
| `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` | Serviço |
| `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` | Característica TX (notificação) |
| `00002902-0000-1000-8000-00805f9b34fb` | Descriptor CCCD |

**Protocolo de mensagens (texto ASCII):**
- Números `"1"` a `"6"` → nível de pressão lido em tempo real pelo ESP32
- `"Enviado"` → sinal de confirmação enviado pelo ESP32 (botão físico) indicando que o último valor lido deve ser persistido como definitivo para aquele ponto

## ⚙️ Pré-requisitos

- Android Studio (recomendado: versão compatível com AGP 8.13.0 / Kotlin 2.0.21 — ver nota de inconsistência de versões em `DOCUMENTACAO_TECNICA.md` antes de sincronizar o projeto)
- JDK 17+ (requisito do AGP 8.x, ainda que o `compileOptions` do módulo declare `JavaVersion.VERSION_1_8` para bytecode)
- Um dispositivo Android físico com Bluetooth LE (emuladores não suportam BLE real)
- Projeto Firebase configurado com Authentication (E-mail/Senha) e Firestore habilitados
- Arquivo `google-services.json` próprio (não incluído no repositório por segurança)
- Hardware Estesiômetro Digital (ESP32 + firmware compatível com o protocolo acima) para testes ponta a ponta

## 🚀 Configuração e execução

```bash
# 1. Clone o repositório
git clone https://github.com/<usuario>/EstesioTech.git
cd EstesioTech

# 2. Adicione seu google-services.json em app/
#    (obtido no console do Firebase: Configurações do projeto > Seus apps)

# 3. Abra no Android Studio e aguarde o Gradle Sync

# 4. Conecte um dispositivo físico com Bluetooth ativado

# 5. Execute o módulo "app"
```

## 📌 Status do projeto e roadmap

| Componente | Status |
|---|---|
| Login/Cadastro (CRM+UF) | ✅ Implementado |
| Radar BLE | ✅ Implementado |
| Execução do exame (TesteActivity) | ✅ Implementado |
| Persistência Firestore | ✅ Implementado |
| Geração de PDF | 🚧 Referenciado, não documentado em detalhe |
| Home / Histórico / Configurações | 🚧 Não documentado em detalhe |
| Tema com correção de daltonismo | 🚧 Não documentado em detalhe |
| Camada ViewModel | ❌ Não implementada |
| Testes automatizados | ❌ Não implementados (apenas dependências de teste presentes no Gradle) |

## 🩹 Dívida técnica conhecida

Resumo — detalhamento completo em [`DOCUMENTACAO_TECNICA.md`](./DOCUMENTACAO_TECNICA.md):

1. `SessionCache` declarado em duplicidade em dois pacotes diferentes
2. Version catalog (`libs.versions.toml`) desconectado do `build.gradle.kts` do módulo
3. Hash de CPF sem salt
4. Ausência de camada `ViewModel`
5. Coordenadas anatômicas dos pontos de exame hardcoded como percentuais fixos
6. `minifyEnabled = false` em build de release

## 👥 Equipe

- **Desenvolvimento:** Glauco — discente de Análise e Desenvolvimento de Sistemas, IFPE Campus Recife
- **Orientação:** Prof. Me. Hilson Gomes Vilar de Andrade
- **Projeto vinculado:** Ecossistema Hansen.AI — IFPE Campus Recife

---

<p align="center">Feito com 🩺 e ☕ para apoiar profissionais de saúde no combate à Hanseníase e Neuropatia Diabética.</p>
