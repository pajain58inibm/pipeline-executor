import java.util.*;
import java.util.concurrent.*;

public class PipelineExecutor {

    private Map<String, Task> taskMap = new HashMap<>();
    private Map<String, List<String>> adjList = new HashMap<>();
    private Map<String, Integer> inDegree = new HashMap<>();

    private Map<String, StageResult> resultStore = new ConcurrentHashMap<>();

    private ExecutorService workerPool;

    private String pipelineId;
    private String runId;
    private String commitSha;

    private FailurePolicy failurePolicy;

    private int retryBudget = 10; // global retry cap

    public PipelineExecutor(List<Task> tasks,
                            int workers,
                            String pipelineId,
                            String runId,
                            String commitSha,
                            FailurePolicy failurePolicy) {

        this.pipelineId = pipelineId;
        this.runId = runId;
        this.commitSha = commitSha;
        this.failurePolicy = failurePolicy;

        this.workerPool = new ThreadPoolExecutor(
                workers,
                workers,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(50), // backpressure
                new ThreadPoolExecutor.AbortPolicy() // rejection
        );

        buildGraph(tasks);
    }

    private void buildGraph(List<Task> tasks) {
        for (Task t : tasks) {
            taskMap.put(t.id, t);
            inDegree.put(t.id, 0);
            adjList.put(t.id, new ArrayList<>());
        }

        for (Task t : tasks) {
            for (String dep : t.dependencies) {
                adjList.get(dep).add(t.id);
                inDegree.put(t.id, inDegree.get(t.id) + 1);
            }
        }
    }

    public void execute() {
        Queue<String> queue = new LinkedList<>();

        for (String id : inDegree.keySet()) {
            if (inDegree.get(id) == 0) queue.add(id);
        }

        while (!queue.isEmpty()) {

            int size = queue.size();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < size; i++) {
                String id = queue.poll();
                Task task = taskMap.get(id);

                futures.add(CompletableFuture.runAsync(() -> executeStage(task), workerPool)
                        .orTimeout(task.timeoutMs, TimeUnit.MILLISECONDS)
                        .exceptionally(ex -> {
                            markFailed(task.id);
                            return null;
                        }));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            for (String completed : new ArrayList<>(resultStore.keySet())) {

                StageResult res = resultStore.get(completed);

                if (res.status.equals("FAILED")) {
                    if (failurePolicy == FailurePolicy.FAIL_FAST) return;
                    markDownstreamSkipped(completed);
                }

                for (String neighbor : adjList.get(completed)) {
                    inDegree.put(neighbor, inDegree.get(neighbor) - 1);

                    if (inDegree.get(neighbor) == 0 &&
                            !resultStore.containsKey(buildKey(neighbor))) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        workerPool.shutdown();
    }

    private void executeStage(Task task) {

        String key = buildKey(task.id);

        // idempotency
        if (resultStore.containsKey(key)) return;

        long start = System.currentTimeMillis();

        String status = "FAILED";

        while (task.attempts <= task.maxRetries && retryBudget > 0) {
            try {
                task.attempts++;
                retryBudget--;

                task.execute();

                status = "SUCCESS";
                break;

            } catch (Exception e) {

                if (task.attempts > task.maxRetries) {
                    status = "FAILED";
                    break;
                }

                // exponential backoff + jitter
                try {
                    long backoff = (long) (Math.pow(2, task.attempts) * 100 + Math.random() * 50);
                    Thread.sleep(backoff);
                } catch (InterruptedException ignored) {}
            }
        }

        long duration = System.currentTimeMillis() - start;

        StageResult result = new StageResult(key, task.id, pipelineId, status, duration);

        // crash-safe pattern (simulate write-before-ack)
        resultStore.putIfAbsent(key, result);

        log(task.id, status, duration);
    }

    private void markFailed(String taskId) {
        String key = buildKey(taskId);
        resultStore.putIfAbsent(key,
                new StageResult(key, taskId, pipelineId, "FAILED", 0));
    }

    private void markDownstreamSkipped(String failed) {
        Queue<String> q = new LinkedList<>();
        q.add(failed);

        while (!q.isEmpty()) {
            String curr = q.poll();

            for (String next : adjList.get(curr)) {
                String key = buildKey(next);

                if (!resultStore.containsKey(key)) {
                    resultStore.put(key,
                            new StageResult(key, next, pipelineId, "SKIPPED", 0));
                    log(next, "SKIPPED", 0);
                    q.add(next);
                }
            }
        }
    }

    private String buildKey(String stageId) {
        return pipelineId + ":" + runId + ":" + stageId + ":" + commitSha;
    }

    private void log(String stageId, String status, long duration) {
        System.out.println(
                "trace_id=" + runId +
                " pipeline_id=" + pipelineId +
                " stage_id=" + stageId +
                " status=" + status +
                " duration_ms=" + duration
        );
    }
}
