# 🚀 Pipeline Executor (DAG-based Workflow Engine)

A lightweight **workflow execution engine** that models tasks as a **Directed Acyclic Graph (DAG)** and executes them with production-grade concerns:

* ⚡ Parallel wave scheduling (`CompletableFuture`)
* 🧵 Bounded worker pool with backpressure
* 🔁 Idempotent retries with strong key design
* ❌ Failure propagation (SKIPPED downstream)
* ⏱️ Per-stage timeouts
* 🧭 Configurable failure policies (FAIL_FAST / CONTINUE)
* 📊 Structured logging with trace IDs
* 📉 Retry budgets, exponential backoff + jitter
* 🚦 Queue limits and rejection policy

---

# 🧠 Problem Statement

Design a pipeline system where:

* Tasks have dependencies (DAG)
* Tasks run only after dependencies complete
* System handles retries, failures, timeouts, and parallel execution

---

# 🏗️ Architecture Overview

## DAG Representation

* Nodes → `Task`
* Edges → dependencies
* Data structures:

  * `adjList` → dependency graph
  * `inDegree` → unresolved dependencies

## Execution Model

* Based on **Kahn’s Algorithm (Topological Sort)**
* Executes tasks in **waves**:

  * All `inDegree = 0` tasks run in parallel
  * Next wave starts after completion

---

# ⚙️ Advanced Execution Features

## 1. Parallel Wave Scheduling

* Uses `CompletableFuture.runAsync()`
* Synchronization via:

  ```java
  CompletableFuture.allOf(...).join();
  ```
* Enables **fan-out / fan-in execution**

---

## 2. Worker Pool & Backpressure

### Implementation

```java
new ThreadPoolExecutor(
  N, N,
  0L,
  TimeUnit.MILLISECONDS,
  new ArrayBlockingQueue<>(50),
  new ThreadPoolExecutor.AbortPolicy()
)
```

### Behavior

* Bounded queue prevents overload
* If queue fills → **task rejected**
* Models real-world **backpressure**

---

## 3. Failure Policy

### Options

* `FAIL_FAST` → stop pipeline immediately on failure
* `CONTINUE` → continue independent branches

### Tradeoff

* FAIL_FAST → saves compute
* CONTINUE → maximizes partial success

---

## 4. Transitive Failure Propagation

If a stage fails:

* All downstream stages are marked **SKIPPED**
* No unnecessary execution

---

## 5. Timeout Handling

Each task has:

```java
long timeoutMs;
```

Enforced via:

```java
future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
```

If exceeded:

* Stage marked **FAILED**
* Failure policy applied

---

# 🔁 Idempotency Design

## Key Structure

```
pipelineId : runId : stageId : commitSha
```

### Why this works

* Uniquely identifies execution context
* Prevents duplicate execution across retries

---

## Implementation

```java
ConcurrentHashMap.putIfAbsent(key, result)
```

---

## Crash-after-success Problem

> What if process crashes after execution but before storing result?

### Mitigation (Design)

* External store (DB/Redis)
* Write-before-acknowledge pattern

---

# 🔁 Retry Strategy

## Features

* Per-task retry limit
* Global retry budget
* Exponential backoff + jitter

### Example

```java
backoff = 2^attempt * base + random_jitter
```

---

# 📊 Observability

## Structured Logging

Example:

```text
trace_id=run123 pipeline_id=pipeline1 stage_id=B status=SUCCESS duration_ms=512
```

## Fields

* `trace_id` → correlates entire run
* `pipeline_id`
* `stage_id`
* `status`
* `duration_ms`

---

## Metrics (Design)

Can be added via:

* Counters:

  * total_tasks
  * success_count
  * failure_count
* Histograms:

  * execution_latency

### Alerts (what to monitor)

* High failure rate
* Retry spikes
* Long-running stages
* Queue saturation

---

## MDC (Mapped Diagnostic Context)

In production:

* Use SLF4J MDC to auto-attach:

  * trace_id
  * pipeline_id

---

# 🧪 Test Scenarios

## ✅ Normal DAG

```
A → B → D
A → C → D
```

* B & C run in parallel

---

## 🔁 Retry Scenario

* Task fails once → succeeds on retry

---

## ❌ Failure Scenario

* Task fails permanently
* Downstream tasks SKIPPED

---

## 🔄 Cycle Detection

```
A → B → C → A
```

* Execution halts

---

# ▶️ How to Run

```bash
javac src/*.java
java -cp src Main
```

---

# 📂 Project Structure

```
src/
 ├── Task.java
 ├── PipelineExecutor.java
 ├── StageResult.java
 ├── FailurePolicy.java
 └── Main.java
```

---

# 🔮 Future Improvements

* Distributed queue (Kafka / RabbitMQ)
* Persistent execution state (Redis / DB)
* Priority scheduling (`PriorityBlockingQueue`)
* REST API trigger
* DAG visualization UI

---

# 🔁 Migration to Distributed System (Interview Talking Point)

To scale:

1. Replace in-memory queue → Kafka topic
2. Workers → distributed consumers
3. Idempotency store → Redis
4. State → persistent DB

---

# 💬 Interview Talking Points

You can confidently say:

> “This system supports DAG-based parallel execution with bounded concurrency, idempotent retries, failure propagation, timeout handling, and structured observability, similar to workflow engines like Airflow or CI/CD systems.”

---

# 📌 Summary

This project demonstrates how to design a **fault-tolerant, scalable workflow engine** with real-world production considerations and tradeoffs.

---
