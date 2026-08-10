package dev.foggy.platform;

/** A version-neutral handle for either a Bukkit or Folia scheduled task. */
public interface TaskHandle {
    /** Cancels future executions. This method is idempotent. */
    void cancel();
}
