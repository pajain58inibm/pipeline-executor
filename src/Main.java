import java.util.List;

public class Main {
    public static void main(String[] args) {

        Task A = new Task("A", List.of(), 2);
        Task B = new Task("B", List.of("A"), 2);
        Task C = new Task("C", List.of("A"), 2);
        Task D = new Task("D", List.of("B", "C"), 2);

        List<Task> tasks = List.of(A, B, C, D);

        PipelineExecutor executor = new PipelineExecutor(tasks);
        executor.execute();
    }
}
