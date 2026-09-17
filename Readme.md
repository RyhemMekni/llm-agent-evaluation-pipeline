# 🛡️ DevSecOps Infrastructure for AI Agent Testing & Security

> **Master 2 BDIA — Université Paris Dauphine-PSL**  
> Internship project at **DiscoveryInTech** · Supervisor: M. Mohamed Hechmi Jeridi  
> Author: **Ryhem Mekni** · 10 sprints · 40+ commits

---

## 📌 Overview

This project builds a **complete DevSecOps infrastructure** for automated testing and security of AI agents based on Large Language Models (LLMs).

Rather than simply building an AI agent, the goal is to answer a critical question for any organisation deploying generative AI in production:

> *How do you automatically guarantee the quality, factuality, and security of an LLM-based agent's responses — before and after each code change?*

The infrastructure combines:
- A **Spring AI agent** (DevSecOps expert domain) enriched with MCP-based context retrieval and web search
- An **LLM-as-a-judge evaluation framework** with 4 independent evaluators + a novel groundedness evaluator
- A **full CI/CD pipeline** (GitLab, 6 stages, 10 jobs) integrating SAST, DAST, and AI-specific security tests
- A **monitoring stack** (Prometheus + Grafana) tracking quality and security metrics over time

---

## 🏗️ Architecture

![Architecture](docs/pfe_devsecops_architecture.png)

```
GitLab (self-hosted, Docker)
        │
        ▼
Pipeline CI/CD (.gitlab-ci.yml)
build → test → evaluate → sast → dast → report
        │                   │
        ▼                   ▼
  agent-ia :8080    agent-evaluateur :8081
  Spring Boot 4     Spring Boot 4
  Groq LLM          LLM-as-a-Judge
  MCP web_search    4 evaluators
        │
        ├── Sécurité: SonarQube · OWASP Dep-Check · OWASP ZAP
        └── Monitoring: Prometheus :9091 · Grafana :3000
```

---

## 📦 Repository Structure

```
pfe-devsecops/
├── agent-ia/                        # Main AI agent (Spring Boot, port 8080)
│   ├── src/main/java/com/pfe/agentia/
│   │   ├── controller/              # AgentController — /api/agent/ask
│   │   ├── service/                 # AgentService — LLM orchestration
│   │   └── mcp/
│   │       └── CapturingToolCallback.java  # Intercepts web_search content
│   └── src/test/                    # 16 unit tests + 3 integration tests
│
├── agent-evaluateur/                # Evaluation agent (Spring Boot, port 8081)
│   ├── src/main/java/com/pfe/evaluateur/
│   │   ├── evaluation/
│   │   │   ├── RelevancyEvaluator.java        # Response relevance (≥80%)
│   │   │   ├── FactCheckingEvaluator.java     # Hallucination detection (≤20%)
│   │   │   ├── ConformityEvaluator.java       # Domain scope compliance
│   │   │   ├── PromptInjectionEvaluator.java  # Jailbreak resistance
│   │   │   └── GroundednessEvaluator.java     # ✨ NEW — anchoring on retrieved content
│   │   └── service/
│   │       ├── BatchEvaluationService.java    # 10-case batch evaluation
│   │       ├── PromptInjectionService.java    # 10 attack scenarios
│   │       └── MetricsService.java            # Prometheus metrics exposure
│   ├── datasets/
│   │   └── dataset_evaluation.json           # 10 structured test cases
│   └── docs/
│       ├── RAPPORT_HALLUCINATION_WebSearch.md # Sprint 10 investigation report
│       ├── RAPPORT_BUG_FactCheckingEvaluator.md
│       ├── PROCESSUS_TEST_EVALUATION.md
│       └── logs-tests/                        # Raw evaluation test logs
│
├── prometheus/
│   └── prometheus.yml               # Scrape config for agent-evaluateur
│
└── .gitlab-ci.yml                   # 6-stage CI/CD pipeline
```

---

## ⚙️ Tech Stack

| Layer | Technology |
|---|---|
| Agent framework | Java 21 · Spring Boot 4 · Spring AI 2.0 |
| LLM provider | Groq API (compatible OpenAI) · `openai/gpt-oss-120b` |
| MCP protocol | `spring-ai-starter-mcp-client` · HTTP Streamable transport |
| Web search | `mcp-server-websearch` · SearXNG (self-hosted meta-search) |
| Build | Maven 3.9 |
| Containerisation | Docker Desktop · Docker Compose |
| CI/CD | GitLab CE (self-hosted) · GitLab Runner |
| SAST | SonarQube · OWASP Dependency-Check |
| DAST | OWASP ZAP |
| Monitoring | Prometheus · Grafana · Micrometer |
| Testing | JUnit 5 · Mockito |

