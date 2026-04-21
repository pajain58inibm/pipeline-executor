import java.util.List;

public class Task {

    String id;
    List<String> dependencies;
    int maxRetries;

    int attempts = 0;
    Status status = Status.PENDING;

    long timeoutMs = 2000; // ⏱️ timeout support

    enum Status {
        PENDING, RUNNING, SUCCESS, FAILED
    }

    public Task(String id, List<String> dependencies, int maxRetries) {
        this.id = id;
        this.dependencies = dependencies;
        this.maxRetries = maxRetries;
    }

    public void execute() throws Exception {
        System.out.println("[EXEC] " + id);

        // Simulate work
        Thread.sleep(200);

        // Optional: simulate failure
        // if (id.equals("B")) throw new Exception("Failing B");
    }
}
