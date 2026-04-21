import java.util.List;

public class Main {

    public static void main(String[] args) {

        runTest("TEST 1: NORMAL DAG", Main::testNormalPipeline);
        runTest("TEST 2: RETRY SCENARIO", Main::testRetryPipeline);
        runTest("TEST 3: FAILURE SCENARIO", Main::testFailurePipeline);
        runTest("TEST 4: CYCLE DETECTION", Main::testCyclePipeline);
    }

    // 🔧 Wrapper to measure execution time
    private static void runTest(String testName, Runnable testMethod) {
        System.out.println("\n========== " + testName + " ==========");

        long start = System.currentTimeMillis();

        testMethod.run();

        long end = System.currentTimeMillis();

        System.out.println("Execution time: " + (end - start) + " ms");
    }

    // ✅ Test 1: Normal DAG
    private static void testNormalPipeline() {
        Task A = new Task("A", List.of(), 2);
        Task B = new Task("B", List.of("A"), 2);
        Task C = new Task("C", List.of("A"), 2);
        Task D = new Task("D", List.of("B", "C"), 2);

        List<Task> tasks = List.of(A, B, C, D);

        PipelineExecutor executor = new PipelineExecutor(tasks);
        executor.execute();
    }

    // 🔁 Test 2: Retry scenario
    private static void testRetryPipeline() {

        Task A = new Task("A", List.of(), 2);

        Task B = new Task("B", List.of("A"), 2) {
            @Override
            public void execute() throws Exception {
                System.out.println("[START] " + id + " attempt " + attempts);

                // Fail first attempt, succeed second
                if (attempts < 2) {
                    throw new Exception("Simulated failure");
                }

                Thread.sleep(200);
                System.out.println("[END] " + id);
            }
        };

        Task C = new Task("C", List.of("B"), 2);

        List<Task> tasks = List.of(A, B, C);

        PipelineExecutor executor = new PipelineExecutor(tasks);
        executor.execute();
    }

    // ❌ Test 3: Permanent failure
    private static void testFailurePipeline() {

        Task A = new Task("A", List.of(), 2);

        Task B = new Task("B", List.of("A"), 1) {
            @Override
            public void execute() throws Exception {
                System.out.println("[START] " + id);
                throw new Exception("Always failing");
            }
        };

        Task C = new Task("C", List.of("B"), 2);

        List<Task> tasks = List.of(A, B, C);

        PipelineExecutor executor = new PipelineExecutor(tasks);
        executor.execute();
    }

    // 🔄 Test 4: Cycle detection
    private static void testCyclePipeline() {

        Task A = new Task("A", List.of("C"), 2);
        Task B = new Task("B", List.of("A"), 2);
        Task C = new Task("C", List.of("B"), 2);

        List<Task> tasks = List.of(A, B, C);

        PipelineExecutor executor = new PipelineExecutor(tasks);
        executor.execute();
    }
}
