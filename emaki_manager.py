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
from getpass import getpass
from pathlib import Path
from typing import Iterable


PROJECT_ROOT = Path(__file__).resolve().parent
DEFAULT_WIKI_SOURCE = PROJECT_ROOT.parent / "Emaki Plugin Wiki"
LOCAL_WIKI_DIR = PROJECT_ROOT / "wiki"
DEFAULT_RELEASE_NOTES_FILE = PROJECT_ROOT / "release-notes.md"
API_VERSION = "2022-11-28"
DEFAULT_RELEASE_NOTES = textwrap.dedent(
    """\
    ### 更新摘要

    - 发布新的构建产物

    ### 变更说明

    - 请在此补充本次版本的详细更新内容

    ### 兼容性说明

    - 如无额外说明，则以当前仓库代码与对应模块版本为准
    """
).strip()


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


def print_section(title: str) -> None:
    print(f"\n[{title}]")


def read_input(prompt: str = "") -> str:
    try:
        return input(prompt)
    except EOFError as exc:
        raise KeyboardInterrupt from exc


def prompt_text(prompt: str, default: str | None = None, *, allow_empty: bool = True) -> str:
    while True:
        suffix = f" [{default}]" if default not in {None, ""} else ""
        value = read_input(f"{prompt}{suffix}: ").strip()
        if value:
            return value
        if default is not None:
            return default
        if allow_empty:
            return ""
        print("输入不能为空，请重新输入。")


def prompt_yes_no(prompt: str, default: bool = True) -> bool:
    hint = "Y/n" if default else "y/N"
    while True:
        value = read_input(f"{prompt} [{hint}]: ").strip().lower()
        if not value:
            return default
        if value in {"y", "yes", "1"}:
            return True
        if value in {"n", "no", "0"}:
            return False
        print("请输入 y 或 n。")


def prompt_menu_choice(title: str, options: list[tuple[str, str]], *, default: str | None = None) -> str:
    print_section(title)
    for key, label in options:
        print(f"{key}. {label}")
    valid = {key for key, _ in options}
    while True:
        suffix = f" [{default}]" if default else ""
        value = read_input(f"请输入选项编号{suffix}: ").strip()
        if not value and default:
            return default
        if value in valid:
            return value
        print("无效选项，请重新输入。")


def prompt_multiline(prompt: str, end_marker: str = "END") -> str:
    print(prompt)
    print(f"输入完成后，单独输入一行 {end_marker} 结束。")
    lines: list[str] = []
    while True:
        line = read_input()
        if line.strip() == end_marker:
            break
        lines.append(line)
    return "\n".join(lines).strip()


def prompt_continue() -> None:
    read_input("\n按回车键继续...")


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


def build_default_release_notes(changed_modules: list[ModuleReleaseInfo]) -> str:
    sections = []
    for module in changed_modules:
        sections.append(
            textwrap.dedent(
                f"""\
                ### {module.spec.display_name}

                - 发布版本：`{module.version}`
                - 发布附件：`{module.asset_name}`
                """
            ).strip()
        )
    return "\n\n".join(sections) if sections else DEFAULT_RELEASE_NOTES


def load_release_notes(args: argparse.Namespace, changed_modules: list[ModuleReleaseInfo]) -> str:
    if getattr(args, "notes", None) and getattr(args, "notes_file", None):
        raise ValueError("--notes and --notes-file cannot be used together.")

    if getattr(args, "notes_file", None):
        notes_path = Path(args.notes_file).resolve()
        if not notes_path.exists():
            raise FileNotFoundError(f"Release notes file not found: {notes_path}")
        return read_text(notes_path).strip()

    if getattr(args, "notes", None):
        return args.notes.strip()

    return build_default_release_notes(changed_modules)


def build_release_body(
    all_modules: list[ModuleReleaseInfo],
    changed_modules: list[ModuleReleaseInfo],
    release_notes: str,
) -> str:
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
        ## Release Notes

        {release_notes}

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
    release_notes = load_release_notes(args, changed_modules)
    release_body = build_release_body(module_info, changed_modules, release_notes)
    target_commitish = args.target_commitish or get_current_branch()

    print("Release plan:")
    print(f"- Repository: {owner}/{repo}")
    print(f"- Tag: {tag_name}")
    print(f"- Name: {release_name}")
    print(f"- Target: {target_commitish}")
    print("- Included assets:")
    for module in changed_modules:
        print(f"  - {module.asset_name}")
    print("- Release notes preview:")
    print(textwrap.indent(release_notes, "  "))

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