---

## 🚀 Quick Start

### Prerequisites

- Docker Desktop running
- Java 21+, Maven 3.9+
- A [Groq API key](https://console.groq.com) (free tier works)

### 1. Start infrastructure

```bash
# From project root
docker compose up -d
# Starts: GitLab, GitLab Runner, SonarQube, ZAP, Prometheus, Grafana
```

### 2. Start the AI agent

```bash
cd agent-ia
mvn spring-boot:run \
  -Dspring.ai.openai.api-key=YOUR_GROQ_KEY \
  -Dspring.ai.openai.base-url=https://api.groq.com/openai/v1 \
  -Dspring.ai.openai.chat.model=openai/gpt-oss-120b
```

### 3. Start the evaluation agent

```bash
cd agent-evaluateur
mvn spring-boot:run \
  -Dspring.ai.openai.api-key=YOUR_GROQ_KEY \
  -Dspring.ai.openai.base-url=https://api.groq.com/openai/v1 \
  -Dspring.ai.openai.chat.model=openai/gpt-oss-120b
```

### 4. Test the agent

```bash
# Ask a DevSecOps question
curl "http://localhost:8080/api/agent/ask?question=Qu%27est-ce%20que%20le%20SAST%3F"

# Run a single evaluation (relevance + factuality)
curl "http://localhost:8081/api/evaluateur/evaluer?testId=TEST-01&question=Qu%27est-ce%20que%20le%20SAST%3F&contexte=Le%20SAST%20analyse%20le%20code%20source"

# Run full batch evaluation (10 cases)
curl http://localhost:8081/api/evaluateur/batch

# Run prompt injection test (10 attack scenarios)
curl http://localhost:8081/api/evaluateur/prompt-injection
```

### 5. (Optional) Start web search capability

```bash
# Start SearXNG
docker run -d --name searxng -p 8080:8080 searxng/searxng

# Start MCP web search server
cd ../mcp-server-websearch
mvn spring-boot:run
# Runs on port 8060
```

---

## 🧪 Evaluation Framework

### Evaluators (LLM-as-a-Judge pattern)

| Evaluator | Criterion | Threshold | Details |
|---|---|---|---|
| `RelevancyEvaluator` | Response relevance | ≥ 80% | Checks coherence with actual MCP context used |
| `FactCheckingEvaluator` | Hallucination detection | ≤ 20% hallucination | Against reference context from dataset |
| `ConformityEvaluator` | Domain scope compliance | Pass/Fail | Must refuse out-of-scope questions |
| `PromptInjectionEvaluator` | Jailbreak resistance | Pass/Fail | 10 attack categories (direct, roleplay, DAN...) |
| `GroundednessEvaluator` ✨ | Claim anchoring | ≥ 80% anchored | Verifies each claim exists in `webSearchContent` |

### Test Dataset

10 structured test cases covering 6 DevSecOps domains, split into 3 categories:

- **6 positive cases** — legitimate in-scope questions (expected: APPROVED)
- **2 out-of-scope cases** — unrelated questions (expected: REJECTED)
- **2 factual trap cases** — questions designed to trigger hallucination (expected: REJECTED)

---

## 🔬 Key Scientific Finding — Sprint 10

During the web search integration, a **structural bias in FactCheckingEvaluator** was empirically discovered and documented:

| Test case | Ground truth | FactChecking verdict | Groundedness verdict |
|---|---|---|---|
| 3 fabricated CVEs | Hallucination | Detected ✅ | Detected ✅ |
| Correct, sourced response | Correct | **False positive ❌** | Accepted ✅ |
| Honest "I don't know" | Correct | **False positive ❌** | Accepted ✅ |

**Root cause:** `FactCheckingEvaluator` relies on the judge LLM's own parametric memory to verify claims. When it cannot confirm a claim from memory, it systematically rejects it — even if the claim is correct. This is a **systematic rejection bias**, not factual checking.

**Fix:** `GroundednessEvaluator` implements the *decompose-then-verify* paradigm (inspired by FActScore, SAFE): each atomic claim is verified against the actual retrieved `webSearchContent`, independent of the judge's own knowledge.

**Result: 7/7 correct verdicts** vs 4/6 for the historical evaluator.

---

## 🛡️ Security Results

### SAST
- **SonarQube**: 1 critical (CORS misconfiguration — fixed), 3 major, 8 informational
- **OWASP Dependency-Check**: 3 high CVE, 4 medium CVE on dependencies

### DAST (OWASP ZAP)
- Missing `X-Content-Type-Options` header (Low)
- HTTP without TLS (Medium — local dev environment)
- **Notable false positive**: Path Traversal alert on `/api/agent/ask` — ZAP detected `</web-app>` in response body, but this was a legitimate educational LLM response about Java EE config files, not an actual file exposure. Documents the fundamental limitation of classical DAST against generative AI endpoints.

### Prompt Injection
- **10/10 attacks resisted** across 5 categories: direct instruction, roleplay (DAN), system prompt extraction, hypothetical framing, obfuscation

---

## 📊 CI/CD Pipeline

```yaml
stages:
  - build        # Maven compile
  - test         # 16 unit tests (no LLM calls, Mockito)
  - evaluate     # LLM-as-a-judge smoke test (auto) + full batch (manual)
  - sast         # SonarQube + OWASP Dependency-Check
  - dast         # OWASP ZAP + Prompt Injection (manual)
  - report       # Consolidated report
```

All security jobs run with `allow_failure: true` (informative mode). Manual jobs preserve Groq API quota by not running on every commit.

---

## 📈 Monitoring

| Metric | Endpoint |
|---|---|
| Quality conformance rate | `http://localhost:9091` → `taux_conformite_pourcent` |
| Prompt injection resistance | `taux_resistance_prompt_injection` |
| Verdict counters | `nombre_approuves_total`, `nombre_rejetes_total` |

Grafana dashboards available at `http://localhost:3000`.

---

## 📁 Documentation

| Document | Location |
|---|---|
| Full PFE report (38 pages, 15 figures) | `docs/RAPPORT_PFE_Ryhem_Mekni.pdf` |
| Sprint 10 hallucination investigation | `agent-evaluateur/docs/RAPPORT_HALLUCINATION_WebSearch.md` |
| FactCheckingEvaluator bug report | `agent-evaluateur/docs/RAPPORT_BUG_FactCheckingEvaluator.md` |
| Test evaluation process | `agent-evaluateur/docs/PROCESSUS_TEST_EVALUATION.md` |
| Raw test logs | `agent-evaluateur/docs/logs-tests/` |

---

## 🔧 Environment Variables

| Variable | Description |
|---|---|
| `GROQ_API_KEY` | Groq API key (set in GitLab CI/CD variables) |
| `SONAR_HOST_URL` | SonarQube server URL |
| `SONAR_TOKEN` | SonarQube authentication token |
| `NVD_API_KEY` | NVD API key for OWASP Dependency-Check |

---

## ⚠️ Known Limitations

- **MCP SDK bug** (`mcp-core:2.0.0`): `McpTransportException` on large SSE payloads — documented, non-blocking workaround in place
- **LLM deprecation risk**: `llama-3.3-70b-versatile` was removed from Groq catalog mid-project without notice, requiring migration to `openai/gpt-oss-120b` — highlights operational risk of hosted LLM dependencies
- **Web search CI integration**: SearXNG and `mcp-server-websearch` not yet connected to the CI/CD Docker network — local testing only
- **Groundedness sample size**: 7 test cases — demonstrates the mechanism, not a statistically robust benchmark

---

## 📚 References

- Spring AI Documentation — [spring.io/projects/spring-ai](https://spring.io/projects/spring-ai)
- Model Context Protocol Specification — [modelcontextprotocol.io](https://modelcontextprotocol.io)
- OWASP Top 10 for LLM Applications — [owasp.org/www-project-top-10-for-large-language-model-applications](https://owasp.org/www-project-top-10-for-large-language-model-applications)
- FActScore (Min et al., 2023) — Fine-grained Atomic Evaluation of Factual Precision
- SAFE (Wei et al., 2024) — Search-Augmented Factuality Evaluator

---

## 📄 License

Academic project — Université Paris Dauphine-PSL, Master 2 BDIA, 2025/2026.