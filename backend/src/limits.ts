export const INSTALLATION_LIMIT = 5;
export const IP_LIMIT = 20;
// Submissions from everyone together per 24 hours. The per-IP limit buckets IPv6 by /64, and anyone
// with a routed /48 has 65,536 of those; this cap keeps the moderation queue bounded regardless.
export const GLOBAL_DAILY_LIMIT = 500;
const WINDOW_SECONDS = 86400;

// All submissions of the last 24 hours, withdrawn and deleted ones included.
export const RECENT_TOTAL_SQL =
  `((SELECT COUNT(*) FROM submissions WHERE submitted_at >= unixepoch() - ${WINDOW_SECONDS})
    + (SELECT COUNT(*) FROM deleted_submission_slots WHERE submitted_at >= unixepoch() - ${WINDOW_SECONDS}))`;

// Submissions sent within the last 24 hours by one installation or IP hash. Withdrawn and
// deleted submissions count as well: their slots stay in deleted_submission_slots until the
// window has passed. The expression takes the hash as two bindings.
export function recentSubmissionCount(column: "installation_hash" | "ip_hash"): string {
  return `((SELECT COUNT(*) FROM submissions WHERE ${column} = ? AND submitted_at >= unixepoch() - ${WINDOW_SECONDS})
    + (SELECT COUNT(*) FROM deleted_submission_slots WHERE ${column} = ? AND submitted_at >= unixepoch() - ${WINDOW_SECONDS}))`;
}

// Runs in the same batch as, and before, a statement that deletes the submissions matching
// `where`, so that withdrawing or deleting a submission never frees its slot early.
export function keepRateLimitSlots(db: D1Database, where: string, ...bindings: unknown[]): D1PreparedStatement {
  return db.prepare(
    `INSERT INTO deleted_submission_slots (installation_hash, ip_hash, submitted_at)
     SELECT installation_hash, ip_hash, submitted_at FROM submissions
     WHERE ${where} AND submitted_at >= unixepoch() - ${WINDOW_SECONDS}`
  ).bind(...bindings);
}

export function removeExpiredSlots(db: D1Database): D1PreparedStatement {
  return db.prepare(`DELETE FROM deleted_submission_slots WHERE submitted_at < unixepoch() - ${WINDOW_SECONDS}`);
}