def build_guide_text(topic: str) -> str:
    wiki_source = str(DEFAULT_WIKI_SOURCE)
    local_wiki = str(LOCAL_WIKI_DIR)
    if topic == "wiki":
        return textwrap.dedent(
            f"""\
            Wiki 发布操作步骤

            1. 直接运行脚本
               python .\\emaki_manager.py

            2. 选择主菜单中的“发布 Wiki”

            3. 准备 Wiki 内容
               - 默认外部 Wiki 目录：{wiki_source}
               - 本地仓库 Wiki 目录：{local_wiki}
               - 如果你只想直接发布当前本地 wiki/，可以跳过同步步骤

            4. 预备检查
               - 确认 GitHub 仓库已开启 Wiki
               - 确认你已经至少创建过一次 GitHub Wiki 首页

            5. 按界面提示选择：
               - 是否先同步外部 Wiki 目录
               - 是否直接发布当前本地 wiki/
               - 本次 Wiki 提交信息

            6. 确认后脚本会自动完成推送
            """
        ).strip()

    if topic == "release":
        return textwrap.dedent(
            """\
            Release 发布操作步骤

            1. 先在 IDEA 或命令行完成构建
               - Emaki_CoreLib/target/emaki-corelib-<version>.jar
               - Emaki_Forge/target/emaki-forge-<version>.jar

            2. 直接运行脚本
               python .\\emaki_manager.py

            3. 选择主菜单中的“发布 Release”

            4. 脚本会按步骤引导你：
               - 检查当前模块版本与构建产物
               - 输入或读取 GitHub Token
               - 选择发布日志来源
               - 先预览本次 Release 计划
               - 二次确认后正式发布

            5. 自动识别规则
               - 如果 CoreLib 和 Forge 都是新版本：同一个 Release 同时上传两个 jar
               - 如果只有一个模块是新版本：只上传这个模块的 jar
               - 如果两个模块当前版本都已发布：不创建新 Release
            """
        ).strip()

    return textwrap.dedent(
        """\
        Emaki Manager 操作总览

        默认使用方式：
        - 直接运行 python .\\emaki_manager.py
        - 根据菜单编号输入选项
        - 按步骤完成 Wiki 或 Release 发布

        主菜单功能：
        - 查看帮助总览
        - 查看 Wiki 发布说明
        - 发布 Wiki
        - 查看 Release 发布说明
        - 发布 Release

        兼容说明：
        - 如你仍然习惯命令行参数，旧的子命令方式仍然可用
        """
    ).strip()


def command_guide(args: argparse.Namespace) -> int:
    print(build_guide_text(args.topic))
    return 0


def build_release_namespace(
    *,
    token: str | None,
    tag: str | None = None,
    name: str | None = None,
    target_commitish: str | None = None,
    prerelease: bool = False,
    notes: str | None = None,
    notes_file: str | None = None,
    dry_run: bool = False,
) -> argparse.Namespace:
    return argparse.Namespace(
        token=token,
        tag=tag,
        name=name,
        target_commitish=target_commitish,
        prerelease=prerelease,
        notes=notes,
        notes_file=notes_file,
        dry_run=dry_run,
    )


def interactive_publish_wiki() -> int:
    print(build_guide_text("wiki"))
    print_section("Wiki 发布向导")

    source_default = str(DEFAULT_WIKI_SOURCE)
    target_default = str(LOCAL_WIKI_DIR)
    source = prompt_text("请输入外部 Wiki 目录", source_default)
    target = prompt_text("请输入本地 wiki 目录", target_default)
    do_sync = prompt_yes_no("发布前是否先同步外部 Wiki 到本地 wiki", default=True)
    message = prompt_text("请输入 Wiki 提交信息", "docs: 更新 Wiki")

    print_section("本次操作确认")
    print(f"外部 Wiki 目录: {Path(source).resolve()}")
    print(f"本地 wiki 目录: {Path(target).resolve()}")
    print(f"发布前同步: {'是' if do_sync else '否'}")
    print(f"提交信息: {message}")

    if not prompt_yes_no("确认开始发布 Wiki 吗", default=False):
        print("已取消 Wiki 发布。")
        return 0

    args = argparse.Namespace(
        source=source,
        target=target,
        no_sync=not do_sync,
        message=message,
    )
    return command_publish_wiki(args)


