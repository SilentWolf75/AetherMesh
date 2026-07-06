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
        dirty = subprocess.call(
            ["git", "diff", "--quiet", "--", ":!app", ":!docs"],
            stderr=subprocess.DEVNULL,
        )
        if dirty != 0:
            h += "*"  # uncommitted firmware/proto changes present
    except Exception:
        pass
    return h

env.Append(CPPDEFINES=[("FW_GIT_HASH", env.StringifyMacro(git_hash()))])
