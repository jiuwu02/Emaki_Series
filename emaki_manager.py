#!/usr/bin/env python3
"""
Unified local utility for Emaki Series project maintenance.

Features:
- Sync local wiki content from an external folder into Project/wiki
- Publish the local wiki directory to the GitHub Wiki repository
- Create GitHub Releases and upload only module jars whose versions have not
  been published before
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import re
import shutil
import subprocess
import sys
import tempfile
import textwrap
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as et
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


PROJECT_ROOT = Path(__file__).resolve().parent
DEFAULT_WIKI_SOURCE = PROJECT_ROOT.parent / "Emaki Plugin Wiki"
LOCAL_WIKI_DIR = PROJECT_ROOT / "wiki"
API_VERSION = "2022-11-28"


@dataclass(frozen=True)
class ModuleSpec:
    key: str
    display_name: str
    module_dir: Path
    artifact_id: str

    @property
    def pom_path(self) -> Path:
        return self.module_dir / "pom.xml"

    @property
    def target_dir(self) -> Path:
        return self.module_dir / "target"


@dataclass(frozen=True)
class ModuleReleaseInfo:
    spec: ModuleSpec
    version: str
    jar_path: Path

    @property
    def asset_name(self) -> str:
        return self.jar_path.name


MODULES = [
    ModuleSpec(
        key="corelib",
        display_name="Emaki CoreLib",
        module_dir=PROJECT_ROOT / "Emaki_CoreLib",
        artifact_id="emaki-corelib",
    ),
    ModuleSpec(
        key="forge",
        display_name="Emaki Forge",
        module_dir=PROJECT_ROOT / "Emaki_Forge",
        artifact_id="emaki-forge",
    ),
]


def run_git(*args: str, cwd: Path | None = None, capture_output: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=str(cwd or PROJECT_ROOT),
        text=True,
        check=True,
        capture_output=capture_output,
    )


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write_text(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8", newline="\n")


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def resolve_github_repo(remote_url: str) -> tuple[str, str]:
    patterns = [
        r"^https://github\.com/(?P<owner>[^/]+)/(?P<repo>[^/.]+?)(?:\.git)?$",
        r"^git@github\.com:(?P<owner>[^/]+)/(?P<repo>[^/.]+?)(?:\.git)?$",
    ]
    for pattern in patterns:
        match = re.match(pattern, remote_url.strip())
        if match:
            return match.group("owner"), match.group("repo")
    raise ValueError(f"Unsupported GitHub remote URL: {remote_url}")


def get_origin_url() -> str:
    result = run_git("remote", "get-url", "origin", capture_output=True)
    return result.stdout.strip()


def get_current_branch() -> str:
    result = run_git("branch", "--show-current", capture_output=True)
    branch = result.stdout.strip()
    return branch or "main"


def convert_origin_to_wiki_remote(origin_url: str) -> str:
    if origin_url.endswith(".git"):
        return origin_url[:-4] + ".wiki.git"
    return origin_url + ".wiki.git"


def parse_pom_version(pom_path: Path) -> str:
    tree = et.parse(pom_path)
    root = tree.getroot()
    namespace_match = re.match(r"\{(.*)\}", root.tag)
    ns = {"m": namespace_match.group(1)} if namespace_match else {}
    version_node = root.find("m:version", ns) if ns else root.find("version")
    if version_node is None or not version_node.text:
        raise ValueError(f"Direct <version> not found in {pom_path}")
    return version_node.text.strip()


def locate_release_jar(spec: ModuleSpec, version: str) -> Path:
    expected = spec.target_dir / f"{spec.artifact_id}-{version}.jar"
    if expected.exists():
        return expected
    raise FileNotFoundError(
        f"Built jar not found for {spec.display_name}: expected {expected}"
    )


def collect_module_release_info() -> list[ModuleReleaseInfo]:
    info_list: list[ModuleReleaseInfo] = []
    for spec in MODULES:
        version = parse_pom_version(spec.pom_path)
        jar_path = locate_release_jar(spec, version)
        info_list.append(ModuleReleaseInfo(spec=spec, version=version, jar_path=jar_path))
    return info_list


def sync_directory(source: Path, target: Path, *, ignore_names: Iterable[str] = ()) -> None:
    ignore = set(ignore_names)
    ensure_dir(target)

    source_entries = {entry.name: entry for entry in source.iterdir() if entry.name not in ignore}
    target_entries = {entry.name: entry for entry in target.iterdir() if entry.name not in ignore}

    for stale_name in sorted(target_entries.keys() - source_entries.keys()):
        stale_path = target / stale_name
        if stale_path.is_dir():
            shutil.rmtree(stale_path)
        else:
            stale_path.unlink()

    for name, source_entry in source_entries.items():
        target_entry = target / name
        if source_entry.is_dir():
            if target_entry.exists() and not target_entry.is_dir():
                target_entry.unlink()
            sync_directory(source_entry, target_entry, ignore_names=ignore)
        else:
            ensure_dir(target_entry.parent)
            shutil.copy2(source_entry, target_entry)


def command_sync_wiki(args: argparse.Namespace) -> int:
    source = Path(args.source).resolve()
    target = Path(args.target).resolve()
    if not source.exists():
        if args.allow_missing_source:
            print(f"Wiki source not found, keeping existing local wiki: {source}")
            return 0
        raise FileNotFoundError(f"Wiki source not found: {source}")
    sync_directory(source, target)
    print(f"Wiki synced: {source} -> {target}")
    return 0


def clone_repo(remote_url: str, target_dir: Path) -> None:
    subprocess.run(
        ["git", "clone", remote_url, str(target_dir)],
        check=True,
        text=True,
    )


def command_publish_wiki(args: argparse.Namespace) -> int:
    local_wiki = Path(args.target).resolve()
    source = Path(args.source).resolve()

    if not args.no_sync and source.exists():
        sync_directory(source, local_wiki)
        print(f"Wiki synced before publish: {source} -> {local_wiki}")
    elif not local_wiki.exists():
        raise FileNotFoundError(f"Local wiki directory not found: {local_wiki}")

    origin_url = get_origin_url()
    wiki_remote = convert_origin_to_wiki_remote(origin_url)

    with tempfile.TemporaryDirectory(prefix="emaki-wiki-") as temp_dir:
        wiki_repo_dir = Path(temp_dir) / "wiki"
        clone_repo(wiki_remote, wiki_repo_dir)
        sync_directory(local_wiki, wiki_repo_dir, ignore_names={".git"})

        status = subprocess.run(
            ["git", "status", "--short"],
            cwd=str(wiki_repo_dir),
            text=True,
            check=True,
            capture_output=True,
        ).stdout.strip()
        if not status:
            print("No wiki changes to publish.")
            return 0

        subprocess.run(["git", "add", "-A"], cwd=str(wiki_repo_dir), check=True, text=True)
        subprocess.run(
            ["git", "commit", "-m", args.message],
            cwd=str(wiki_repo_dir),
            check=True,
            text=True,
        )
        subprocess.run(
            ["git", "push", "origin", "HEAD:master"],
            cwd=str(wiki_repo_dir),
            check=True,
            text=True,
        )

    print("Wiki published successfully.")
    return 0


def get_github_token(explicit: str | None) -> str | None:
    if explicit:
        return explicit
    return os.getenv("GH_TOKEN") or os.getenv("GITHUB_TOKEN")


def api_headers(token: str | None, extra: dict[str, str] | None = None) -> dict[str, str]:
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": API_VERSION,
        "User-Agent": "emaki-manager",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if extra:
        headers.update(extra)
    return headers


def github_request(
    method: str,
    url: str,
    *,
    token: str | None,
    json_body: dict | None = None,
    raw_body: bytes | None = None,
    content_type: str | None = None,
) -> tuple[int, object, dict[str, str]]:
    if json_body is not None and raw_body is not None:
        raise ValueError("Use either json_body or raw_body, not both.")

    data = None
    headers = api_headers(token)
    if json_body is not None:
        data = json.dumps(json_body).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    elif raw_body is not None:
        data = raw_body
        headers["Content-Type"] = content_type or "application/octet-stream"

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request) as response:
            payload = response.read()
            response_headers = dict(response.headers.items())
            if response_headers.get("Content-Type", "").startswith("application/json"):
                parsed = json.loads(payload.decode("utf-8"))
            else:
                parsed = payload
            return response.status, parsed, response_headers
    except urllib.error.HTTPError as exc:
        payload = exc.read()
        response_headers = dict(exc.headers.items())
        if response_headers.get("Content-Type", "").startswith("application/json"):
            parsed = json.loads(payload.decode("utf-8"))
        else:
            parsed = payload.decode("utf-8", errors="replace")
        return exc.code, parsed, response_headers


def list_all_releases(owner: str, repo: str, token: str | None) -> list[dict]:
    releases: list[dict] = []
    page = 1
    while True:
        url = f"https://api.github.com/repos/{owner}/{repo}/releases?per_page=100&page={page}"
        status, payload, _ = github_request("GET", url, token=token)
        if status != 200:
            if (
                status == 403
                and isinstance(payload, dict)
                and "rate limit" in str(payload.get("message", "")).lower()
                and not token
            ):
                raise RuntimeError(
                    "Anonymous GitHub API rate limit reached. Set GH_TOKEN or "
                    "GITHUB_TOKEN and retry."
                )
            raise RuntimeError(f"Failed to list releases: {payload}")
        if not payload:
            break
        releases.extend(payload)
        if len(payload) < 100:
            break
        page += 1
    return releases


def get_release_by_tag(owner: str, repo: str, tag: str, token: str | None) -> dict | None:
    url = f"https://api.github.com/repos/{owner}/{repo}/releases/tags/{urllib.parse.quote(tag, safe='')}"
    status, payload, _ = github_request("GET", url, token=token)
    if status == 200:
        return payload
    if status == 404:
        return None
    raise RuntimeError(f"Failed to query release by tag {tag}: {payload}")


def create_release(owner: str, repo: str, token: str, body: dict) -> dict:
    url = f"https://api.github.com/repos/{owner}/{repo}/releases"
    status, payload, _ = github_request("POST", url, token=token, json_body=body)
    if status not in {200, 201}:
        raise RuntimeError(f"Failed to create release: {payload}")
    return payload


def update_release(owner: str, repo: str, release_id: int, token: str, body: dict) -> dict:
    url = f"https://api.github.com/repos/{owner}/{repo}/releases/{release_id}"
    status, payload, _ = github_request("PATCH", url, token=token, json_body=body)
    if status != 200:
        raise RuntimeError(f"Failed to update release {release_id}: {payload}")
    return payload


def upload_release_asset(upload_url_template: str, token: str, file_path: Path) -> dict:
    upload_url = upload_url_template.split("{", 1)[0]
    content_type = mimetypes.guess_type(file_path.name)[0] or "application/java-archive"
    query = urllib.parse.urlencode({"name": file_path.name})
    url = f"{upload_url}?{query}"
    status, payload, _ = github_request(
        "POST",
        url,
        token=token,
        raw_body=file_path.read_bytes(),
        content_type=content_type,
    )
    if status not in {200, 201}:
        raise RuntimeError(f"Failed to upload asset {file_path.name}: {payload}")
    return payload


def build_published_asset_set(releases: list[dict]) -> set[str]:
    asset_names: set[str] = set()
    for release in releases:
        for asset in release.get("assets", []):
            name = asset.get("name")
            if name:
                asset_names.add(name)
    return asset_names


def build_release_tag(changed_modules: list[ModuleReleaseInfo]) -> str:
    parts = [f"{module.spec.key}-{module.version}" for module in changed_modules]
    return "release-" + "__".join(parts)


def build_release_name(changed_modules: list[ModuleReleaseInfo]) -> str:
    versions = [f"{module.spec.display_name} {module.version}" for module in changed_modules]
    return " / ".join(versions)


def build_release_body(all_modules: list[ModuleReleaseInfo], changed_modules: list[ModuleReleaseInfo]) -> str:
    changed_lines = "\n".join(
        f"- {module.spec.display_name}: `{module.version}`" for module in changed_modules
    )
    snapshot_lines = "\n".join(
        f"- {module.spec.display_name}: `{module.version}`" for module in all_modules
    )
    asset_lines = "\n".join(
        f"- `{module.asset_name}`" for module in changed_modules
    )
    return textwrap.dedent(
        f"""\
        ## Published Modules

        {changed_lines}

        ## Assets

        {asset_lines}

        ## Current Version Snapshot

        {snapshot_lines}
        """
    ).strip()


def command_publish_release(args: argparse.Namespace) -> int:
    token = get_github_token(args.token)
    if not token and not args.dry_run:
        raise RuntimeError("GitHub token is required. Set GH_TOKEN or GITHUB_TOKEN, or pass --token.")

    module_info = collect_module_release_info()
    owner, repo = resolve_github_repo(get_origin_url())
    releases = list_all_releases(owner, repo, token)
    published_assets = build_published_asset_set(releases)
    changed_modules = [module for module in module_info if module.asset_name not in published_assets]

    if not changed_modules:
        print("No new module versions detected. No release will be created.")
        return 0

    tag_name = args.tag or build_release_tag(changed_modules)
    release_name = args.name or build_release_name(changed_modules)
    release_body = build_release_body(module_info, changed_modules)
    target_commitish = args.target_commitish or get_current_branch()

    print("Release plan:")
    print(f"- Repository: {owner}/{repo}")
    print(f"- Tag: {tag_name}")
    print(f"- Name: {release_name}")
    print(f"- Target: {target_commitish}")
    print("- Included assets:")
    for module in changed_modules:
        print(f"  - {module.asset_name}")

    if args.dry_run:
        return 0

    existing_release = get_release_by_tag(owner, repo, tag_name, token)
    payload = {
        "tag_name": tag_name,
        "target_commitish": target_commitish,
        "name": release_name,
        "body": release_body,
        "draft": False,
        "prerelease": args.prerelease,
        "generate_release_notes": False,
    }

    if existing_release is None:
        release = create_release(owner, repo, token, payload)
        print(f"Created release: {release.get('html_url')}")
    else:
        release = update_release(owner, repo, existing_release["id"], token, payload)
        print(f"Updated existing release: {release.get('html_url')}")

    existing_asset_names = {asset["name"] for asset in release.get("assets", [])}
    for module in changed_modules:
        if module.asset_name in existing_asset_names:
            print(f"Asset already exists on release, skipping: {module.asset_name}")
            continue
        upload_release_asset(release["upload_url"], token, module.jar_path)
        print(f"Uploaded asset: {module.asset_name}")

    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Emaki Series local maintenance utility")
    subparsers = parser.add_subparsers(dest="command", required=True)

    sync_wiki = subparsers.add_parser("sync-wiki", help="Sync external wiki folder into Project/wiki")
    sync_wiki.add_argument("--source", default=str(DEFAULT_WIKI_SOURCE), help="External wiki source directory")
    sync_wiki.add_argument("--target", default=str(LOCAL_WIKI_DIR), help="Local wiki directory")
    sync_wiki.add_argument(
        "--allow-missing-source",
        action="store_true",
        help="Do not fail if the external wiki source folder does not exist",
    )
    sync_wiki.set_defaults(func=command_sync_wiki)

    publish_wiki = subparsers.add_parser("publish-wiki", help="Publish Project/wiki to GitHub Wiki")
    publish_wiki.add_argument("--source", default=str(DEFAULT_WIKI_SOURCE), help="External wiki source directory")
    publish_wiki.add_argument("--target", default=str(LOCAL_WIKI_DIR), help="Local wiki directory")
    publish_wiki.add_argument("--no-sync", action="store_true", help="Publish the local wiki without syncing from external source first")
    publish_wiki.add_argument("--message", default="docs: 更新 Wiki", help="Git commit message for the wiki repository")
    publish_wiki.set_defaults(func=command_publish_wiki)

    publish_release = subparsers.add_parser("publish-release", help="Create or update a GitHub Release and upload new module jars")
    publish_release.add_argument("--token", help="GitHub token. Defaults to GH_TOKEN or GITHUB_TOKEN")
    publish_release.add_argument("--tag", help="Override the generated release tag")
    publish_release.add_argument("--name", help="Override the generated release title")
    publish_release.add_argument("--target-commitish", help="Branch or commit used for the GitHub Release tag")
    publish_release.add_argument("--prerelease", action="store_true", help="Mark the GitHub Release as a pre-release")
    publish_release.add_argument("--dry-run", action="store_true", help="Print the planned release without creating it")
    publish_release.set_defaults(func=command_publish_release)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return int(args.func(args) or 0)
    except Exception as exc:  # pragma: no cover - CLI entrypoint
        print(f"Error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
