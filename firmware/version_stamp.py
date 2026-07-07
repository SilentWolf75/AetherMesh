Import("env")
import subprocess

# Embed the short git commit hash (and a -dirty marker for uncommitted changes)
# into the build as FW_GIT_HASH, so every node reports exactly which build it runs.
def git_hash():
    try:
        h = subprocess.check_output(
            ["git", "rev-parse", "--short=7", "HEAD"],
            stderr=subprocess.DEVNULL,
        ).decode().strip()
    except Exception:
        return "nogit"
    try:
        # Compare actual content, not `git diff --quiet` exit codes: --quiet
        # short-circuits on stat-cache/line-ending churn and can report dirty
        # for files whose content is unchanged (false '*' on every build).
        changed = subprocess.check_output(
            ["git", "diff", "--name-only", "--", ":!app", ":!docs"],
            stderr=subprocess.DEVNULL,
        ).decode().strip()
        if changed:
            h += "*"  # uncommitted firmware/proto changes present
    except Exception:
        pass
    return h

env.Append(CPPDEFINES=[("FW_GIT_HASH", env.StringifyMacro(git_hash()))])
