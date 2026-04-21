# Pipeline Executor (DAG आधारित)

## Features
- DAG-based execution (Topological Sort)
- Retry with backoff
- Idempotency-safe execution
- Cycle detection

## How it works
- Uses Kahn’s Algorithm
- Tracks in-degree for dependency resolution

## Example
A → B → D  
A → C → D

## Future Improvements
- Parallel execution (thread pool)
- Distributed queue
- Persistent state
