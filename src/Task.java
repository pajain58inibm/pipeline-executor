import java.util.List;

public class Task {

    String id;
    List<String> dependencies;
    int maxRetries;

    int attempts = 0;
    Status status = Status.PENDING;

    long timeoutMs = 2000;

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
        Thread.sleep(200);
    }
}
