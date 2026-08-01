"""List every registered stage id, grouped by kind and owner.

Reads ids from source rather than from a running server: builtin action stages
pass their id to super(...), sources and gates return it from id(), and the
business modules either return a literal or map an enum constant to one.
"""
import io
import os
import re

BASE = r"F:\Bliss Tapestry\Bliss Tapestry\Project"

# `super("id", ...)` in a BaseStage subclass, or `return "id";` from id().
SUPER_ID = re.compile(r'super\(\s*"([a-z0-9_]+)"')
RETURN_ID = re.compile(r'return\s+"([a-z0-9_]+)"\s*;')
ENUM_ID = re.compile(r'^\s*[A-Z][A-Z0-9_]*\(\s*"([a-z0-9_]+)"', re.M)
MAP_ID = re.compile(r'ids\.put\([^,]+,\s*"([a-z0-9_]+)"\)')


def read(path):
    return io.open(path, encoding="utf-8", errors="replace").read()


def ids_in_file(path):
    """Collects candidate stage ids declared in one file."""
    text = read(path)
    found = []
    found += SUPER_ID.findall(text)
    found += ENUM_ID.findall(text)
    found += MAP_ID.findall(text)
    # id() overrides: take the literal inside the method body only.
    for m in re.finditer(r'String\s+id\(\)\s*\{(.*?)\}', text, re.S):
        found += RETURN_ID.findall(m.group(1))
    return found


def collect(root, label):
    out = {}
    for dirpath, dirnames, filenames in os.walk(root):
        if "target" in dirpath:
            continue
        for fn in filenames:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(dirpath, fn)
            for stage_id in ids_in_file(path):
                out.setdefault(stage_id, fn)
    return out


builtin = os.path.join(BASE, "EmakiCoreLib", "src", "main", "java", "emaki",
                       "jiuwu", "craft", "corelib", "action", "builtin")

print("=== CoreLib builtin ===")
for kind in ("source", "gate", "stage"):
    found = collect(os.path.join(builtin, kind), kind)
    print("\n[%s] %d ids" % (kind, len(found)))
    for stage_id in sorted(found):
        print("  %s" % stage_id)

MODULES = ["EmakiAttribute", "EmakiCodex", "EmakiCooking", "EmakiForge", "EmakiGem",
           "EmakiItem", "EmakiLevel", "EmakiSkills", "EmakiStorage", "EmakiStrengthen"]

print("\n=== business modules ===")
for module in MODULES:
    root = os.path.join(BASE, module, "src", "main", "java")
    found = {}
    for dirpath, dirnames, filenames in os.walk(root):
        if "target" in dirpath or os.sep + "action" not in dirpath:
            continue
        for fn in filenames:
            if fn.endswith(".java"):
                for stage_id in ids_in_file(os.path.join(dirpath, fn)):
                    found.setdefault(stage_id, fn)
    print("\n[%s] %d ids" % (module, len(found)))
    for stage_id in sorted(found):
        print("  %s" % stage_id)
