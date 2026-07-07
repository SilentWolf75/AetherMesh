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
        # This script runs with cwd = firmware/, and git pathspecs are relative
        # to the cwd — a plain ':!app' would exclude firmware/app (nonexistent)
        # instead of the repo-root app/, so uncommitted app/gradle edits made
        # every build report dirty. ':(top,...)' anchors to the repo root.
        changed = subprocess.check_output(
            ["git", "diff", "--name-only", "--",
             ":(top,exclude)app", ":(top,exclude)docs"],
            stderr=subprocess.DEVNULL,
        ).decode().strip()
        if changed:
            h += "*"  # uncommitted firmware/proto changes present
    except Exception:
        pass
    return h

env.Append(CPPDEFINES=[("FW_GIT_HASH", env.StringifyMacro(git_hash()))])
