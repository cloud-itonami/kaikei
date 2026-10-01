#!/usr/bin/env python3
"""kaikei repo evidence. read-only. MEASURE<TAB>key<TAB>value lines + ledger append.
REFUSED + exit 2 if the repo checkout is unreadable (never a silent green)."""
import json, os, subprocess, sys, datetime

ROOT = os.path.expanduser("~/github/cloud-itonami")
REPO = os.path.join(ROOT, "kaikei")
HOME = os.path.expanduser("~/.hermes/profiles/kaikei")
LEDGER = os.path.join(HOME, "workspace", "kaikei-ledger.jsonl")

def refuse(reason):
    print("REFUSED\t%s" % reason)
    sys.exit(2)

if not os.path.isdir(os.path.join(REPO, ".git")):
    refuse("repo checkout missing or not a git dir: %s" % REPO)

def git(*args):
    p = subprocess.run(["git", "-C", REPO] + list(args),
                       capture_output=True, text=True)
    if p.returncode != 0:
        refuse("git %s failed: %s" % (" ".join(args), p.stderr.strip()[:200]))
    return p.stdout.strip()

rows = {}
rows["as_of"] = datetime.datetime.now().astimezone().isoformat(timespec="seconds")
rows["head"] = git("rev-parse", "--short", "HEAD")
rows["last_commit_date"] = git("log", "-1", "--format=%ad", "--date=short")
rows["dirty_files"] = len([l for l in git("status", "--porcelain").splitlines() if l.strip()])

def count_ext(base, exts):
    n = 0
    for dp, dns, fns in os.walk(base):
        dns[:] = [d for d in dns if d not in ("node_modules", ".git", "public", ".shadow-cljs", ".nbb", ".cpcr")]
        n += sum(1 for f in fns if f.endswith(exts))
    return n

src = count_ext(REPO, (".cljc", ".cljs", ".cljk", ".clj", ".kotoba"))
rows["source_files"] = src if src > 0 else "UNMEASURED zero-source"

readme = os.path.join(REPO, "README.md")
rows["readme_bytes"] = os.path.getsize(readme) if os.path.isdir(REPO) else "UNMEASURED"

# capability declared vs implemented probe:
# README names APP_CAPABILITIES journal-entry/trial-balance/profit-loss/balance-sheet.
# search source tree for any handler/implementation mention beyond the manifest itself.
impl = 0
try:
    p = subprocess.run(["grep", "-rliE", "journal-entry|trial-balance|profit-loss|balance-sheet", REPO],
                       capture_output=True, text=True)
    files = [f for f in p.stdout.splitlines()
             if not f.endswith((".md", ".edn", ".json")) and "/.git/" not in f]
    impl = len(files)
except OSError:
    impl = -1
rows["capability_impl_files"] = impl if impl >= 0 else "UNMEASURED grep-failed"

mig = os.path.join(REPO, "migration.edn")
rows["migration_edn"] = "present" if os.path.isfile(mig) else "absent"

for k, v in rows.items():
    print("MEASURE\t%s\t%s" % (k, v))

os.makedirs(os.path.dirname(LEDGER), exist_ok=True)
with open(LEDGER, "a") as f:
    f.write(json.dumps(rows, ensure_ascii=False) + "\n")
print("LEDGER\tappended\t%s" % LEDGER)
