"""Bump the patch segment of every Emaki module version, and the parent pom
properties that pin them.

Two places must move together: each module pom declares its own version, and the
parent pom pins the same numbers as `emaki.*.version` properties used by the
inter-module dependencies. Leaving either behind makes the reactor resolve a
version that no longer exists.
"""
import io
import os
import re

BASE = r"F:\Bliss Tapestry\Bliss Tapestry\Project"

MODULES = [
    "EmakiAttribute", "EmakiAttributeApi", "EmakiCodex", "EmakiCodexApi",
    "EmakiCooking", "EmakiCookingApi", "EmakiCoreLib", "EmakiCoreLibApi",
    "EmakiForge", "EmakiForgeApi", "EmakiGem", "EmakiGemApi",
    "EmakiItem", "EmakiItemApi", "EmakiLevel", "EmakiLevelApi",
    "EmakiSkills", "EmakiSkillsApi", "EmakiStorage", "EmakiStorageApi",
    "EmakiStrengthen", "EmakiStrengthenApi",
]


def bump(version):
    parts = version.split(".")
    parts[-1] = str(int(parts[-1]) + 1)
    return ".".join(parts)


def read(path):
    with io.open(path, "r", encoding="utf-8", newline="") as fh:
        return fh.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)


old_to_new = {}

# Pass 1: each module's own <version>, the first one after its <artifactId>.
for module in MODULES:
    path = os.path.join(BASE, module, "pom.xml")
    text = read(path)
    # Skip the <parent> block so the parent's 4.0.0 is never touched.
    end_parent = text.find("</parent>")
    head, tail = text[:end_parent], text[end_parent:]
    m = re.search(r"(<artifactId>[^<]+</artifactId>\s*<version>)([0-9.]+)(</version>)", tail)
    if not m:
        print("SKIP %s: no own <version> found" % module)
        continue
    current = m.group(2)
    new = bump(current)
    old_to_new[module] = (current, new)
    tail = tail[:m.start()] + m.group(1) + new + m.group(3) + tail[m.end():]
    write(path, head + tail)
    print("%-22s %s -> %s" % (module, current, new))

# Pass 2: parent pom properties.
parent_path = os.path.join(BASE, "pom.xml")
parent = read(parent_path)


def prop_repl(match):
    name, value = match.group(1), match.group(2)
    return "<%s>%s</%s>" % (name, bump(value), name)


parent_new, count = re.subn(
    r"<(emaki\.[a-z.]*version)>([0-9.]+)</\1>", prop_repl, parent)
write(parent_path, parent_new)
print("\nparent pom: bumped %d emaki.*.version properties" % count)
