public class StageResult {
    String stageId;
    String pipelineId;
    String status; // SUCCESS, FAILED, SKIPPED
    long durationMs;

    public StageResult(String stageId, String pipelineId, String status, long durationMs) {
        this.stageId = stageId;
        this.pipelineId = pipelineId;
        this.status = status;
        this.durationMs = durationMs;
    }
}
