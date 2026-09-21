package io.matrix.operator;

/**
 * Desired state of a {@link TaskCellResource}: task description plus the
 * death-by-budget contract of DESIGN-12 (spawn/isolate/die).
 */
public class TaskCellSpec {

    private String task;
    private long cpuMs;
    private long memoryBytes;
    private long wallTimeMs;
    private int ttlSeconds;

    public String getTask() { return task; }
    public void setTask(String task) { this.task = task; }
    public long getCpuMs() { return cpuMs; }
    public void setCpuMs(long cpuMs) { this.cpuMs = cpuMs; }
    public long getMemoryBytes() { return memoryBytes; }
    public void setMemoryBytes(long memoryBytes) { this.memoryBytes = memoryBytes; }
    public long getWallTimeMs() { return wallTimeMs; }
    public void setWallTimeMs(long wallTimeMs) { this.wallTimeMs = wallTimeMs; }
    public int getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
}