def interactive_choose_release_notes() -> tuple[str | None, str | None]:
    default_notes_exists = DEFAULT_RELEASE_NOTES_FILE.exists()
    options: list[tuple[str, str]] = []
    if default_notes_exists:
        options.append(("1", f"使用默认日志文件 {DEFAULT_RELEASE_NOTES_FILE.name}"))
        next_key = 2
    else:
        next_key = 1
    options.extend(
        [
            (str(next_key), "使用其他 Markdown 日志文件"),
            (str(next_key + 1), "手动输入发布日志"),
            (str(next_key + 2), "使用脚本自动生成的基础日志"),
        ]
    )

    choice = prompt_menu_choice("选择发布日志来源", options, default="1")
    if default_notes_exists and choice == "1":
        return None, str(DEFAULT_RELEASE_NOTES_FILE)
    if choice == str(next_key):
        notes_file = prompt_text("请输入 Markdown 日志文件路径", allow_empty=False)
        return None, str(Path(notes_file).resolve())
    if choice == str(next_key + 1):
        notes = prompt_multiline("请输入本次发布日志内容。")
        return notes, None
    return None, None


def interactive_publish_release() -> int:
    print(build_guide_text("release"))
    print_section("Release 发布向导")

    module_info = collect_module_release_info()
    print("已检测到以下模块与构建产物：")
    for module in module_info:
        print(f"- {module.spec.display_name}: {module.version}")
        print(f"  jar: {module.jar_path}")

    token = get_github_token(None)
    if token:
        print("已检测到环境变量中的 GitHub Token。")
    else:
        print("当前未检测到 GH_TOKEN 或 GITHUB_TOKEN。")
        if prompt_yes_no("是否现在手动输入 GitHub Token", default=True):
            token_input = getpass("GitHub Token: ").strip()
            token = token_input or None

    current_branch = get_current_branch()
    target_commitish = prompt_text("Release 目标分支", current_branch)

    custom_tag = None
    if prompt_yes_no("是否自定义 Release Tag", default=False):
        custom_tag = prompt_text("请输入自定义 Tag", allow_empty=False)

    custom_name = None
    if prompt_yes_no("是否自定义 Release 标题", default=False):
        custom_name = prompt_text("请输入自定义标题", allow_empty=False)

    prerelease = prompt_yes_no("是否标记为预发布版本", default=False)
    notes, notes_file = interactive_choose_release_notes()

    preview_args = build_release_namespace(
        token=token,
        tag=custom_tag,
        name=custom_name,
        target_commitish=target_commitish,
        prerelease=prerelease,
        notes=notes,
        notes_file=notes_file,
        dry_run=True,
    )

    print_section("Release 预览")
    command_publish_release(preview_args)

    if not prompt_yes_no("确认按以上计划正式发布 Release 吗", default=False):
        print("已取消 Release 发布。")
        return 0

    publish_args = build_release_namespace(
        token=token,
        tag=custom_tag,
        name=custom_name,
        target_commitish=target_commitish,
        prerelease=prerelease,
        notes=notes,
        notes_file=notes_file,
        dry_run=False,
    )
    return command_publish_release(publish_args)


