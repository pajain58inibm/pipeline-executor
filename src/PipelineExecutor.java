import java.util.*;
import java.util.concurrent.*;

public class PipelineExecutor {

    private Map<String, Task> taskMap = new HashMap<>();
    private Map<String, List<String>> adjList = new HashMap<>();
    private Map<String, Integer> inDegree = new HashMap<>();

    private Map<String, StageResult> resultStore = new ConcurrentHashMap<>();
    private ExecutorService workerPool;

    private String pipelineId;

    public PipelineExecutor(List<Task> tasks, int workers, String pipelineId) {
        this.pipelineId = pipelineId;
        this.workerPool = Executors.newFixedThreadPool(workers);
        buildGraph(tasks);
    }

    private void buildGraph(List<Task> tasks) {
        for (Task task : tasks) {
            taskMap.put(task.id, task);
            inDegree.put(task.id, 0);
            adjList.put(task.id, new ArrayList<>());
        }

        for (Task task : tasks) {
            for (String dep : task.dependencies) {
                adjList.get(dep).add(task.id);
                inDegree.put(task.id, inDegree.get(task.id) + 1);
            }
        }
    }

    public void execute() {
        Queue<String> queue = new LinkedList<>();

        for (String id : inDegree.keySet()) {
            if (inDegree.get(id) == 0) queue.add(id);
        }

        while (!queue.isEmpty()) {

            int waveSize = queue.size();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // 🚀 Execute current wave in parallel
            for (int i = 0; i < waveSize; i++) {
                String taskId = queue.poll();
                Task task = taskMap.get(taskId);

                futures.add(CompletableFuture.runAsync(() -> executeStage(task), workerPool));
            }

            // ⏳ Wait for wave to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 🔄 Process results and prepare next wave
            for (CompletableFuture<Void> f : futures) {
                // nothing needed here, results already stored
            }

            // Now update next wave
            for (String completed : new ArrayList<>(resultStore.keySet())) {

                StageResult result = resultStore.get(completed);

                // ❌ If failed → mark downstream as skipped
                if (result.status.equals("FAILED")) {
                    markDownstreamSkipped(completed);
                }

                // Reduce in-degree
                for (String neighbor : adjList.get(completed)) {
                    inDegree.put(neighbor, inDegree.get(neighbor) - 1);

                    if (inDegree.get(neighbor) == 0 &&
                        !resultStore.containsKey(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        workerPool.shutdown();
        System.out.println("Pipeline finished.");
    }

    // 🚀 Execute a single stage with idempotency + retry
    private void executeStage(Task task) {

        // 🔁 Idempotency check
        if (resultStore.containsKey(task.id)) return;

        long start = System.currentTimeMillis();
        String status = "FAILED";

        try {
            runWithRetry(task);
            status = (task.status == Task.Status.SUCCESS) ? "SUCCESS" : "FAILED";
        } catch (Exception e) {
            status = "FAILED";
        }

        long duration = System.currentTimeMillis() - start;

        // 🔁 Idempotency write
        resultStore.putIfAbsent(task.id,
                new StageResult(task.id, pipelineId, status, duration));

        // 📊 Structured logging
        log(task.id, status, duration);
    }

    private void markDownstreamSkipped(String failedTask) {
        Queue<String> q = new LinkedList<>();
        q.add(failedTask);

        while (!q.isEmpty()) {
            String curr = q.poll();

            for (String neighbor : adjList.get(curr)) {
                if (!resultStore.containsKey(neighbor)) {

                    resultStore.put(neighbor,
                            new StageResult(neighbor, pipelineId, "SKIPPED", 0));

                    log(neighbor, "SKIPPED", 0);

                    q.add(neighbor);
                }
            }
        }
    }

    private void runWithRetry(Task task) {
        while (task.attempts <= task.maxRetries) {
            try {
                task.status = Task.Status.RUNNING;
                task.attempts++;

                task.execute();

                task.status = Task.Status.SUCCESS;
                return;

            } catch (Exception e) {
                if (task.attempts > task.maxRetries) {
                    task.status = Task.Status.FAILED;
                    return;
                }

                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    // 📊 Structured logging
    private void log(String stageId, String status, long duration) {
        System.out.println(
                "pipeline_id=" + pipelineId +
                " stage_id=" + stageId +
                " status=" + status +
                " duration_ms=" + duration
        );
    }
}
