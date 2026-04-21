# 🚀 Pipeline Executor (DAG-based Workflow Engine)

A lightweight **workflow execution engine** that models tasks as a **Directed Acyclic Graph (DAG)** and executes them with:

* ⚡ Parallel wave scheduling
* 🔁 Retry + idempotency guarantees
* ❌ Failure propagation (SKIPPED downstream tasks)
* 🧵 Worker pool (bounded concurrency)
* 📊 Structured logging for observability

---

# 🧠 Problem Statement

Design a system where:

* Tasks have dependencies
* A task runs only after its dependencies complete
* System should handle retries, failures, and parallel execution

---

# 🏗️ Architecture Overview

### DAG Representation

* Each task = node
* Dependencies = directed edges
* Uses:

  * `adjList` → dependency graph
  * `inDegree` → pending dependencies

### Execution Model

* Uses **Kahn’s Algorithm (Topological Sort)**
* Executes tasks in **waves**:

  * All tasks with `inDegree = 0` run in parallel
  * Next wave triggered after completion

---

# ⚡ Features

## 1. Parallel Wave Scheduling

* Implemented using `CompletableFuture`
* Uses:

  ```java
  CompletableFuture.allOf(...).join();
  ```
* Enables **fan-out execution** of independent tasks

---

## 2. Worker Pool (Bounded Concurrency)

* Uses:

  ```java
  Executors.newFixedThreadPool(N)
  ```
* Prevents resource exhaustion
* Models real-world worker systems

---

## 3. Idempotency

* Ensures tasks are executed **exactly once**
* Implemented using:

  ```java
  ConcurrentHashMap.putIfAbsent()
  ```
* Prevents duplicate execution during retries

---

## 4. Retry Mechanism

* Each task has `maxRetries`
* Simple backoff using `Thread.sleep`
* Handles transient failures gracefully

---

## 5. Failure Propagation

* If a task fails:

  * All downstream tasks are marked **SKIPPED**
* Avoids wasted computation
* Models real CI/CD behavior

---

## 6. Observability (Structured Logging)

Each stage emits logs like:

```text
pipeline_id=pipeline-normal stage_id=B status=SUCCESS duration_ms=512
```

Fields:

* `pipeline_id`
* `stage_id`
* `status` (SUCCESS / FAILED / SKIPPED)
* `duration_ms`

---

## 7. Cycle Detection

* If tasks remain unprocessed → cycle exists
* Prevents invalid DAG execution

---

# 🧪 Test Scenarios

## ✅ Normal DAG (Parallel Execution)

```
A → B → D
A → C → D
```

* B and C run in parallel after A

---

## 🔁 Retry Scenario

* Task fails first attempt → succeeds on retry

---

## ❌ Failure Scenario

* Task fails permanently
* Downstream tasks are SKIPPED

---

## 🔄 Cycle Detection

```
A → B → C → A
```

* Execution halts due to cycle

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
 └── Main.java
```

---

# 🧠 Key Concepts Demonstrated

* DAG execution & Topological Sort
* Concurrent programming with thread pools
* Idempotent system design
* Retry & failure handling strategies
* Observability & structured logging

---

# 🔥 Future Improvements

* Distributed queue (Kafka / RabbitMQ)
* Persistent state (Redis / DB)
* REST API to trigger pipelines
* DAG visualization UI
* Priority scheduling

---

# 💬 Interview Talking Points

You can confidently say:

> “This system models a DAG-based pipeline executor with parallel scheduling, idempotent retries, and failure propagation, similar to workflow engines like Airflow or CI/CD systems.”

---

# 📌 Summary

This project demonstrates how to design and implement a **scalable, fault-tolerant workflow execution system** with real-world engineering tradeoffs.

---