def interactive_main() -> int:
    while True:
        choice = prompt_menu_choice(
            "Emaki Manager 主菜单",
            [
                ("1", "查看操作总览"),
                ("2", "查看 Wiki 发布说明"),
                ("3", "发布 Wiki"),
                ("4", "查看 Release 发布说明"),
                ("5", "发布 Release"),
                ("0", "退出"),
            ],
            default="1",
        )

        try:
            if choice == "1":
                print(build_guide_text("all"))
            elif choice == "2":
                print(build_guide_text("wiki"))
            elif choice == "3":
                interactive_publish_wiki()
            elif choice == "4":
                print(build_guide_text("release"))
            elif choice == "5":
                interactive_publish_release()
            else:
                print("已退出 Emaki Manager。")
                return 0
        except Exception as exc:
            print(f"\n操作失败：{exc}")

        prompt_continue()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Emaki Series 本地发布工具（默认支持交互式输入模式）",
        formatter_class=argparse.RawTextHelpFormatter,
        epilog=build_guide_text("all"),
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    guide = subparsers.add_parser(
        "guide",
        help="显示分步骤操作说明",
        formatter_class=argparse.RawTextHelpFormatter,
        description="显示 Wiki 发布或 Release 发布的完整操作步骤。",
    )
    guide.add_argument(
        "topic",
        nargs="?",
        choices=["all", "wiki", "release"],
        default="all",
        help="查看全部说明，或只看 wiki / release",
    )
    guide.set_defaults(func=command_guide)

    sync_wiki = subparsers.add_parser(
        "sync-wiki",
        help="同步外部 Wiki 到本地 wiki/",
        formatter_class=argparse.RawTextHelpFormatter,
        description=textwrap.dedent(
            f"""\
            同步外部 Wiki 内容到本地仓库的 wiki/ 目录。

            默认外部目录：
            {DEFAULT_WIKI_SOURCE}

            默认本地目录：
            {LOCAL_WIKI_DIR}
            """
        ),
    )
    sync_wiki.add_argument("--source", default=str(DEFAULT_WIKI_SOURCE), help="External wiki source directory")
    sync_wiki.add_argument("--target", default=str(LOCAL_WIKI_DIR), help="Local wiki directory")
    sync_wiki.add_argument(
        "--allow-missing-source",
        action="store_true",
        help="Do not fail if the external wiki source folder does not exist",
    )
    sync_wiki.set_defaults(func=command_sync_wiki)

    publish_wiki = subparsers.add_parser(
        "publish-wiki",
        help="发布本地 wiki/ 到 GitHub Wiki",
        formatter_class=argparse.RawTextHelpFormatter,
        description=textwrap.dedent(
            """\
            发布 Wiki 的完整流程：
            1. 如有外部 Wiki 目录，先同步到本地 wiki/
            2. 克隆 GitHub Wiki 仓库
            3. 将本地 wiki/ 内容覆盖同步到 GitHub Wiki
            4. 自动提交并推送

            如果你已经手动维护好了本地 wiki/，可以使用 --no-sync 跳过同步步骤。
            """
        ),
    )
    publish_wiki.add_argument("--source", default=str(DEFAULT_WIKI_SOURCE), help="External wiki source directory")
    publish_wiki.add_argument("--target", default=str(LOCAL_WIKI_DIR), help="Local wiki directory")
    publish_wiki.add_argument("--no-sync", action="store_true", help="Publish the local wiki without syncing from external source first")
    publish_wiki.add_argument("--message", default="docs: 更新 Wiki", help="Git commit message for the wiki repository")
    publish_wiki.set_defaults(func=command_publish_wiki)

    publish_release = subparsers.add_parser(
        "publish-release",
        help="自动识别新版本并发布 GitHub Release",
        formatter_class=argparse.RawTextHelpFormatter,
        description=textwrap.dedent(
            """\
            自动识别模块版本是否已发布，并按需要创建或更新 GitHub Release。

            识别规则：
            - 脚本读取 Emaki_CoreLib 与 Emaki_Forge 的 pom.xml 版本号
            - 脚本检查 GitHub Releases 中已存在的 jar 资产
            - 未发布过的模块会被纳入本次 Release
            - 多个未发布模块会被放进同一个 Release 中

            发布前建议先执行：
            python .\\emaki_manager.py publish-release --dry-run
            """
        ),
    )
    publish_release.add_argument("--token", help="GitHub token. Defaults to GH_TOKEN or GITHUB_TOKEN")
    publish_release.add_argument("--tag", help="Override the generated release tag")
    publish_release.add_argument("--name", help="Override the generated release title")
    publish_release.add_argument("--target-commitish", help="Branch or commit used for the GitHub Release tag")
    publish_release.add_argument("--prerelease", action="store_true", help="Mark the GitHub Release as a pre-release")
    publish_release.add_argument("--notes", help="Inline release notes text")
    publish_release.add_argument("--notes-file", help="Path to a Markdown file used as release notes")
    publish_release.add_argument("--dry-run", action="store_true", help="Print the planned release without creating it")
    publish_release.set_defaults(func=command_publish_release)

    return parser


def main(argv: list[str] | None = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    if not argv:
        try:
            return interactive_main()
        except KeyboardInterrupt:
            print("\n已取消操作。")
            return 1

    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return int(args.func(args) or 0)
    except Exception as exc:  # pragma: no cover - CLI entrypoint
        print(f"Error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
