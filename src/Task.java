import java.util.List;
import java.util.Random;

public class Task {
    String id;
    List<String> dependencies;
    int maxRetries;

    int attempts = 0;
    Status status = Status.PENDING;

    enum Status {
        PENDING, RUNNING, SUCCESS, FAILED
    }

    public Task(String id, List<String> dependencies, int maxRetries) {
        this.id = id;
        this.dependencies = dependencies;
        this.maxRetries = maxRetries;
    }

    public void execute() throws Exception {
        System.out.println("[START] " + id);

        // simulate random failure
        if (new Random().nextInt(10) < 2) {
            throw new Exception("Random failure");
        }

        Thread.sleep(200);
        System.out.println("[END] " + id);
    }
}
