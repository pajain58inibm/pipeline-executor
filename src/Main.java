import java.util.List;
import java.util.UUID;

public class Main {

    public static void main(String[] args) {

        runTest("TEST 1: NORMAL DAG (PARALLEL)", Main::testNormalPipeline);

        runTest("TEST 2: RETRY SCENARIO", Main::testRetryPipeline);

        runTest("TEST 3: FAILURE WITH SKIP PROPAGATION", Main::testFailurePipeline);

        runTest("TEST 4: CYCLE DETECTION", Main::testCyclePipeline);
    }

    private static void runTest(String testName, Runnable testMethod) {
        System.out.println("\n========== " + testName + " ==========");

        long start = System.currentTimeMillis();

        testMethod.run();

        long end = System.currentTimeMillis();

        System.out.println("Execution time: " + (end - start) + " ms");
    }

    // Helper to generate run metadata
    private static PipelineExecutor createExecutor(List<Task> tasks, int workers, String pipelineId) {
        String runId = UUID.randomUUID().toString();
        String commitSha = "commit-" + System.currentTimeMillis();

        return new PipelineExecutor(
                tasks,
                workers,
                pipelineId,
                runId,
                commitSha,
                FailurePolicy.CONTINUE // change to FAIL_FAST to demo behavior
        );
    }

    // ✅ TEST 1
    private static void testNormalPipeline() {

        Task A = new Task("A", List.of(), 2);

        Task B = new Task("B", List.of("A"), 2) {
            @Override
            public void execute() throws Exception {
                Thread.sleep(500);
                System.out.println("[WORK] B done");
            }
        };

        Task C = new Task("C", List.of("A"), 2) {
            @Override
            public void execute() throws Exception {
                Thread.sleep(500);
                System.out.println("[WORK] C done");
            }
        };

        Task D = new Task("D", List.of("B", "C"), 2);

        List<Task> tasks = List.of(A, B, C, D);

        PipelineExecutor executor = createExecutor(tasks, 4, "pipeline-normal");

        executor.execute();
    }

    // 🔁 TEST 2
    private static void testRetryPipeline() {

        Task A = new Task("A", List.of(), 2);

        Task B = new Task("B", List.of("A"), 2) {
            @Override
            public void execute() throws Exception {
                System.out.println("[WORK] B attempt " + attempts);

                if (attempts < 2) {
                    throw new Exception("Simulated failure");
                }

                Thread.sleep(200);
                System.out.println("[WORK] B success");
            }
        };

        Task C = new Task("C", List.of("B"), 2);

        List<Task> tasks = List.of(A, B, C);

        PipelineExecutor executor = createExecutor(tasks, 3, "pipeline-retry");

        executor.execute();
    }

    // ❌ TEST 3
    private static void testFailurePipeline() {

        Task A = new Task("A", List.of(), 2);

        Task B = new Task("B", List.of("A"), 1) {
            @Override
            public void execute() throws Exception {
                System.out.println("[WORK] B always fails");
                throw new Exception("Permanent failure");
            }
        };

        Task C = new Task("C", List.of("B"), 2);
        Task D = new Task("D", List.of("C"), 2);

        List<Task> tasks = List.of(A, B, C, D);

        PipelineExecutor executor = createExecutor(tasks, 3, "pipeline-failure");

        executor.execute();
    }

    // 🔄 TEST 4
    private static void testCyclePipeline() {

        Task A = new Task("A", List.of("C"), 2);
        Task B = new Task("B", List.of("A"), 2);
        Task C = new Task("C", List.of("B"), 2);

        List<Task> tasks = List.of(A, B, C);

        PipelineExecutor executor = createExecutor(tasks, 2, "pipeline-cycle");

        executor.execute();
    }
}
