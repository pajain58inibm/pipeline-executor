import java.util.*;

public class PipelineExecutor {

    private Map<String, Task> taskMap = new HashMap<>();
    private Map<String, List<String>> adjList = new HashMap<>();
    private Map<String, Integer> inDegree = new HashMap<>();

    public PipelineExecutor(List<Task> tasks) {
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

        for (String taskId : inDegree.keySet()) {
            if (inDegree.get(taskId) == 0) {
                queue.add(taskId);
            }
        }

        int processed = 0;

        while (!queue.isEmpty()) {
            String taskId = queue.poll();
            Task task = taskMap.get(taskId);

            runWithRetry(task);

            if (task.status != Task.Status.SUCCESS) {
                System.out.println("Pipeline failed at task: " + taskId);
                return;
            }

            processed++;

            for (String neighbor : adjList.get(taskId)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);

                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (processed != taskMap.size()) {
            System.out.println("Cycle detected in pipeline!");
        } else {
            System.out.println("Pipeline executed successfully!");
        }
    }

    private void runWithRetry(Task task) {
        if (task.status == Task.Status.SUCCESS) return;

        while (task.attempts <= task.maxRetries) {
            try {
                task.status = Task.Status.RUNNING;
                task.attempts++;

                task.execute();

                task.status = Task.Status.SUCCESS;
                return;

            } catch (Exception e) {
                System.out.println("[RETRY] " + task.id + " attempt " + task.attempts);

                if (task.attempts > task.maxRetries) {
                    task.status = Task.Status.FAILED;
                    return;
                }

                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {}
            }
        }
    }
}
