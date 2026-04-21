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

    private int retryBudget = 20;

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
                new ArrayBlockingQueue<>(50),
                new ThreadPoolExecutor.AbortPolicy()
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
            List<String> currentWave = new ArrayList<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < size; i++) {
                String stageId = queue.poll();
                currentWave.add(stageId);

                Task task = taskMap.get(stageId);

                futures.add(
                    CompletableFuture.runAsync(() -> executeStage(task), workerPool)
                    .orTimeout(task.timeoutMs, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        markFailed(stageId);
                        return null;
                    })
                );
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            for (String stageId : currentWave) {

                StageResult res = resultStore.get(buildKey(stageId));
                if (res == null) continue;

                if (res.status.equals("FAILED")) {
                    if (failurePolicy == FailurePolicy.FAIL_FAST) return;
                    markDownstreamSkipped(stageId);
                }

                List<String> neighbors = adjList.get(stageId);
                if (neighbors == null) continue;

                for (String neighbor : neighbors) {
                    inDegree.put(neighbor, inDegree.get(neighbor) - 1);

                    if (inDegree.get(neighbor) == 0 &&
                            !resultStore.containsKey(buildKey(neighbor))) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        workerPool.shutdown();
        System.out.println("Pipeline completed.");
    }

    private void executeStage(Task task) {

        String key = buildKey(task.id);
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
                if (task.attempts > task.maxRetries) break;

                try {
                    long backoff = (long) (Math.pow(2, task.attempts) * 100 + Math.random() * 50);
                    Thread.sleep(backoff);
                } catch (InterruptedException ignored) {}
            }
        }

        long duration = System.currentTimeMillis() - start;

        resultStore.putIfAbsent(key,
                new StageResult(key, task.id, pipelineId, status, duration));

        log(task.id, status, duration);
    }

    private void markFailed(String stageId) {
        resultStore.putIfAbsent(buildKey(stageId),
                new StageResult(buildKey(stageId), stageId, pipelineId, "FAILED", 0));
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
