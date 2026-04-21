public class StageResult {
    String key;
    String stageId;
    String pipelineId;
    String status;
    long durationMs;

    public StageResult(String key, String stageId, String pipelineId, String status, long durationMs) {
        this.key = key;
        this.stageId = stageId;
        this.pipelineId = pipelineId;
        this.status = status;
        this.durationMs = durationMs;
    }
}
