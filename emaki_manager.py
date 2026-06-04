#!/usr/bin/env python3
"""
Emaki Series Project Manager

核心功能: 
- 构建产物复制(带前缀重命名)
- VitePress 文档构建与部署到 GitHub Pages
- Release 发布到 GitHub
- 版本发布到 Modrinth
- config/lang 默认资源版本号同步
- Git 开发分支工作流(同步到 dev / 按策略合并到 main)

支持的项目: 
- EmakiCoreLib (核心库)
- EmakiForge (锻造模块)
- EmakiAttribute (属性模块)
- EmakiStrengthen (强化模块)
- EmakiGem (宝石模块)
- EmakiCooking (烹饪模块)
- EmakiSkills (技能模块)
- EmakiItem (物品模块)
- EmakiLevel (等级模块)
"""

from __future__ import annotations

import argparse
import getpass
import json
import logging
import os
import mimetypes
import re
import shutil
import subprocess
import sys
import textwrap
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Optional

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("emaki-manager")

PROJECT_ROOT = Path(__file__).resolve().parent
DOCS_DIR = PROJECT_ROOT / "docs"
DEFAULT_RELEASE_NOTES_FILE = PROJECT_ROOT / "release-notes.md"
PLUGINS_UPDATE_DIR = PROJECT_ROOT.parent / "plugins" / "update"
RELEASE_VERSION_DIR = PROJECT_ROOT / "release version"

GITHUB_OWNER = "jiuwu02"
GITHUB_REPO = "Emaki_Series"
DEFAULT_REMOTE_NAME = "origin"
DEFAULT_DEV_BRANCH = "dev"
DEFAULT_MAIN_BRANCH = "main"
DEFAULT_TARGET_COMMITISH = DEFAULT_MAIN_BRANCH
API_VERSION = "2022-11-28"

GITHUB_TOKEN_ENV_KEYS: tuple[str, ...] = ("EMAKI_GITHUB_TOKEN", "GITHUB_TOKEN")

# Modrinth 发布配置
MODRINTH_API_BASE = "https://api.modrinth.com/v2"
MODRINTH_TOKEN_ENV_KEYS: tuple[str, ...] = ("EMAKI_MODRINTH_TOKEN", "MODRINTH_TOKEN")
MODRINTH_DEFAULT_LOADERS: list[str] = ["spigot", "paper", "purpur", "folia"]
MODRINTH_DEFAULT_GAME_VERSIONS: list[str] = ["1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11"]
LOCAL_TOKEN_ENV_FILES: tuple[Path, ...] = (PROJECT_ROOT / ".env", PROJECT_ROOT / ".env.local")
_LOCAL_ENV_LOADED = False


@dataclass(frozen=True)
class ModuleSpec:
    """项目模块规格定义"""
    key: str
    display_name: str
    module_dir: Path
    artifact_id: str
    jar_prefix: str
    publish_to_github: bool = True
    include_in_main_merge: bool = True
    publish_to_modrinth: bool = False
    modrinth_project_id: str = ""
    modrinth_slug: str = ""

    @property
    def pom_path(self) -> Path:
        return self.module_dir / "pom.xml"

    @property
    def target_dir(self) -> Path:
        return self.module_dir / "target"

    @property
    def relative_path(self) -> str:
        return self.module_dir.relative_to(PROJECT_ROOT).as_posix()

    @property
    def changelog_path(self) -> Path:
        return self.module_dir / "CHANGELOG.md"

    @property
    def plugin_description_path(self) -> Path:
        return self.module_dir / "Plugin Description.md"

    @property
    def main_merge_preserved_paths(self) -> tuple[str, ...]:
        """排除模块时仍允许带到 main 的模块级发布资料文件。"""
        paths: list[str] = []
        if self.module_dir.exists():
            for path in sorted(self.module_dir.glob("*.md")):
                if path.is_file():
                    paths.append(path.relative_to(PROJECT_ROOT).as_posix())
        for path in (self.changelog_path, self.plugin_description_path):
            if path.exists():
                paths.append(path.relative_to(PROJECT_ROOT).as_posix())
        return tuple(unique_preserving_order(paths))


@dataclass(frozen=True)
class GitSyncProfile:
    """Git 开发分支同步配置"""
    key: str
    display_name: str
    branch: str
    remote: str = DEFAULT_REMOTE_NAME
    allow_dirty_switch: bool = False


@dataclass(frozen=True)
class GitPromoteProfile:
    """Git 分支晋升(合并)配置"""
    key: str
    display_name: str
    source_branch: str
    target_branch: str
    remote: str = DEFAULT_REMOTE_NAME
    excluded_module_keys: tuple[str, ...] = ()
    fetch: bool = True
    pull: bool = True
    return_to_source: bool = True


@dataclass(frozen=True)
class ModuleVersionInfo:
    """模块版本信息"""
    spec: ModuleSpec
    version: str

    @property
    def asset_name(self) -> str:
        return f"{self.spec.artifact_id}-{self.version}.jar"


@dataclass(frozen=True)
class ModuleReleaseInfo:
    """模块发布信息(包含 JAR 路径)"""
    spec: ModuleSpec
    version: str
    jar_path: Path

    @property
    def asset_name(self) -> str:
        return self.jar_path.name


@dataclass(frozen=True)
class ResourceVersionSyncResult:
    """资源版本同步结果"""
    spec: ModuleSpec
    path: Path
    target_version: str
    previous_version: str | None
    status: str
    message: str = ""


@dataclass(frozen=True)
class GitStatusEntry:
    """Git 工作区状态条目"""
    staged: str
    unstaged: str
    path: str
    original_path: str | None = None

    @property
    def code(self) -> str:
        return f"{self.staged}{self.unstaged}"

    @property
    def top_level(self) -> str:
        normalized = self.path.replace("\\", "/")
        return normalized.split("/", 1)[0]


# ============================================================
# 集中配置区
# 以后如果要调整模块、分支或主分支排除策略，优先改这里。
# ============================================================

MODULES: tuple[ModuleSpec, ...] = (
    ModuleSpec(
        key="corelib",
        display_name="Emaki CoreLib",
        module_dir=PROJECT_ROOT / "EmakiCoreLib",
        artifact_id="EmakiCoreLib",
        jar_prefix="[E][绘卷核心]",
        publish_to_modrinth=True,
        modrinth_project_id="EGYz2X2a",  # emaki-corelib
        modrinth_slug="emaki-corelib",
    ),
    ModuleSpec(
        key="corelib-api",
        display_name="Emaki CoreLib API",
        module_dir=PROJECT_ROOT / "EmakiCoreLibApi",
        artifact_id="emaki-corelib-api",
        jar_prefix="[E][绘卷核心API]",
    ),
    ModuleSpec(
        key="forge",
        display_name="Emaki Forge",
        module_dir=PROJECT_ROOT / "EmakiForge",
        artifact_id="EmakiForge",
        jar_prefix="[E][绘卷锻造]",
        publish_to_modrinth=True,
        modrinth_project_id="Ksqjj9TX",  # emaki-forge
        modrinth_slug="emaki-forge",
    ),
    ModuleSpec(
        key="forge-api",
        display_name="Emaki Forge API",
        module_dir=PROJECT_ROOT / "EmakiForgeApi",
        artifact_id="emaki-forge-api",
        jar_prefix="[E][绘卷锻造API]",
    ),
    ModuleSpec(
        key="attribute",
        display_name="Emaki Attribute",
        module_dir=PROJECT_ROOT / "EmakiAttribute",
        artifact_id="EmakiAttribute",
        jar_prefix="[E][绘卷属性]",
        publish_to_modrinth=True,
        modrinth_project_id="ZzBwu6WX",  # emaki-attribute
        modrinth_slug="emaki-attribute",
    ),
    ModuleSpec(
        key="attribute-api",
        display_name="Emaki Attribute API",
        module_dir=PROJECT_ROOT / "EmakiAttributeApi",
        artifact_id="emaki-attribute-api",
        jar_prefix="[E][绘卷属性API]",
    ),
    ModuleSpec(
        key="strengthen",
        display_name="Emaki Strengthen",
        module_dir=PROJECT_ROOT / "EmakiStrengthen",
        artifact_id="EmakiStrengthen",
        jar_prefix="[E][绘卷强化]",
        publish_to_modrinth=True,
        modrinth_project_id="SH4PlNS2",  # emakistrengthen
        modrinth_slug="emakistrengthen",
    ),
    ModuleSpec(
        key="strengthen-api",
        display_name="Emaki Strengthen API",
        module_dir=PROJECT_ROOT / "EmakiStrengthenApi",
        artifact_id="emaki-strengthen-api",
        jar_prefix="[E][绘卷强化API]",
    ),
    ModuleSpec(
        key="cooking",
        display_name="Emaki Cooking",
        module_dir=PROJECT_ROOT / "EmakiCooking",
        artifact_id="EmakiCooking",
        jar_prefix="[E][绘卷烹饪]",
        publish_to_modrinth=True,
        modrinth_project_id="NlpcnczH",  # emakicooking
        modrinth_slug="emakicooking",
    ),
    ModuleSpec(
        key="cooking-api",
        display_name="Emaki Cooking API",
        module_dir=PROJECT_ROOT / "EmakiCookingApi",
        artifact_id="emaki-cooking-api",
        jar_prefix="[E][绘卷烹饪API]",
    ),
    ModuleSpec(
        key="level",
        display_name="Emaki Level",
        module_dir=PROJECT_ROOT / "EmakiLevel",
        artifact_id="EmakiLevel",
        jar_prefix="[E][绘卷等级]",
    ),
    ModuleSpec(
        key="level-api",
        display_name="Emaki Level API",
        module_dir=PROJECT_ROOT / "EmakiLevelApi",
        artifact_id="emaki-level-api",
        jar_prefix="[E][绘卷等级API]",
    ),
    ModuleSpec(
        key="gem",
        display_name="Emaki Gem",
        module_dir=PROJECT_ROOT / "EmakiGem",
        artifact_id="EmakiGem",
        jar_prefix="[E][绘卷宝石]",
        include_in_main_merge=False,
    ),
    ModuleSpec(
        key="gem-api",
        display_name="Emaki Gem API",
        module_dir=PROJECT_ROOT / "EmakiGemApi",
        artifact_id="emaki-gem-api",
        jar_prefix="[E][绘卷宝石API]",
    ),
    ModuleSpec(
        key="skills",
        display_name="Emaki Skills",
        module_dir=PROJECT_ROOT / "EmakiSkills",
        artifact_id="EmakiSkills",
        jar_prefix="[E][绘卷技能]",
        include_in_main_merge=False,
    ),
    ModuleSpec(
        key="skills-api",
        display_name="Emaki Skills API",
        module_dir=PROJECT_ROOT / "EmakiSkillsApi",
        artifact_id="emaki-skills-api",
        jar_prefix="[E][绘卷技能API]",
    ),
    ModuleSpec(
        key="item",
        display_name="Emaki Item",
        module_dir=PROJECT_ROOT / "EmakiItem",
        artifact_id="EmakiItem",
        jar_prefix="[E][绘卷物品]",
        include_in_main_merge=False,
    ),
    ModuleSpec(
        key="item-api",
        display_name="Emaki Item API",
        module_dir=PROJECT_ROOT / "EmakiItemApi",
        artifact_id="emaki-item-api",
        jar_prefix="[E][绘卷物品API]",
    ),
)

MODULES_BY_KEY: dict[str, ModuleSpec] = {module.key: module for module in MODULES}

DEFAULT_GIT_SYNC_PROFILE = GitSyncProfile(
    key="dev-sync",
    display_name="同步当前工作到 dev",
    branch=DEFAULT_DEV_BRANCH,
    remote=DEFAULT_REMOTE_NAME,
    allow_dirty_switch=False,
)

DEFAULT_GIT_PROMOTE_PROFILE = GitPromoteProfile(
    key="main-release",
    display_name="将 dev 合并到 main (自动排除 include_in_main_merge 为 False 的模块)",
    source_branch=DEFAULT_DEV_BRANCH,
    target_branch=DEFAULT_MAIN_BRANCH,
    remote=DEFAULT_REMOTE_NAME,
    excluded_module_keys=tuple(module.key for module in MODULES if not module.include_in_main_merge),
    fetch=True,
    pull=True,
    return_to_source=True,
)


def get_module_by_key(key: str) -> Optional[ModuleSpec]:
    return MODULES_BY_KEY.get(key)


def get_modules_by_keys(keys: list[str] | tuple[str, ...]) -> list[ModuleSpec]:
    modules: list[ModuleSpec] = []
    missing_keys: list[str] = []
    seen: set[str] = set()
    for raw_key in keys:
        key = raw_key.strip()
        if not key or key in seen:
            continue
        seen.add(key)
        module = get_module_by_key(key)
        if module is None:
            missing_keys.append(key)
            continue
        modules.append(module)
    if missing_keys:
        valid_keys = ", ".join(sorted(MODULES_BY_KEY))
        raise ValueError(f"未知模块 key: {', '.join(missing_keys)}。可用 key: {valid_keys}")
    return modules


def print_section(title: str) -> None:
    print(f"\n[{title}]")


def read_input(prompt: str = "") -> str:
    try:
        return input(prompt)
    except EOFError as exc:
        raise KeyboardInterrupt from exc


def prompt_text(prompt: str, default: str | None = None) -> str:
    suffix = f" [{default}]" if default not in {None, ""} else ""
    value = read_input(f"{prompt}{suffix}: ").strip()
    if value:
        return value
    return default or ""


def prompt_secret(prompt: str) -> str:
    try:
        return getpass.getpass(f"{prompt}: ").strip()
    except EOFError as exc:
        raise KeyboardInterrupt from exc


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


def prompt_menu_choice(title: str, options: list[tuple[str, str]], default: str | None = None) -> str:
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


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def normalize_git_path(path: str | Path) -> str:
    value = str(path).strip().replace("\\", "/")
    while value.startswith("./"):
        value = value[2:]
    return value.strip("/")


def unique_preserving_order(values: list[str]) -> list[str]:
    return list(dict.fromkeys(value for value in values if value))


def format_module_list(modules: list[ModuleSpec]) -> str:
    if not modules:
        return "无"
    return ", ".join(f"{module.display_name} [{module.relative_path}]" for module in modules)


def format_path_list(paths: list[str]) -> str:
    if not paths:
        return "无"
    return ", ".join(paths)


def build_promote_merge_message(source_branch: str, target_branch: str, *, has_exclusions: bool) -> str:
    suffix = " (exclude selected modules)" if has_exclusions else ""
    return f"merge: {source_branch} into {target_branch}{suffix}"


def format_shell_args(args: list[str]) -> str:
    return " ".join(args)


def run_command(args: list[str], cwd: Path, *, check: bool = True) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        args,
        cwd=str(cwd),
        text=True,
        capture_output=True,
    )
    if check and result.returncode != 0:
        details: list[str] = []
        if result.stdout.strip():
            details.append(result.stdout.strip())
        if result.stderr.strip():
            details.append(result.stderr.strip())
        detail_text = "\n".join(details) if details else f"exit={result.returncode}"
        raise RuntimeError(f"命令执行失败: {format_shell_args(args)}\n{detail_text}")
    return result


def run_git(args: list[str], cwd: Path = PROJECT_ROOT, *, check: bool = True) -> subprocess.CompletedProcess[str]:
    return run_command(["git", *args], cwd, check=check)


def parse_pom_version(pom_path: Path) -> str:
    import xml.etree.ElementTree as et
    
    tree = et.parse(pom_path)
    root = tree.getroot()
    
    def get_namespace(tag: str) -> str | None:
        if tag.startswith("{") and "}" in tag:
            return tag[1:].split("}", 1)[0]
        return None
    
    namespace_match = get_namespace(root.tag)
    ns = {"m": namespace_match} if namespace_match else {}
    version_node = root.find("m:version", ns) if ns else root.find("version")
    if version_node is None or not version_node.text:
        raise ValueError(f"Direct <version> not found in {pom_path}")
    return version_node.text.strip()


RESOURCE_VERSION_PATTERN = re.compile(r'^(?P<prefix>version\s*:\s*)(?P<quote>["\']?)(?P<version>[^"\'\r\n#]+?)(?P=quote)(?P<suffix>\s*(?:#.*)?)$', re.MULTILINE)


def plugin_runtime_modules() -> list[ModuleSpec]:
    """返回服务器实际安装的插件本体模块，排除 API 模块。"""
    return [module for module in MODULES if not module.key.endswith("-api")]


def plugin_resource_modules() -> list[ModuleSpec]:
    """返回拥有运行时默认资源的插件模块，排除 API 模块。"""
    return plugin_runtime_modules()


def module_resource_version_files(spec: ModuleSpec) -> list[Path]:
    """收集插件 config.yml 与 lang/*.yml 资源文件。"""
    resources_dir = spec.module_dir / "src" / "main" / "resources"
    paths: list[Path] = []
    config_path = resources_dir / "config.yml"
    if config_path.exists():
        paths.append(config_path)
    lang_dir = resources_dir / "lang"
    if lang_dir.exists():
        paths.extend(sorted(path for path in lang_dir.glob("*.yml") if path.is_file()))
    return paths


def replace_yaml_top_level_version(content: str, target_version: str) -> tuple[str, str | None, bool]:
    """替换 YAML 顶层 version 行，返回新内容、旧版本与是否修改。"""
    match = RESOURCE_VERSION_PATTERN.search(content)
    if match is None:
        return content, None, False
    previous_version = match.group("version").strip()
    quote = match.group("quote") or '"'
    replacement = f'{match.group("prefix")}{quote}{target_version}{quote}{match.group("suffix")}'
    if previous_version == target_version and match.group(0) == replacement:
        return content, previous_version, False
    return content[:match.start()] + replacement + content[match.end():], previous_version, True


def sync_module_resource_versions(modules: list[ModuleSpec], *, dry_run: bool = False) -> list[ResourceVersionSyncResult]:
    """同步指定插件模块的 config/lang 资源版本号。"""
    results: list[ResourceVersionSyncResult] = []
    for spec in modules:
        try:
            target_version = parse_pom_version(spec.pom_path)
        except Exception as exc:
            results.append(ResourceVersionSyncResult(spec, spec.pom_path, "", None, "failed", str(exc)))
            continue

        resource_files = module_resource_version_files(spec)
        if not resource_files:
            results.append(ResourceVersionSyncResult(spec, spec.module_dir, target_version, None, "skipped", "未找到 config.yml 或 lang/*.yml"))
            continue

        for path in resource_files:
            try:
                content = read_text(path)
                next_content, previous_version, changed = replace_yaml_top_level_version(content, target_version)
                if previous_version is None:
                    results.append(ResourceVersionSyncResult(spec, path, target_version, None, "missing", "未找到顶层 version 字段"))
                    continue
                if not changed:
                    results.append(ResourceVersionSyncResult(spec, path, target_version, previous_version, "unchanged"))
                    continue
                if not dry_run:
                    path.write_text(next_content, encoding="utf-8")
                results.append(ResourceVersionSyncResult(spec, path, target_version, previous_version, "updated"))
            except Exception as exc:
                results.append(ResourceVersionSyncResult(spec, path, target_version, None, "failed", str(exc)))
    return results


# ============================================================
# JAR 文件操作模块
# ============================================================

def locate_release_jar(spec: ModuleSpec, version: str) -> Path:
    expected = spec.target_dir / f"{spec.artifact_id}-{version}.jar"
    if expected.exists():
        logger.info(f"找到 JAR 文件: {expected}")
        return expected
    raise FileNotFoundError(f"未找到 {spec.display_name} 的构建 JAR 文件: 期望路径 {expected}")


def collect_module_versions(modules: list[ModuleSpec] | tuple[ModuleSpec, ...] | None = None) -> list[ModuleVersionInfo]:
    result: list[ModuleVersionInfo] = []
    for spec in (modules if modules is not None else MODULES):
        try:
            version = parse_pom_version(spec.pom_path)
            result.append(ModuleVersionInfo(spec=spec, version=version))
            logger.info(f"模块 {spec.display_name} 版本: {version}")
        except Exception as e:
            logger.warning(f"无法解析 {spec.display_name} 的版本: {e}")
    return result


def github_release_module_versions() -> list[ModuleVersionInfo]:
    return [module for module in collect_module_versions() if module.spec.publish_to_github]


def collect_module_release_info(source_modules: Optional[list[ModuleVersionInfo]] = None) -> list[ModuleReleaseInfo]:
    result: list[ModuleReleaseInfo] = []
    modules = source_modules if source_modules is not None else collect_module_versions()
    for module in modules:
        try:
            jar_path = locate_release_jar(module.spec, module.version)
            result.append(ModuleReleaseInfo(
                spec=module.spec,
                version=module.version,
                jar_path=jar_path,
            ))
        except FileNotFoundError as e:
            logger.warning(str(e))
    return result


def copy_jar_file(
    source_jar: Path,
    target_dir: Path,
    *,
    prefix: str = "",
    overwrite: bool = False,
) -> tuple[Path, bool]:
    if not source_jar.exists():
        logger.error(f"源 JAR 文件不存在: {source_jar}")
        raise FileNotFoundError(f"源 JAR 文件未找到: {source_jar}")
    
    ensure_dir(target_dir)
    
    new_filename = f"{prefix}{source_jar.name}" if prefix else source_jar.name
    target_path = target_dir / new_filename
    
    if target_path.exists():
        if overwrite:
            logger.info(f"目标文件已存在，将覆盖: {target_path.name}")
            target_path.unlink()
        else:
            logger.info(f"目标文件已存在，跳过: {target_path.name}")
            return target_path, True
    
    shutil.copy2(source_jar, target_path)
    
    if not target_path.exists():
        logger.error(f"复制后验证失败: 目标文件不存在 {target_path}")
        raise IOError(f"复制验证失败: {target_path}")
    
    file_size = target_path.stat().st_size
    logger.info(f"已成功复制: {source_jar.name} -> {target_path.name} ({file_size:,} bytes)")
    return target_path, True


def copy_release_info_list(
    release_info_list: list[ModuleReleaseInfo],
    target_dir: Path,
    *,
    overwrite: bool = False,
    rename_with_prefix: bool = True,
) -> dict[str, tuple[Path | None, bool]]:
    results = {}

    for release_info in release_info_list:
        spec = release_info.spec
        try:
            target_path, success = copy_jar_file(
                source_jar=release_info.jar_path,
                target_dir=target_dir,
                prefix=spec.jar_prefix if rename_with_prefix else "",
                overwrite=overwrite,
            )
            
            if success:
                results[spec.key] = (target_path, True)
                copied_name = f"{spec.jar_prefix}{release_info.asset_name}" if rename_with_prefix else release_info.asset_name
                print(f"  ✓ {spec.display_name}: {copied_name}")
            else:
                results[spec.key] = (None, False)
                print(f"  ✗ {spec.display_name}: 失败")
                
        except Exception as e:
            logger.error(f"复制失败: {spec.display_name}, 错误: {e}")
            results[spec.key] = (None, False)
            print(f"  ✗ {spec.display_name}: 失败 ({e})")
    return results


def copy_all_jars_to_update_dir(target_dir: Path | None = None, overwrite: bool = False) -> dict[str, tuple[Path | None, bool]]:
    if target_dir is None:
        target_dir = PLUGINS_UPDATE_DIR
    
    release_info_list = collect_module_release_info(collect_module_versions(plugin_runtime_modules()))
    
    print_section("JAR 文件复制")
    logger.info(f"目标目录: {target_dir.resolve()}")
    logger.info("复制范围: 插件本体 Jar，不包含 API Jar")
    logger.info(f"发现 {len(release_info_list)} 个可用的 JAR 文件")
    
    if not release_info_list:
        print("\n⚠️  未找到任何可用的 JAR 文件！")
        print("提示: 请先使用 Maven 构建项目 (mvn clean package)\n")
        return {}
    
    results = copy_release_info_list(
        release_info_list,
        target_dir,
        overwrite=overwrite,
        rename_with_prefix=True,
    )
    
    success_count = sum(1 for v in results.values() if v[1])
    total_count = len(results)
    
    print(f"\n{'='*50}")
    print(f"复制完成: {success_count}/{total_count} 个文件成功")
    
    # 简单验证
    verify_copies_in_target(target_dir)
    
    logger.info(f"复制完成: {success_count}/{total_count} 个文件成功")
    return results


def copy_all_jars_to_release_version_dir(target_dir: Path | None = None, overwrite: bool = False) -> dict[str, tuple[Path | None, bool]]:
    if target_dir is None:
        target_dir = RELEASE_VERSION_DIR
    
    release_info_list = collect_module_release_info(collect_module_versions(plugin_runtime_modules()))
    
    print_section("Release JAR 文件复制")
    logger.info(f"目标目录: {target_dir.resolve()}")
    logger.info("复制范围: 插件本体 Jar，不包含 API Jar")
    logger.info(f"发现 {len(release_info_list)} 个可用的 JAR 文件")
    
    if not release_info_list:
        print("\n⚠️  未找到任何可用的 JAR 文件！")
        print("提示: 请先使用 Maven 构建项目 (mvn clean package)\n")
        return {}
    
    results = copy_release_info_list(
        release_info_list,
        target_dir,
        overwrite=overwrite,
        rename_with_prefix=False,
    )
    
    success_count = sum(1 for v in results.values() if v[1])
    total_count = len(results)
    
    print(f"\n{'='*50}")
    print(f"复制完成: {success_count}/{total_count} 个文件成功")
    
    verify_copies_in_target(target_dir)
    
    logger.info(f"复制完成: {success_count}/{total_count} 个文件成功")
    return results


def verify_copies_in_target(target_dir: Path) -> None:
    """验证目标目录中的文件是否存在"""
    print(f"\n目标目录内容检查: {target_dir}")
    
    if not target_dir.exists():
        print(f"  ⚠️  目标目录不存在: {target_dir}")
        return
    
    files_in_target = list(target_dir.glob("*.jar"))
    
    if not files_in_target:
        print(f"  ⚠️  目标目录为空或没有 JAR 文件")
        return
    
    print(f"  发现 {len(files_in_target)} 个 JAR 文件:")
    for f in sorted(files_in_target):
        size = f.stat().st_size
        print(f"    ✓ {f.name} ({size:,} bytes)")


# ============================================================
# 目录同步工具
# ============================================================

def sync_directory(source: Path, target: Path, *, ignore_names: set[str] | None = None) -> None:
    ignore = ignore_names or set()
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


# ============================================================
# Git 工作流工具
# ============================================================

def get_current_branch(cwd: Path = PROJECT_ROOT) -> str:
    return run_git(["branch", "--show-current"], cwd).stdout.strip()


def get_branch_head(branch: str, cwd: Path = PROJECT_ROOT) -> str:
    return run_git(["rev-parse", branch], cwd).stdout.strip()


def get_upstream_branch(cwd: Path = PROJECT_ROOT) -> str | None:
    result = run_git(["rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}"], cwd, check=False)
    if result.returncode != 0:
        return None
    value = result.stdout.strip()
    return value or None


def get_ahead_behind(upstream: str, cwd: Path = PROJECT_ROOT) -> tuple[int, int] | None:
    result = run_git(["rev-list", "--left-right", "--count", f"{upstream}...HEAD"], cwd, check=False)
    if result.returncode != 0:
        return None
    parts = result.stdout.strip().split()
    if len(parts) != 2:
        return None
    behind = int(parts[0])
    ahead = int(parts[1])
    return ahead, behind


def local_branch_exists(branch: str, cwd: Path = PROJECT_ROOT) -> bool:
    result = run_git(["show-ref", "--verify", "--quiet", f"refs/heads/{branch}"], cwd, check=False)
    return result.returncode == 0


def remote_branch_known(remote: str, branch: str, cwd: Path = PROJECT_ROOT) -> bool:
    result = run_git(["show-ref", "--verify", "--quiet", f"refs/remotes/{remote}/{branch}"], cwd, check=False)
    return result.returncode == 0


def resolve_branch_ref(branch: str, cwd: Path = PROJECT_ROOT, *, remote: str | None = None) -> str:
    if local_branch_exists(branch, cwd):
        return branch
    if remote and remote_branch_known(remote, branch, cwd):
        return f"{remote}/{branch}"
    raise RuntimeError(f"找不到分支引用: {branch}")


def parse_git_status_entries(cwd: Path = PROJECT_ROOT) -> list[GitStatusEntry]:
    result = run_git(["status", "--porcelain=v1"], cwd)
    entries: list[GitStatusEntry] = []
    for raw_line in result.stdout.splitlines():
        if len(raw_line) < 3:
            continue
        staged = raw_line[0]
        unstaged = raw_line[1]
        content = raw_line[3:]
        original_path = None
        path = content
        if " -> " in content:
            original_path, path = content.split(" -> ", 1)
        entries.append(GitStatusEntry(
            staged=staged,
            unstaged=unstaged,
            path=path,
            original_path=original_path,
        ))
    return entries


def describe_git_status_entry(entry: GitStatusEntry) -> str:
    if entry.code == "??":
        return "未跟踪"
    if "U" in entry.code:
        return "冲突"
    if "R" in entry.code:
        return "重命名"
    if "A" in entry.code:
        return "新增"
    if "D" in entry.code:
        return "删除"
    if "C" in entry.code:
        return "复制"
    return "修改"


def summarize_git_status(entries: list[GitStatusEntry]) -> Counter[str]:
    summary: Counter[str] = Counter()
    for entry in entries:
        summary[describe_git_status_entry(entry)] += 1
    return summary


def summarize_git_top_levels(entries: list[GitStatusEntry]) -> Counter[str]:
    summary: Counter[str] = Counter()
    for entry in entries:
        summary[entry.top_level] += 1
    return summary


def has_staged_changes(cwd: Path = PROJECT_ROOT) -> bool:
    result = run_git(["diff", "--cached", "--quiet", "--exit-code"], cwd, check=False)
    return result.returncode != 0


def count_commits_between(base_branch: str, compare_branch: str, cwd: Path = PROJECT_ROOT) -> int:
    output = run_git(["rev-list", "--count", f"{base_branch}..{compare_branch}"], cwd).stdout.strip()
    return int(output or "0")


def preview_commits_between(base_branch: str, compare_branch: str, cwd: Path = PROJECT_ROOT, *, limit: int = 20) -> str:
    result = run_git(
        ["log", "--oneline", "--decorate", f"--max-count={limit}", f"{base_branch}..{compare_branch}"],
        cwd,
        check=False,
    )
    return result.stdout.strip()


def stage_git_paths(paths: list[str] | None, cwd: Path = PROJECT_ROOT) -> None:
    if paths:
        run_git(["add", "--", *paths], cwd)
    else:
        run_git(["add", "-A"], cwd)


def git_path_exists_in_ref(ref: str, path: str, cwd: Path = PROJECT_ROOT) -> bool:
    normalized_path = normalize_git_path(path)
    result = run_git(["cat-file", "-e", f"{ref}:{normalized_path}"], cwd, check=False)
    return result.returncode == 0


def get_unmerged_paths(cwd: Path = PROJECT_ROOT) -> list[str]:
    result = run_git(["diff", "--name-only", "--diff-filter=U"], cwd, check=False)
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def merge_in_progress(cwd: Path = PROJECT_ROOT) -> bool:
    result = run_git(["rev-parse", "-q", "--verify", "MERGE_HEAD"], cwd, check=False)
    return result.returncode == 0


def abort_merge_if_needed(cwd: Path = PROJECT_ROOT) -> bool:
    if not merge_in_progress(cwd):
        return False
    run_git(["merge", "--abort"], cwd, check=False)
    return True


def remove_local_path(path: Path, *, max_retries: int = 3, retry_delay: float = 0.5) -> bool:
    """删除本地路径，带 Windows 文件锁定重试机制。返回是否成功删除。"""
    if not path.exists():
        return True
    for attempt in range(1, max_retries + 1):
        try:
            if path.is_dir():
                shutil.rmtree(path)
            else:
                path.unlink()
            return True
        except PermissionError:
            if attempt < max_retries:
                logger.warning(f"文件被占用，{retry_delay}s 后重试 ({attempt}/{max_retries}): {path}")
                time.sleep(retry_delay)
                retry_delay *= 2
            else:
                logger.warning(f"无法删除文件（已被其他进程占用）: {path}")
                return False
        except OSError as e:
            logger.warning(f"删除文件失败: {path}，错误: {e}")
            return False
    return False


def collect_preserved_local_files(
    path: str,
    cwd: Path = PROJECT_ROOT,
    *,
    tracked_paths: list[str] | tuple[str, ...] | None = None,
) -> dict[str, bytes]:
    """收集排除模块时必须保留的文件。

    默认保留被 Git 忽略或未跟踪的本地文件，避免删除私有资料。
    tracked_paths 用于显式保留已跟踪文件，例如排除插件的 CHANGELOG.md
    与 Plugin Description.md；这些文件应随 dev 合并到 main，即使模块主体被排除。
    """
    result = run_git(["status", "--porcelain=v1", "--ignored", "--", path], cwd, check=False)
    preserved: dict[str, bytes] = {}
    for raw_line in result.stdout.splitlines():
        if len(raw_line) < 4:
            continue
        status = raw_line[:2]
        if status not in {"??", "!!"}:
            continue
        relative_path = normalize_git_path(raw_line[3:])
        absolute_path = cwd / Path(relative_path)
        if absolute_path.is_file():
            preserved[relative_path] = absolute_path.read_bytes()

    for raw_path in tracked_paths or ():
        relative_path = normalize_git_path(raw_path)
        absolute_path = cwd / Path(relative_path)
        if absolute_path.is_file():
            preserved[relative_path] = absolute_path.read_bytes()
    return preserved


def restore_preserved_local_files(files: dict[str, bytes], cwd: Path = PROJECT_ROOT) -> None:
    for relative_path, content in files.items():
        absolute_path = cwd / Path(relative_path)
        ensure_dir(absolute_path.parent)
        absolute_path.write_bytes(content)


def apply_merge_exclusions(
    excluded_paths: list[str],
    *,
    base_ref: str = "HEAD",
    cwd: Path = PROJECT_ROOT,
    preserved_tracked_paths: dict[str, tuple[str, ...]] | None = None,
) -> None:
    preserved_tracked_paths = preserved_tracked_paths or {}
    for path in excluded_paths:
        normalized_path = normalize_git_path(path)
        absolute_path = cwd / Path(normalized_path)
        preserved_files = collect_preserved_local_files(
            normalized_path,
            cwd,
            tracked_paths=preserved_tracked_paths.get(normalized_path, ()),
        )
        if git_path_exists_in_ref(base_ref, normalized_path, cwd):
            checkout_result = run_git(["checkout", "--ours", "--", normalized_path], cwd, check=False)
            if checkout_result.returncode != 0:
                run_git(["checkout", base_ref, "--", normalized_path], cwd)
            restore_preserved_local_files(preserved_files, cwd)
            run_git(["add", "--", normalized_path], cwd)
            print(f"  - 保留目标分支版本: {normalized_path}")
            if preserved_files:
                print(f"    已保留本地忽略/未跟踪文件: {len(preserved_files)} 个")
            continue

        run_git(["rm", "-r", "-f", "--ignore-unmatch", "--", normalized_path], cwd, check=False)
        deleted = remove_local_path(absolute_path)
        restore_preserved_local_files(preserved_files, cwd)
        status = "删除" if deleted else "标记删除（文件被占用，稍后手动清理）"
        print(f"  - {status}仅来自源分支的路径: {normalized_path}")
        if preserved_files:
            print(f"    已保留本地忽略/未跟踪文件: {len(preserved_files)} 个")


def ensure_clean_worktree(*, context: str, cwd: Path = PROJECT_ROOT) -> None:
    entries = parse_git_status_entries(cwd)
    if not entries:
        return
    raise RuntimeError(f"{context} 需要干净工作区，请先提交、暂存或清理当前改动。")


def ensure_safe_branch_switch(target_branch: str, cwd: Path = PROJECT_ROOT, *, allow_dirty_switch: bool = False) -> None:
    if allow_dirty_switch:
        return
    current_branch = get_current_branch(cwd)
    if current_branch == target_branch:
        return
    entries = parse_git_status_entries(cwd)
    if not entries:
        return
    if not local_branch_exists(target_branch, cwd):
        return
    current_head = get_branch_head(current_branch, cwd)
    target_head = get_branch_head(target_branch, cwd)
    if current_head != target_head:
        raise RuntimeError(
            f"当前工作区仍有未提交改动，且目标分支 {target_branch} 与当前分支提交点不同。"
            "为避免切换冲突，请先提交当前改动，或改为从当前 HEAD 新建分支。"
        )


def checkout_or_create_branch(branch: str, cwd: Path = PROJECT_ROOT, *, start_point: str | None = None) -> str:
    current_branch = get_current_branch(cwd)
    if current_branch == branch:
        return "already-on-branch"
    if local_branch_exists(branch, cwd):
        run_git(["switch", branch], cwd)
        return "switched-existing"
    switch_args = ["switch", "-c", branch]
    if start_point:
        switch_args.append(start_point)
    run_git(switch_args, cwd)
    return "created-new"


def describe_branch_action(action: str) -> str:
    mapping = {
        "already-on-branch": "已位于目标分支",
        "switched-existing": "已切换到已有本地分支",
        "created-new": "已创建并切换到新分支",
    }
    return mapping.get(action, action)


def push_branch(remote: str, branch: str, cwd: Path = PROJECT_ROOT) -> None:
    upstream = get_upstream_branch(cwd)
    if upstream:
        run_git(["push", remote, branch], cwd)
    else:
        run_git(["push", "-u", remote, branch], cwd)


def resolve_promote_excluded_modules_and_paths(
    args: argparse.Namespace,
    profile: GitPromoteProfile,
) -> tuple[list[ModuleSpec], list[str], list[str], dict[str, tuple[str, ...]]]:
    module_keys: list[str] = []
    if getattr(args, "use_default_excludes", True):
        module_keys.extend(profile.excluded_module_keys)
    module_keys.extend(getattr(args, "exclude_modules", None) or [])

    modules = get_modules_by_keys(module_keys)
    module_paths = [module.relative_path for module in modules]
    preserved_tracked_paths = {
        module.relative_path: module.main_merge_preserved_paths
        for module in modules
        if module.main_merge_preserved_paths
    }
    extra_paths: list[str] = []
    for path in getattr(args, "exclude_paths", None) or []:
        normalized_path = normalize_git_path(path)
        if normalized_path:
            extra_paths.append(normalized_path)
    extra_paths = unique_preserving_order(extra_paths)
    all_paths = unique_preserving_order([*module_paths, *extra_paths])
    return modules, all_paths, extra_paths, preserved_tracked_paths


# ============================================================
# GitHub API 模块
# ============================================================

def build_repo_auth_url(token: str) -> str:
    quoted = urllib.parse.quote(token, safe="")
    return f"https://x-access-token:{quoted}@github.com/{GITHUB_OWNER}/{GITHUB_REPO}.git"


def parse_env_value(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
        return value[1:-1]
    return value


def load_local_env_tokens() -> None:
    global _LOCAL_ENV_LOADED
    if _LOCAL_ENV_LOADED:
        return
    _LOCAL_ENV_LOADED = True
    for path in LOCAL_TOKEN_ENV_FILES:
        if not path.exists() or not path.is_file():
            continue
        for raw_line in path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("export "):
                line = line[7:].strip()
            if "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key):
                continue
            os.environ.setdefault(key, parse_env_value(value))


def first_env_token(*names: str) -> str:
    load_local_env_tokens()
    for name in names:
        value = os.environ.get(name, "").strip()
        if value:
            return value
    return ""


def token_error_message(provider: str, env_keys: tuple[str, ...]) -> str:
    env_names = " / ".join(env_keys)
    primary_key = env_keys[0]
    return (
        f"{provider} Token 为空，请通过 --token 参数传入，设置环境变量: {env_names}，"
        f"或在项目根目录 .env 写入: {primary_key}=你的令牌"
    )


def prompt_token(provider: str, env_keys: tuple[str, ...]) -> str:
    token = first_env_token(*env_keys)
    if token:
        return token
    primary_key = env_keys[0]
    print(f"未检测到 {provider} Token。")
    print(f"可在项目根目录 .env 中长期保存: {primary_key}=你的令牌")
    token = prompt_secret(f"请输入 {provider} Token（输入不会显示，留空取消）")
    if not token:
        raise RuntimeError(token_error_message(provider, env_keys))
    os.environ[primary_key] = token
    return token


def get_github_token(explicit: str | None) -> str:
    if explicit and explicit.strip():
        return explicit.strip()
    token = first_env_token(*GITHUB_TOKEN_ENV_KEYS)
    if token:
        return token
    raise RuntimeError(token_error_message("GitHub", GITHUB_TOKEN_ENV_KEYS))


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


def github_request(method: str, url: str, *, token: str | None, json_body: dict | None = None,
                   raw_body: bytes | None = None, content_type: str | None = None) -> tuple[int, object, dict[str, str]]:
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


def list_all_releases(token: str) -> list[dict]:
    releases: list[dict] = []
    page = 1
    while True:
        url = f"https://api.github.com/repos/{GITHUB_OWNER}/{GITHUB_REPO}/releases?per_page=100&page={page}"
        status, payload, _ = github_request("GET", url, token=token)
        if status != 200:
            raise RuntimeError(f"获取 Release 列表失败: {payload}")
        if not payload:
            break
        releases.extend(payload)
        if len(payload) < 100:
            break
        page += 1
    return releases


def get_release_by_tag(tag: str, token: str) -> dict | None:
    encoded = urllib.parse.quote(tag, safe="")
    url = f"https://api.github.com/repos/{GITHUB_OWNER}/{GITHUB_REPO}/releases/tags/{encoded}"
    status, payload, _ = github_request("GET", url, token=token)
    if status == 200:
        return payload
    if status == 404:
        return None
    raise RuntimeError(f"查询 Release (tag={tag}) 失败: {payload}")


def create_release(token: str, body: dict) -> dict:
    url = f"https://api.github.com/repos/{GITHUB_OWNER}/{GITHUB_REPO}/releases"
    status, payload, _ = github_request("POST", url, token=token, json_body=body)
    if status not in {200, 201}:
        raise RuntimeError(f"创建 Release 失败: {payload}")
    return payload


def update_release(release_id: int, token: str, body: dict) -> dict:
    url = f"https://api.github.com/repos/{GITHUB_OWNER}/{GITHUB_REPO}/releases/{release_id}"
    status, payload, _ = github_request("PATCH", url, token=token, json_body=body)
    if status != 200:
        raise RuntimeError(f"更新 Release (ID={release_id}) 失败: {payload}")
    return payload


def upload_release_asset(upload_url_template: str, token: str, file_path: Path) -> dict:
    upload_url = upload_url_template.split("{", 1)[0]
    query = urllib.parse.urlencode({"name": file_path.name})
    content_type = mimetypes.guess_type(file_path.name)[0] or "application/java-archive"
    status, payload, _ = github_request(
        "POST", f"{upload_url}?{query}", token=token,
        raw_body=file_path.read_bytes(), content_type=content_type,
    )
    if status not in {200, 201}:
        raise RuntimeError(f"上传附件 {file_path.name} 失败: {payload}")
    return payload


# ============================================================
# Modrinth API 模块
# ============================================================

def get_modrinth_token(explicit: str | None) -> str:
    if explicit and explicit.strip():
        return explicit.strip()
    token = first_env_token(*MODRINTH_TOKEN_ENV_KEYS)
    if token:
        return token
    raise RuntimeError(token_error_message("Modrinth", MODRINTH_TOKEN_ENV_KEYS))


def modrinth_api_headers(token: str) -> dict[str, str]:
    return {
        "Authorization": token,
        "User-Agent": "emaki-manager",
    }


def modrinth_request(method: str, url: str, *, token: str, json_body: dict | None = None) -> tuple[int, object]:
    """发送 Modrinth JSON API 请求（非 multipart）"""
    data = None
    headers = modrinth_api_headers(token)
    headers["Accept"] = "application/json"
    if json_body is not None:
        data = json.dumps(json_body).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request) as response:
            payload = json.loads(response.read().decode("utf-8"))
            return response.status, payload
    except urllib.error.HTTPError as exc:
        raw = exc.read()
        try:
            parsed = json.loads(raw.decode("utf-8"))
        except Exception:
            parsed = raw.decode("utf-8", errors="replace")
        return exc.code, parsed


def modrinth_list_versions(project_id: str, token: str) -> list[dict]:
    url = f"{MODRINTH_API_BASE}/project/{urllib.parse.quote(project_id, safe='')}/version"
    status, payload = modrinth_request("GET", url, token=token)
    if status != 200:
        raise RuntimeError(f"获取 Modrinth 版本列表失败 (project={project_id}): {payload}")
    return payload


def modrinth_create_version(
    token: str,
    *,
    project_id: str,
    version_number: str,
    version_title: str,
    changelog: str,
    jar_path: Path,
    game_versions: list[str],
    loaders: list[str],
    version_type: str = "release",
    dependencies: list[dict] | None = None,
) -> dict:
    """通过 multipart/form-data 创建 Modrinth 版本并上传 JAR"""
    import uuid

    file_part_name = "file"
    version_data = {
        "project_id": project_id,
        "file_parts": [file_part_name],
        "version_number": version_number,
        "name": version_title,
        "changelog": changelog,
        "version_type": version_type,
        "loaders": loaders,
        "game_versions": game_versions,
        "featured": True,
        "dependencies": dependencies or [],
        "primary_file": file_part_name,
    }

    boundary = f"----EmakiManager{uuid.uuid4().hex}"
    body_parts: list[bytes] = []

    # data 字段 (JSON)
    body_parts.append(f"--{boundary}\r\n".encode())
    body_parts.append(b'Content-Disposition: form-data; name="data"\r\n')
    body_parts.append(b"Content-Type: application/json\r\n\r\n")
    body_parts.append(json.dumps(version_data).encode("utf-8"))
    body_parts.append(b"\r\n")

    # file 字段 (JAR)
    jar_bytes = jar_path.read_bytes()
    body_parts.append(f"--{boundary}\r\n".encode())
    body_parts.append(f'Content-Disposition: form-data; name="{file_part_name}"; filename="{jar_path.name}"\r\n'.encode())
    body_parts.append(b"Content-Type: application/java-archive\r\n\r\n")
    body_parts.append(jar_bytes)
    body_parts.append(b"\r\n")

    # 结束
    body_parts.append(f"--{boundary}--\r\n".encode())

    raw_body = b"".join(body_parts)
    content_type = f"multipart/form-data; boundary={boundary}"

    headers = modrinth_api_headers(token)
    headers["Content-Type"] = content_type
    headers["Accept"] = "application/json"

    url = f"{MODRINTH_API_BASE}/version"
    request = urllib.request.Request(url, data=raw_body, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request) as response:
            payload = json.loads(response.read().decode("utf-8"))
            return payload
    except urllib.error.HTTPError as exc:
        raw = exc.read()
        try:
            parsed = json.loads(raw.decode("utf-8"))
        except Exception:
            parsed = raw.decode("utf-8", errors="replace")
        raise RuntimeError(f"Modrinth 创建版本失败 (HTTP {exc.code}): {parsed}")


def extract_modrinth_changelog(changelog_path: Path) -> str:
    """从 CHANGELOG.md 中提取 Modrinth Changelog 部分"""
    if not changelog_path.exists():
        return ""
    content = read_text(changelog_path)
    markers = (
        "## Modrinth Changelog"
        )
    marker_idx = -1
    marker_len = 0
    for marker in markers:
        idx = content.find(marker)
        if idx != -1 and (marker_idx == -1 or idx < marker_idx):
            marker_idx = idx
            marker_len = len(marker)
    if marker_idx == -1:
        return ""
    section = content[marker_idx + marker_len:]
    # 截断到下一个 --- 分隔符（如果有的话）
    end_idx = section.find("\n---")
    if end_idx != -1:
        section = section[:end_idx]
    return section.strip()


def modrinth_module_versions() -> list[ModuleVersionInfo]:
    return [m for m in collect_module_versions() if m.spec.publish_to_modrinth and m.spec.modrinth_project_id]


def scan_release_version_dir(target_dir: Path | None = None) -> list[ModuleReleaseInfo]:
    """从 release version 目录扫描 JAR 文件，解析模块和版本号。
    文件名格式: {artifact_id}-{version}.jar"""
    if target_dir is None:
        target_dir = RELEASE_VERSION_DIR
    if not target_dir.exists():
        return []
    result: list[ModuleReleaseInfo] = []
    for spec in MODULES:
        prefix = f"{spec.artifact_id}-"
        for jar in target_dir.glob(f"{prefix}*.jar"):
            name = jar.name
            if not name.startswith(prefix) or not name.endswith(".jar"):
                continue
            version = name[len(prefix):-4]
            if version:
                result.append(ModuleReleaseInfo(spec=spec, version=version, jar_path=jar))
                break  # 每个模块只取一个（目录里应该只有一个）
    return result


def modrinth_release_infos() -> list[ModuleReleaseInfo]:
    """从 release version 目录获取需要发布到 Modrinth 的模块信息"""
    return [r for r in scan_release_version_dir() if r.spec.publish_to_modrinth and r.spec.modrinth_project_id]


def build_modrinth_published_version_map(token: str, modules: list[ModuleVersionInfo]) -> dict[str, set[str]]:
    """查询各模块在 Modrinth 上已发布的版本号"""
    published: dict[str, set[str]] = {}
    for module in modules:
        project_id = module.spec.modrinth_project_id
        try:
            versions = modrinth_list_versions(project_id, token)
            published[module.spec.key] = {v["version_number"] for v in versions}
        except Exception as e:
            logger.warning(f"查询 Modrinth 版本失败 ({module.spec.display_name}): {e}")
            published[module.spec.key] = set()
    return published


def modrinth_get_latest_version(project_id: str, token: str) -> dict | None:
    """获取 Modrinth 项目的最新版本（按日期排序第一个）"""
    versions = modrinth_list_versions(project_id, token)
    if not versions:
        return None
    return versions[0]


def modrinth_resolve_corelib_dependency(token: str) -> list[dict]:
    """获取 EmakiCoreLib 在 Modrinth 上的最新 version，构建 required 依赖项。
    如果 CoreLib 未配置 Modrinth 发布或查询失败，返回空列表。"""
    corelib_spec = get_module_by_key("corelib")
    if corelib_spec is None or not corelib_spec.publish_to_modrinth or not corelib_spec.modrinth_project_id:
        return []
    try:
        latest = modrinth_get_latest_version(corelib_spec.modrinth_project_id, token)
        if latest is None:
            logger.warning("EmakiCoreLib 在 Modrinth 上没有已发布版本，跳过依赖声明。")
            return []
        return [{
            "version_id": latest["id"],
            "project_id": latest["project_id"],
            "dependency_type": "required",
        }]
    except Exception as e:
        logger.warning(f"查询 EmakiCoreLib Modrinth 最新版本失败: {e}")
        return []


# ============================================================
# 发布辅助函数
# ============================================================

def build_published_version_map(releases: list[dict]) -> dict[str, set[str]]:
    published_versions = {spec.key: set() for spec in MODULES if spec.publish_to_github}
    for release in releases:
        for asset in release.get("assets", []):
            name = asset.get("name")
            if not name or not name.endswith(".jar"):
                continue
            for spec in MODULES:
                if not spec.publish_to_github:
                    continue
                prefix = f"{spec.artifact_id}-"
                if not name.startswith(prefix):
                    continue
                version = name[len(prefix):-4]
                if version:
                    published_versions[spec.key].add(version)
                break
    return published_versions


def split_modules_for_publish(module_versions: list[ModuleVersionInfo], published_versions: dict[str, set[str]]) -> tuple[list[ModuleVersionInfo], list[ModuleVersionInfo]]:
    pending: list[ModuleVersionInfo] = []
    already_published: list[ModuleVersionInfo] = []
    for module in module_versions:
        module_published_versions = published_versions.get(module.spec.key, set())
        if module.version in module_published_versions:
            already_published.append(module)
        else:
            pending.append(module)
    return pending, already_published


def format_published_versions(versions: set[str]) -> str:
    if not versions:
        return "无"
    return ", ".join(sorted(versions))


def build_release_tag(changed_modules: list[ModuleReleaseInfo]) -> str:
    parts = [f"{module.spec.key}-{module.version}" for module in changed_modules]
    return "release-" + "__".join(parts)


def build_release_name(changed_modules: list[ModuleReleaseInfo]) -> str:
    return " / ".join(f"{module.spec.display_name} {module.version}" for module in changed_modules)


def build_default_release_notes(changed_modules: list[ModuleReleaseInfo]) -> str:
    sections: list[str] = []
    for module in changed_modules:
        sections.append(textwrap.dedent(f"""\
                ### {module.spec.display_name}

                - 发布版本: `{module.version}`
                - 发布附件: `{module.asset_name}`
                """).strip())
    return "\n\n".join(sections)


def load_release_notes(args: argparse.Namespace, changed_modules: list[ModuleReleaseInfo]) -> str:
    notes_file = getattr(args, "notes_file", None)
    if notes_file:
        path = Path(notes_file).resolve()
        if not path.exists():
            raise FileNotFoundError(f"Release 说明文件未找到: {path}")
        return read_text(path).strip()
    if DEFAULT_RELEASE_NOTES_FILE.exists():
        return read_text(DEFAULT_RELEASE_NOTES_FILE).strip()
    return build_default_release_notes(changed_modules)


# ============================================================
# 命令实现
# ============================================================

def resolve_resource_sync_modules(raw_keys: list[str] | None) -> list[ModuleSpec]:
    if raw_keys:
        modules = get_modules_by_keys(raw_keys)
    else:
        modules = plugin_resource_modules()
    api_modules = [module.key for module in modules if module.key.endswith("-api")]
    if api_modules:
        raise ValueError(f"资源版本同步不支持 API 模块: {', '.join(api_modules)}")
    return modules


def print_resource_version_sync_plan(modules: list[ModuleSpec], results: list[ResourceVersionSyncResult], *, dry_run: bool) -> None:
    print_section("资源版本同步")
    print(f"模式: {'预览，不写入文件' if dry_run else '实际写入'}")
    print(f"模块: {format_module_list(modules)}\n")

    status_labels = {
        "updated": "待更新" if dry_run else "已更新",
        "unchanged": "已一致",
        "missing": "缺少 version",
        "skipped": "跳过",
        "failed": "失败",
    }
    for result in results:
        label = status_labels.get(result.status, result.status)
        path_text = result.path.relative_to(PROJECT_ROOT).as_posix() if result.path.is_relative_to(PROJECT_ROOT) else str(result.path)
        if result.previous_version is not None:
            print(f"  - [{label}] {path_text}: {result.previous_version} -> {result.target_version}")
        elif result.message:
            print(f"  - [{label}] {path_text}: {result.message}")
        else:
            print(f"  - [{label}] {path_text}")

    counter = Counter(result.status for result in results)
    print("\n统计:")
    for status in ("updated", "unchanged", "missing", "skipped", "failed"):
        if counter.get(status, 0):
            print(f"  - {status_labels.get(status, status)}: {counter[status]}")


def command_sync_resource_versions(args: argparse.Namespace) -> int:
    modules = resolve_resource_sync_modules(getattr(args, "modules", None))
    dry_run = bool(getattr(args, "dry_run", False))

    preview_results = sync_module_resource_versions(modules, dry_run=True)
    print_resource_version_sync_plan(modules, preview_results, dry_run=True)

    if dry_run:
        return 1 if any(result.status == "failed" for result in preview_results) else 0

    pending_updates = [result for result in preview_results if result.status == "updated"]
    if not pending_updates:
        print("\n没有需要同步的资源版本。")
        return 1 if any(result.status == "failed" for result in preview_results) else 0

    if not getattr(args, "yes", False) and not prompt_yes_no("确认写入以上资源版本更新吗", default=False):
        print("已取消资源版本同步。")
        return 0

    results = sync_module_resource_versions(modules, dry_run=False)
    print_resource_version_sync_plan(modules, results, dry_run=False)
    return 1 if any(result.status == "failed" for result in results) else 0


def command_copy_jars(args: argparse.Namespace) -> int:
    target_dir = Path(args.target) if args.target else PLUGINS_UPDATE_DIR
    overwrite = args.overwrite
    
    print_section("JAR 文件复制")
    print(f"目标目录: {target_dir.resolve()}")
    print("复制范围: 插件本体 Jar，不包含 API Jar")
    print(f"覆盖已有文件: {'是' if overwrite else '否'}\n")
    
    release_info_list = collect_module_release_info(collect_module_versions(plugin_runtime_modules()))
    
    if not release_info_list:
        print("⚠️  未找到任何可用的 JAR 文件。")
        print("提示: 请先使用 Maven 构建项目 (mvn clean package)\n")
        return 1
    
    print("将复制以下文件:")
    for info in release_info_list:
        size = info.jar_path.stat().st_size
        print(f"  - {info.spec.jar_prefix}{info.asset_name} ({size:,} bytes)")
    
    if not args.yes and not prompt_yes_no("确认复制以上文件吗", default=False):
        print("已取消复制。")
        return 0
    
    results = copy_all_jars_to_update_dir(target_dir, overwrite)
    
    if not results:
        return 1
    
    all_success = all(v[1] for v in results.values())
    return 0 if all_success else 1


def command_copy_release_jars(args: argparse.Namespace) -> int:
    target_dir = Path(args.target) if args.target else RELEASE_VERSION_DIR
    overwrite = args.overwrite
    
    print_section("Release JAR 文件复制")
    print(f"目标目录: {target_dir.resolve()}")
    print("复制方式: 保留原始 JAR 文件名")
    print("复制范围: 插件本体 Jar，不包含 API Jar")
    print(f"覆盖已有文件: {'是' if overwrite else '否'}\n")
    
    release_info_list = collect_module_release_info(collect_module_versions(plugin_runtime_modules()))
    
    if not release_info_list:
        print("⚠️  未找到任何可用的 JAR 文件。")
        print("提示: 请先使用 Maven 构建项目 (mvn clean package)\n")
        return 1
    
    print("将复制以下文件:")
    for info in release_info_list:
        size = info.jar_path.stat().st_size
        print(f"  - {info.asset_name} ({size:,} bytes)")
    
    if not args.yes and not prompt_yes_no("确认复制以上文件吗", default=False):
        print("已取消复制。")
        return 0
    
    results = copy_all_jars_to_release_version_dir(target_dir, overwrite)
    
    if not results:
        return 1
    
    all_success = all(v[1] for v in results.values())
    return 0 if all_success else 1


def _find_npm(docs_dir: Path) -> str:
    """查找 npm 可执行文件路径"""
    npm_cmd = shutil.which("npm")
    if npm_cmd:
        return npm_cmd
    raise RuntimeError(
        "未找到 npm 命令。请先安装 Node.js (https://nodejs.org/)，"
        "并确保 npm 在系统 PATH 中。"
    )


def _ensure_docs_deps(docs_dir: Path) -> None:
    """确保 docs 目录已安装依赖"""
    node_modules = docs_dir / "node_modules"
    if node_modules.exists():
        return
    npm = _find_npm(docs_dir)
    print("正在安装文档依赖...")
    subprocess.run([npm, "install"], cwd=str(docs_dir), check=True, text=True)
    print("依赖安装完成。")


def command_docs_build(args: argparse.Namespace) -> int:
    docs_dir = Path(getattr(args, "docs_dir", DOCS_DIR)).resolve()
    if not (docs_dir / "package.json").exists():
        raise FileNotFoundError(f"文档目录未找到 package.json: {docs_dir}")

    npm = _find_npm(docs_dir)
    _ensure_docs_deps(docs_dir)

    print_section("VitePress 文档构建")
    print(f"文档目录: {docs_dir}")

    result = subprocess.run([npm, "run", "build"], cwd=str(docs_dir), text=True)
    if result.returncode != 0:
        print("文档构建失败。")
        return 1

    dist_dir = docs_dir / ".vitepress" / "dist"
    if dist_dir.exists():
        file_count = sum(1 for _ in dist_dir.rglob("*") if _.is_file())
        print(f"构建成功，产物目录: {dist_dir}")
        print(f"产物文件数: {file_count}")
    else:
        print("构建完成，但未找到产物目录。")

    return 0


def command_docs_preview(args: argparse.Namespace) -> int:
    docs_dir = Path(getattr(args, "docs_dir", DOCS_DIR)).resolve()
    if not (docs_dir / "package.json").exists():
        raise FileNotFoundError(f"文档目录未找到 package.json: {docs_dir}")

    npm = _find_npm(docs_dir)
    _ensure_docs_deps(docs_dir)

    dist_dir = docs_dir / ".vitepress" / "dist"
    if not dist_dir.exists():
        print("产物目录不存在，先执行构建...")
        build_result = command_docs_build(argparse.Namespace(docs_dir=str(docs_dir)))
        if build_result != 0:
            return build_result

    print_section("VitePress 文档预览")
    print(f"文档目录: {docs_dir}")
    print("启动预览服务器... (按 Ctrl+C 停止)")
    try:
        subprocess.run([npm, "run", "preview"], cwd=str(docs_dir), text=True)
    except KeyboardInterrupt:
        print("\n预览服务器已停止。")
    return 0


def command_docs_deploy(args: argparse.Namespace) -> int:
    docs_dir = Path(getattr(args, "docs_dir", DOCS_DIR)).resolve()
    branch = getattr(args, "branch", "gh-pages")

    if not (docs_dir / "package.json").exists():
        raise FileNotFoundError(f"文档目录未找到 package.json: {docs_dir}")

    npm = _find_npm(docs_dir)
    _ensure_docs_deps(docs_dir)

    print_section("VitePress 文档部署")
    print(f"文档目录: {docs_dir}")
    print(f"目标分支: {branch}")
    print(f"仓库: {GITHUB_OWNER}/{GITHUB_REPO}")

    # 构建
    print("\n正在构建文档...")
    # 清理旧的构建产物和缓存，确保全新构建
    import shutil
    for clean_target in [docs_dir / ".vitepress" / "dist", docs_dir / ".vitepress" / "cache"]:
        if clean_target.exists():
            shutil.rmtree(clean_target, ignore_errors=True)
    build_result = subprocess.run([npm, "run", "build"], cwd=str(docs_dir), text=True)
    if build_result.returncode != 0:
        print("文档构建失败，中止部署。")
        return 1

    dist_dir = docs_dir / ".vitepress" / "dist"
    if not dist_dir.exists():
        raise RuntimeError("构建产物目录不存在。")

    if args.dry_run:
        file_count = sum(1 for _ in dist_dir.rglob("*") if _.is_file())
        print(f"\n[预览模式] 构建成功，共 {file_count} 个文件待部署到 {branch} 分支。")
        return 0

    token = get_github_token(getattr(args, "token", None))

    # 在 dist 目录中初始化 git 并推送到 gh-pages
    print(f"\n正在部署到 {branch} 分支...")
    auth_url = build_repo_auth_url(token)

    # 清理上次部署残留的 .git 目录，确保全新提交
    old_git = dist_dir / ".git"
    if old_git.exists():
        import shutil, stat, os
        def _force_remove_readonly(_func, _path, _exc_info):
            os.chmod(_path, stat.S_IWRITE)
            _func(_path)
        shutil.rmtree(old_git, onexc=_force_remove_readonly)

    # 验证构建产物包含英文页面
    en_dir = dist_dir / "en"
    file_count = sum(1 for _ in dist_dir.rglob("*") if _.is_file())
    en_count = sum(1 for _ in en_dir.rglob("*") if _.is_file()) if en_dir.exists() else 0
    print(f"构建产物: 共 {file_count} 个文件, 英文页面 {en_count} 个")

    subprocess.run(["git", "init"], cwd=str(dist_dir), check=True, text=True,
                   capture_output=True)
    subprocess.run(["git", "add", "-A"], cwd=str(dist_dir), check=True, text=True,
                   capture_output=True)
    # 检查暂存区文件数
    staged = subprocess.run(["git", "diff", "--cached", "--stat"], cwd=str(dist_dir),
                            check=True, text=True, capture_output=True)
    print(f"Git 暂存: {staged.stdout.strip().splitlines()[-1] if staged.stdout.strip() else '无变更'}")
    subprocess.run(["git", "commit", "-m", "docs: deploy vitepress"],
                   cwd=str(dist_dir), check=True, text=True, capture_output=True)
    max_retries = 3
    for attempt in range(1, max_retries + 1):
        push_result = subprocess.run(["git", "push", "-f", auth_url, f"HEAD:{branch}"],
                                     cwd=str(dist_dir), text=True, capture_output=True)
        if push_result.returncode == 0:
            safe_push_out = push_result.stderr.replace(token, "***")
            print(f"Push 完成: {safe_push_out.strip()}")
            break
        safe_stderr = push_result.stderr.replace(token, "***")
        safe_stdout = push_result.stdout.replace(token, "***")
        is_network_error = any(kw in push_result.stderr for kw in
                               ("Connection was reset", "Could not resolve host",
                                "Failed to connect", "SSL", "timed out"))
        if is_network_error and attempt < max_retries:
            wait = attempt * 5
            print(f"[重试] 网络错误，{wait}s 后第 {attempt + 1}/{max_retries} 次重试...")
            time.sleep(wait)
            continue
        raise RuntimeError(
            f"git push 失败 (exit {push_result.returncode}):\n{safe_stderr}\n{safe_stdout}".strip()
        )

    pages_url = f"https://{GITHUB_OWNER}.github.io/{GITHUB_REPO}/"
    print(f"部署成功！")
    print(f"站点地址: {pages_url}")
    print(f"\n提示: 首次部署需要在 GitHub 仓库 Settings → Pages 中将 Source 设置为 '{branch}' 分支。")
    return 0


def command_publish_release(args: argparse.Namespace) -> int:
    token = get_github_token(getattr(args, "token", None))
    module_versions = github_release_module_versions()
    releases = list_all_releases(token)
    published_versions = build_published_version_map(releases)
    pending_versions, _ = split_modules_for_publish(module_versions, published_versions)

    print("检测到模块版本:")
    for module in module_versions:
        module_published_versions = published_versions.get(module.spec.key, set())
        status = "发布" if module.version not in module_published_versions else "跳过"
        print(f"- {module.spec.display_name}: 当前版本={module.version}, 已发布=[{format_published_versions(module_published_versions)}], 操作={status}")

    changed_modules = collect_module_release_info(pending_versions)
    changed_module_keys = {(module.spec.key, module.version) for module in changed_modules}
    missing_modules = [
        module for module in pending_versions
        if (module.spec.key, module.version) not in changed_module_keys
    ]

    if missing_modules:
        print("检测到待发布模块版本，但部分 Release JAR 文件缺失。")
        print("请先构建项目，然后重新尝试发布 Release。")
        print("缺失的构件:")
        for module in missing_modules:
            expected_path = module.spec.target_dir / module.asset_name
            print(f"- {module.spec.display_name}: 期望路径 {expected_path}")
        return 1

    if not changed_modules:
        print("所有当前模块版本均已发布，无需创建新 Release。")
        return 0

    tag_name = args.tag or build_release_tag(changed_modules)
    release_name = args.name or build_release_name(changed_modules)
    release_body = load_release_notes(args, changed_modules)
    target_commitish = args.target_commitish or DEFAULT_TARGET_COMMITISH

    print("Release 发布计划:")
    print(f"- 仓库: {GITHUB_OWNER}/{GITHUB_REPO}")
    print(f"- 标签: {tag_name}")
    print(f"- 名称: {release_name}")
    print(f"- 目标分支: {target_commitish}")
    print("- 包含的附件:")
    for module in changed_modules:
        print(f"  - {module.asset_name}")
    print("- Release 说明预览:")
    print(textwrap.indent(release_body, "  "))

    if args.dry_run:
        return 0

    existing_release = get_release_by_tag(tag_name, token)
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
        release = create_release(token, payload)
        print(f"已创建 Release: {release.get('html_url')}")
    else:
        release = update_release(existing_release["id"], token, payload)
        print(f"已更新现有 Release: {release.get('html_url')}")

    existing_assets = {asset["name"] for asset in release.get("assets", [])}
    for module in changed_modules:
        if module.asset_name in existing_assets:
            print(f"附件已存在于 Release 中，跳过: {module.asset_name}")
            continue
        upload_release_asset(release["upload_url"], token, module.jar_path)
        print(f"已上传附件: {module.asset_name}")

    return 0


def command_publish_modrinth(args: argparse.Namespace) -> int:
    token = get_modrinth_token(getattr(args, "token", None))
    game_versions = getattr(args, "game_versions", None) or MODRINTH_DEFAULT_GAME_VERSIONS
    loaders = getattr(args, "loaders", None) or MODRINTH_DEFAULT_LOADERS

    # 从 release version 目录扫描 JAR
    release_infos = modrinth_release_infos()
    if not release_infos:
        print(f"release version 目录中没有可发布到 Modrinth 的 JAR 文件。")
        print(f"目录: {RELEASE_VERSION_DIR}")
        print("提示: 请先使用 copy-release-jars 将构建产物复制到 release version 目录。")
        return 1

    # 查询 Modrinth 已发布版本，与 release version 目录中的版本比对
    published_versions: dict[str, set[str]] = {}
    for info in release_infos:
        project_id = info.spec.modrinth_project_id
        try:
            versions = modrinth_list_versions(project_id, token)
            published_versions[info.spec.key] = {v["version_number"] for v in versions}
        except Exception as e:
            logger.warning(f"查询 Modrinth 版本失败 ({info.spec.display_name}): {e}")
            published_versions[info.spec.key] = set()

    # 筛选待发布: release version 中的版本号不在 Modrinth 已发布列表中
    pending: list[ModuleReleaseInfo] = []
    print("Modrinth 模块版本检测:")
    for info in release_infos:
        mr_published = published_versions.get(info.spec.key, set())
        is_pending = info.version not in mr_published
        status = "发布" if is_pending else "跳过 (版本号一致)"
        print(f"- {info.spec.display_name}: release version={info.version}, "
              f"Modrinth 已发布=[{format_published_versions(mr_published)}], 操作={status}")
        if is_pending:
            pending.append(info)

    if not pending:
        print("\n所有模块当前版本均已在 Modrinth 发布，无需操作。")
        return 0

    # 解析 CoreLib 依赖（非 CoreLib 模块自动添加）
    corelib_deps = modrinth_resolve_corelib_dependency(token)
    if corelib_deps:
        dep_ver = corelib_deps[0].get("version_id", "?")
        print(f"\nEmakiCoreLib 依赖: version_id={dep_ver}")
    else:
        print("\nEmakiCoreLib 依赖: 未配置或查询失败，非 CoreLib 模块将不声明依赖")

    print(f"\nModrinth 发布计划:")
    print(f"- Game Versions: {', '.join(game_versions)}")
    print(f"- Loaders: {', '.join(loaders)}")
    print(f"- Version Type: release")
    print("- 待发布模块:")
    for info in pending:
        changelog_text = extract_modrinth_changelog(info.spec.changelog_path)
        changelog_preview = (changelog_text[:100] + "...") if len(changelog_text) > 100 else changelog_text
        is_corelib = info.spec.key == "corelib"
        dep_label = "无 (自身)" if is_corelib else ("EmakiCoreLib (required)" if corelib_deps else "无")
        print(f"  - {info.spec.display_name} {info.version}")
        print(f"    JAR: {info.jar_path.name} ({info.jar_path.stat().st_size:,} bytes)")
        print(f"    Project: {info.spec.modrinth_project_id}")
        print(f"    依赖: {dep_label}")
        print(f"    Changelog: {changelog_preview or '(无)'}")

    if args.dry_run:
        print("\n[预览模式] 以上为发布计划，未实际执行。")
        return 0

    # 发布顺序: CoreLib 优先，这样其他模块可以依赖刚发布的 CoreLib 版本
    corelib_infos = [i for i in pending if i.spec.key == "corelib"]
    other_infos = [i for i in pending if i.spec.key != "corelib"]
    ordered_infos = corelib_infos + other_infos

    success_count = 0
    fresh_corelib_version_id: str | None = None
    published_links: list[str] = []

    for info in ordered_infos:
        changelog = extract_modrinth_changelog(info.spec.changelog_path)
        version_title = f"{info.spec.display_name} {info.version}"
        is_corelib = info.spec.key == "corelib"

        # 确定依赖
        if is_corelib:
            deps: list[dict] = []
        elif fresh_corelib_version_id:
            # 使用刚刚发布的 CoreLib 版本
            corelib_spec = get_module_by_key("corelib")
            deps = [{
                "version_id": fresh_corelib_version_id,
                "project_id": corelib_spec.modrinth_project_id if corelib_spec else "",
                "dependency_type": "required",
            }]
        else:
            deps = corelib_deps

        try:
            result = modrinth_create_version(
                token,
                project_id=info.spec.modrinth_project_id,
                version_number=info.version,
                version_title=version_title,
                changelog=changelog,
                jar_path=info.jar_path,
                game_versions=game_versions,
                loaders=loaders,
                version_type="release",
                dependencies=deps,
            )
            version_id = result.get("id", "?")
            print(f"✓ {info.spec.display_name} {info.version} 已发布到 Modrinth (version_id={version_id})")
            if is_corelib:
                fresh_corelib_version_id = result.get("id")
            published_links.append(
                f"https://modrinth.com/plugin/{info.spec.modrinth_slug}/version/{version_id}"
            )
            success_count += 1
        except Exception as e:
            print(f"✗ {info.spec.display_name} {info.version} 发布失败: {e}")

    print(f"\nModrinth 发布完成: {success_count}/{len(ordered_infos)} 个模块成功")

    if published_links:
        print("\n已发布版本链接:")
        for link in published_links:
            print(f"  {link}")

    return 0 if success_count == len(ordered_infos) else 1


# ============================================================
# Git 工作流命令
# ============================================================

def command_git_status(args: argparse.Namespace) -> int:
    print_section("Git 工作区状态")
    current_branch = get_current_branch(PROJECT_ROOT)
    upstream = get_upstream_branch(PROJECT_ROOT)
    entries = parse_git_status_entries(PROJECT_ROOT)

    print(f"仓库目录: {PROJECT_ROOT}")
    print(f"当前分支: {current_branch}")
    print(f"上游分支: {upstream or '未配置'}")

    if upstream:
        ahead_behind = get_ahead_behind(upstream, PROJECT_ROOT)
        if ahead_behind is not None:
            ahead, behind = ahead_behind
            print(f"与上游差异: 领先 {ahead} / 落后 {behind}")

    if not entries:
        print("工作区状态: 干净")
        return 0

    print(f"工作区状态: 共 {len(entries)} 项变更")

    type_summary = summarize_git_status(entries)
    if type_summary:
        summary_text = ", ".join(f"{label} {count}" for label, count in type_summary.items())
        print(f"变更类型: {summary_text}")

    top_levels = summarize_git_top_levels(entries)
    if top_levels:
        print("热点目录:")
        for name, count in top_levels.most_common(10):
            print(f"  - {name}: {count}")

    limit = max(1, int(getattr(args, "limit", 20)))
    print("变更明细:")
    for entry in entries[:limit]:
        status_label = describe_git_status_entry(entry)
        if entry.original_path:
            print(f"  - [{entry.code}] {status_label}: {entry.original_path} -> {entry.path}")
        else:
            print(f"  - [{entry.code}] {status_label}: {entry.path}")

    hidden_count = len(entries) - limit
    if hidden_count > 0:
        print(f"  ... 还有 {hidden_count} 项未显示")

    return 0


def command_git_sync_dev(args: argparse.Namespace) -> int:
    target_branch = args.branch
    remote = args.remote
    start_point = args.source_branch
    commit_message = args.message or f"chore: sync {target_branch} workspace"
    stage_paths = args.paths

    print_section("同步当前工作到开发分支")
    print(f"当前分支: {get_current_branch(PROJECT_ROOT)}")
    print(f"目标分支: {target_branch}")
    print(f"远程名称: {remote}")
    print(f"创建起点: {start_point or '当前 HEAD'}")
    print(f"暂存范围: {'全部改动' if not stage_paths else ', '.join(stage_paths)}")
    print(f"提交信息: {commit_message}")

    entries = parse_git_status_entries(PROJECT_ROOT)
    print(f"当前变更数: {len(entries)}")

    if args.dry_run:
        if not entries:
            print("预览结果: 当前工作区没有待提交改动，正式执行时会只处理分支切换与远程推送。")
        else:
            preview_limit = min(len(entries), 10)
            print("预览改动:")
            for entry in entries[:preview_limit]:
                print(f"  - [{entry.code}] {entry.path}")
            if len(entries) > preview_limit:
                print(f"  ... 还有 {len(entries) - preview_limit} 项未显示")
        return 0

    ensure_safe_branch_switch(
        target_branch,
        PROJECT_ROOT,
        allow_dirty_switch=getattr(args, "allow_dirty_switch", False),
    )
    branch_action = checkout_or_create_branch(target_branch, PROJECT_ROOT, start_point=start_point)
    print(f"分支操作: {describe_branch_action(branch_action)}")

    stage_git_paths(stage_paths, PROJECT_ROOT)
    if has_staged_changes(PROJECT_ROOT):
        run_git(["commit", "-m", commit_message], PROJECT_ROOT)
        commit_hash = run_git(["rev-parse", "--short", "HEAD"], PROJECT_ROOT).stdout.strip()
        print(f"已创建提交: {commit_hash}")
    else:
        print("没有新的暂存内容，跳过提交。")

    push_branch(remote, target_branch, PROJECT_ROOT)
    upstream = get_upstream_branch(PROJECT_ROOT)
    print(f"已推送分支: {target_branch}")
    print(f"当前上游: {upstream or f'{remote}/{target_branch}'}")
    return 0


def command_git_promote(args: argparse.Namespace) -> int:
    profile = DEFAULT_GIT_PROMOTE_PROFILE
    source_branch = args.source_branch
    target_branch = args.target_branch
    remote = args.remote
    excluded_modules, excluded_paths, extra_excluded_paths, preserved_tracked_paths = resolve_promote_excluded_modules_and_paths(args, profile)
    merge_message = args.message or build_promote_merge_message(
        source_branch,
        target_branch,
        has_exclusions=bool(excluded_paths),
    )

    if source_branch == target_branch:
        raise ValueError("源分支与目标分支不能相同。")
    if not local_branch_exists(source_branch, PROJECT_ROOT):
        raise RuntimeError(f"本地分支不存在: {source_branch}")
    target_ref = resolve_branch_ref(target_branch, PROJECT_ROOT, remote=remote)

    print_section("将开发分支合并到发布分支")
    print(f"源分支: {source_branch}")
    print(f"目标分支: {target_branch}")
    print(f"远程名称: {remote}")
    print(f"合并信息: {merge_message}")
    print(f"排除模块: {format_module_list(excluded_modules)}")
    if extra_excluded_paths:
        print(f"额外排除路径: {format_path_list(extra_excluded_paths)}")
    if preserved_tracked_paths:
        preserved_paths = [path for paths in preserved_tracked_paths.values() for path in paths]
        print(f"排除模块保留文件: {format_path_list(preserved_paths)}")

    commit_count = count_commits_between(target_ref, source_branch, PROJECT_ROOT)
    preview = preview_commits_between(target_ref, source_branch, PROJECT_ROOT)
    print(f"待合并提交数: {commit_count}")
    if preview:
        print("待合并提交预览:")
        print(textwrap.indent(preview, "  "))
    else:
        print("待合并提交预览: 当前没有新提交需要合入。")

    if args.dry_run:
        return 0

    ensure_clean_worktree(context="执行分支合并", cwd=PROJECT_ROOT)
    original_branch = get_current_branch(PROJECT_ROOT)

    try:
        if getattr(args, "fetch", True):
            run_git(["fetch", remote], PROJECT_ROOT)
            print(f"已获取远程 {remote} 的最新引用。")

        if not local_branch_exists(target_branch, PROJECT_ROOT):
            if remote_branch_known(remote, target_branch, PROJECT_ROOT):
                run_git(["switch", "-c", target_branch, "--track", f"{remote}/{target_branch}"], PROJECT_ROOT)
            else:
                raise RuntimeError(f"本地与远程都找不到目标分支: {target_branch}")
        else:
            run_git(["switch", target_branch], PROJECT_ROOT)

        if getattr(args, "pull", True):
            run_git(["pull", "--ff-only", remote, target_branch], PROJECT_ROOT)
            print(f"已快进同步 {target_branch}。")

        commit_count = count_commits_between(target_branch, source_branch, PROJECT_ROOT)
        if commit_count == 0:
            print(f"{target_branch} 已包含 {source_branch} 的全部提交，无需执行合并。")
            if getattr(args, "return_to_source", True):
                run_git(["switch", source_branch], PROJECT_ROOT)
                print(f"已切回 {source_branch}。")
            return 0

        merge_result = run_git(["merge", "--no-ff", "--no-commit", source_branch], PROJECT_ROOT, check=False)
        if excluded_paths:
            print("应用排除策略:")
            apply_merge_exclusions(
                excluded_paths,
                base_ref="HEAD",
                cwd=PROJECT_ROOT,
                preserved_tracked_paths=preserved_tracked_paths,
            )

        unresolved_conflicts = get_unmerged_paths(PROJECT_ROOT)
        if unresolved_conflicts:
            print(f"发现 {len(unresolved_conflicts)} 个合并冲突，自动采用源分支({source_branch})版本解决:")
            for conflict_path in unresolved_conflicts:
                run_git(["checkout", "--theirs", "--", conflict_path], PROJECT_ROOT)
                run_git(["add", "--", conflict_path], PROJECT_ROOT)
                print(f"  - 采用源分支版本: {conflict_path}")
            # 再次检查是否还有未解决冲突
            remaining_conflicts = get_unmerged_paths(PROJECT_ROOT)
            if remaining_conflicts:
                conflict_preview = "\n".join(f"- {path}" for path in remaining_conflicts[:20])
                if len(remaining_conflicts) > 20:
                    conflict_preview += f"\n- ... 还有 {len(remaining_conflicts) - 20} 项未显示"
                raise RuntimeError(f"自动解决冲突后仍有未解决路径:\n{conflict_preview}")

        if merge_result.returncode != 0 and not merge_in_progress(PROJECT_ROOT):
            details: list[str] = []
            if merge_result.stdout.strip():
                details.append(merge_result.stdout.strip())
            if merge_result.stderr.strip():
                details.append(merge_result.stderr.strip())
            detail_text = "\n".join(details) if details else f"exit={merge_result.returncode}"
            raise RuntimeError(f"合并命令执行失败:\n{detail_text}")

        if not merge_in_progress(PROJECT_ROOT):
            raise RuntimeError("未检测到待提交的 merge 状态，无法创建合并提交。")

        run_git(["commit", "-m", merge_message], PROJECT_ROOT)
        print(f"已完成合并: {source_branch} -> {target_branch}")

        push_branch(remote, target_branch, PROJECT_ROOT)
        print(f"已推送目标分支: {target_branch}")

        if getattr(args, "return_to_source", True):
            run_git(["switch", source_branch], PROJECT_ROOT)
            print(f"已切回 {source_branch}。")

        return 0
    except Exception:
        aborted = abort_merge_if_needed(PROJECT_ROOT)
        if aborted:
            print("检测到合并失败，已自动执行 git merge --abort。")
        current_branch = get_current_branch(PROJECT_ROOT)
        if current_branch != original_branch and not merge_in_progress(PROJECT_ROOT):
            run_git(["switch", original_branch], PROJECT_ROOT, check=False)
            print(f"已切回原分支: {original_branch}")
        raise


# ============================================================
# 交互式界面
# ============================================================

def interactive_copy_jars() -> int:
    print_section("JAR 文件复制")
    
    target_str = prompt_text("目标目录", str(PLUGINS_UPDATE_DIR))
    target_dir = Path(target_str)
    overwrite = prompt_yes_no("是否覆盖已有文件", default=False)
    
    if not prompt_yes_no("确认复制吗", default=False):
        print("已取消。")
        return 0
    
    results = copy_all_jars_to_update_dir(target_dir, overwrite)
    
    if results:
        success_count = sum(1 for v in results.values() if v[1])
        total_count = len(results)
        print(f"\n复制完成: {success_count}/{total_count} 个文件成功")
    else:
        print("\n没有文件被复制。")
    
    return 0


def interactive_copy_release_jars() -> int:
    print_section("Release JAR 文件复制")
    
    target_str = prompt_text("目标目录", str(RELEASE_VERSION_DIR))
    target_dir = Path(target_str)
    overwrite = prompt_yes_no("是否覆盖已有文件", default=False)
    
    if not prompt_yes_no("确认复制吗", default=False):
        print("已取消。")
        return 0
    
    results = copy_all_jars_to_release_version_dir(target_dir, overwrite)
    
    if results:
        success_count = sum(1 for v in results.values() if v[1])
        total_count = len(results)
        print(f"\n复制完成: {success_count}/{total_count} 个文件成功")
    else:
        print("\n没有文件被复制。")
    
    return 0


def interactive_sync_resource_versions() -> int:
    print_section("资源版本同步")
    modules = plugin_resource_modules()
    for module in modules:
        try:
            version = parse_pom_version(module.pom_path)
        except Exception as exc:
            version = f"无法读取 ({exc})"
        print(f"- {module.display_name}: {version}")
    if not prompt_yes_no("是否同步所有插件 config/lang 资源版本", default=True):
        print("已取消资源版本同步。")
        return 0
    return command_sync_resource_versions(argparse.Namespace(modules=None, dry_run=False, yes=True))


def interactive_docs() -> int:
    print_section("VitePress 文档管理")
    choice = prompt_menu_choice(
        "文档操作",
        [
            ("1", "构建文档"),
            ("2", "本地预览"),
            ("3", "部署到 GitHub Pages"),
            ("0", "返回"),
        ],
        default="1",
    )
    if choice == "1":
        return command_docs_build(argparse.Namespace(docs_dir=str(DOCS_DIR)))
    elif choice == "2":
        return command_docs_preview(argparse.Namespace(docs_dir=str(DOCS_DIR)))
    elif choice == "3":
        dry_run = not prompt_yes_no("确认部署到 GitHub Pages 吗", default=False)
        token = None if dry_run else prompt_token("GitHub", GITHUB_TOKEN_ENV_KEYS)
        return command_docs_deploy(argparse.Namespace(
            docs_dir=str(DOCS_DIR), token=token, branch="gh-pages", dry_run=dry_run,
        ))
    return 0


def interactive_publish_release() -> int:
    print_section("Release 发布")
    token = prompt_token("GitHub", GITHUB_TOKEN_ENV_KEYS)
    module_versions = github_release_module_versions()
    releases = list_all_releases(token)
    published_versions = build_published_version_map(releases)
    pending_versions, _ = split_modules_for_publish(module_versions, published_versions)
    pending_keys = {module.spec.key for module in pending_versions}
    for module in module_versions:
        print(f"- {module.spec.display_name}: {module.version}")
        print(f"  published: {format_published_versions(published_versions.get(module.spec.key, set()))}")
        print(f"  action: {'publish' if module.spec.key in pending_keys else 'skip'}")
        if module.spec.key in pending_keys:
            print(f"  jar: {module.spec.target_dir / module.asset_name}")

    prerelease = prompt_yes_no("是否标记为预发布版本", default=False)
    preview_args = argparse.Namespace(token=token, tag=None, name=None, target_commitish=DEFAULT_TARGET_COMMITISH, prerelease=prerelease, notes_file=None, dry_run=True)

    print_section("Release 预览")
    command_publish_release(preview_args)

    if not prompt_yes_no("确认按以上计划正式发布 Release 吗", default=False):
        print("已取消 Release 发布。")
        return 0

    publish_args = argparse.Namespace(token=token, tag=None, name=None, target_commitish=DEFAULT_TARGET_COMMITISH, prerelease=prerelease, notes_file=None, dry_run=False)
    return command_publish_release(publish_args)


def interactive_publish_modrinth() -> int:
    print_section("Modrinth 发布")
    token = prompt_token("Modrinth", MODRINTH_TOKEN_ENV_KEYS)

    release_infos = modrinth_release_infos()
    if not release_infos:
        print(f"release version 目录中没有可发布到 Modrinth 的 JAR 文件。")
        print(f"目录: {RELEASE_VERSION_DIR}")
        return 1

    # 查询已发布版本
    published_versions: dict[str, set[str]] = {}
    for info in release_infos:
        try:
            versions = modrinth_list_versions(info.spec.modrinth_project_id, token)
            published_versions[info.spec.key] = {v["version_number"] for v in versions}
        except Exception as e:
            logger.warning(f"查询 Modrinth 版本失败 ({info.spec.display_name}): {e}")
            published_versions[info.spec.key] = set()

    has_pending = False
    for info in release_infos:
        mr_published = published_versions.get(info.spec.key, set())
        is_pending = info.version not in mr_published
        if is_pending:
            has_pending = True
        print(f"- {info.spec.display_name}: release version={info.version}")
        print(f"  Modrinth 已发布: {format_published_versions(mr_published)}")
        print(f"  操作: {'发布' if is_pending else '跳过 (版本号一致)'}")

    if not has_pending:
        print("\n所有模块当前版本均已在 Modrinth 发布，无需操作。")
        return 0

    game_versions_str = prompt_text("Game Versions (逗号分隔)", ", ".join(MODRINTH_DEFAULT_GAME_VERSIONS))
    game_versions = [v.strip() for v in game_versions_str.split(",") if v.strip()]
    loaders_str = prompt_text("Loaders (逗号分隔)", ", ".join(MODRINTH_DEFAULT_LOADERS))
    loaders = [v.strip() for v in loaders_str.split(",") if v.strip()]

    preview_args = argparse.Namespace(
        token=token, game_versions=game_versions, loaders=loaders, dry_run=True,
    )
    print_section("Modrinth 发布预览")
    command_publish_modrinth(preview_args)

    if not prompt_yes_no("确认按以上计划发布到 Modrinth 吗", default=False):
        print("已取消 Modrinth 发布。")
        return 0

    publish_args = argparse.Namespace(
        token=token, game_versions=game_versions, loaders=loaders, dry_run=False,
    )
    return command_publish_modrinth(publish_args)


def interactive_git_status() -> int:
    return command_git_status(argparse.Namespace(limit=20))


def interactive_git_sync_dev() -> int:
    print_section("同步当前工作到开发分支")
    profile = DEFAULT_GIT_SYNC_PROFILE
    branch = prompt_text("目标开发分支", profile.branch)
    remote = prompt_text("远程名称", profile.remote)
    source_branch = prompt_text("新分支创建起点(留空表示当前 HEAD)", "")
    commit_message = prompt_text("提交信息", f"chore: sync {branch} workspace")

    preview_args = argparse.Namespace(
        branch=branch,
        remote=remote,
        source_branch=source_branch or None,
        message=commit_message,
        paths=None,
        allow_dirty_switch=profile.allow_dirty_switch,
        dry_run=True,
    )

    print_section("同步预览")
    command_git_sync_dev(preview_args)

    if not prompt_yes_no("确认同步全部当前改动到开发分支并推送吗", default=False):
        print("已取消开发分支同步。")
        return 0

    execute_args = argparse.Namespace(
        branch=branch,
        remote=remote,
        source_branch=source_branch or None,
        message=commit_message,
        paths=None,
        allow_dirty_switch=profile.allow_dirty_switch,
        dry_run=False,
    )
    return command_git_sync_dev(execute_args)


def interactive_git_promote() -> int:
    profile = DEFAULT_GIT_PROMOTE_PROFILE
    default_excluded_modules = get_modules_by_keys(profile.excluded_module_keys)

    print_section("将开发分支合并到发布分支")
    source_branch = prompt_text("源分支", profile.source_branch)
    target_branch = prompt_text("目标分支", profile.target_branch)
    remote = prompt_text("远程名称", profile.remote)
    print(f"默认排除模块: {format_module_list(default_excluded_modules)}")
    use_default_excludes = prompt_yes_no("是否启用默认排除模块策略", default=True)
    merge_message = prompt_text(
        "合并提交信息",
        build_promote_merge_message(source_branch, target_branch, has_exclusions=use_default_excludes),
    )
    stay_on_target = prompt_yes_no("合并后是否停留在目标分支", default=False)

    preview_args = argparse.Namespace(
        source_branch=source_branch,
        target_branch=target_branch,
        remote=remote,
        message=merge_message,
        fetch=profile.fetch,
        pull=profile.pull,
        return_to_source=not stay_on_target,
        use_default_excludes=use_default_excludes,
        exclude_modules=None,
        exclude_paths=None,
        dry_run=True,
    )

    print_section("合并预览")
    command_git_promote(preview_args)

    if not prompt_yes_no("确认按以上计划执行合并并推送吗", default=False):
        print("已取消分支合并。")
        return 0

    execute_args = argparse.Namespace(
        source_branch=source_branch,
        target_branch=target_branch,
        remote=remote,
        message=merge_message,
        fetch=profile.fetch,
        pull=profile.pull,
        return_to_source=not stay_on_target,
        use_default_excludes=use_default_excludes,
        exclude_modules=None,
        exclude_paths=None,
        dry_run=False,
    )
    return command_git_promote(execute_args)


def interactive_main() -> int:
    while True:
        choice = prompt_menu_choice(
            "Emaki Manager 主菜单",
            [
                ("1", "复制 JAR 到 plugins/update"),
                ("2", "复制 Release JAR 到 release version"),
                ("3", "同步 config/lang 资源版本号"),
                ("4", "文档管理 (构建/预览/部署)"),
                ("5", "发布 GitHub Release"),
                ("6", "发布到 Modrinth"),
                ("7", "查看 Git 工作区状态"),
                ("8", DEFAULT_GIT_SYNC_PROFILE.display_name),
                ("9", DEFAULT_GIT_PROMOTE_PROFILE.display_name),
                ("0", "退出"),
            ],
            default="1",
        )
        try:
            if choice == "1":
                interactive_copy_jars()
            elif choice == "2":
                interactive_copy_release_jars()
            elif choice == "3":
                interactive_sync_resource_versions()
            elif choice == "4":
                interactive_docs()
            elif choice == "5":
                interactive_publish_release()
            elif choice == "6":
                interactive_publish_modrinth()
            elif choice == "7":
                interactive_git_status()
            elif choice == "8":
                interactive_git_sync_dev()
            elif choice == "9":
                interactive_git_promote()
            else:
                print("已退出 Emaki Manager。")
                return 0
        except Exception as exc:
            logger.exception(f"操作失败: {exc}")
            print(f"\n操作失败: {exc}")

        read_input("\n按回车键继续...")


# ============================================================
# CLI 参数解析器
# ============================================================

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Emaki Series Project Manager - 构建产物、发布流程与 Git 工作流管理工具",
        epilog="示例用法:\n"
               "  python emaki_manager.py copy-jars               # 复制 JAR 到 plugins/update\n"
               "  python emaki_manager.py copy-release-jars       # 复制原始 JAR 到 release version\n"
               "  python emaki_manager.py copy-jars --overwrite   # 强制覆盖已有文件\n"
               "  python emaki_manager.py sync-resource-versions  # 同步 config/lang 资源版本号\n"
               "  python emaki_manager.py docs-build                # 构建 VitePress 文档\n"
               "  python emaki_manager.py docs-preview              # 本地预览文档\n"
               "  python emaki_manager.py docs-deploy               # 部署文档到 GitHub Pages\n"
               "  python emaki_manager.py publish-release          # 发布 GitHub Release\n"
               "  python emaki_manager.py publish-modrinth         # 发布到 Modrinth\n"
               "  python emaki_manager.py git-status               # 查看当前 Git 工作区状态\n"
               "  python emaki_manager.py git-sync-dev             # 同步当前工作到 dev 分支并推送\n"
               "  python emaki_manager.py git-promote              # 将 dev 合并到 main，并自动排除私有模块",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    subparsers = parser.add_subparsers(dest="command", help="可用命令")

    copy_parser = subparsers.add_parser("copy-jars", help="复制构建产物 JAR 到目标目录(带前缀)")
    copy_parser.add_argument("--target", default=str(PLUGINS_UPDATE_DIR), help=f"目标目录(默认: {PLUGINS_UPDATE_DIR})")
    copy_parser.add_argument("--overwrite", action="store_true", help="覆盖已存在的文件")
    copy_parser.add_argument("-y", "--yes", action="store_true", help="跳过确认提示")
    copy_parser.set_defaults(func=command_copy_jars)

    copy_release_parser = subparsers.add_parser("copy-release-jars", help="复制构建产物 JAR 到本地 release version 目录(不改名)")
    copy_release_parser.add_argument("--target", default=str(RELEASE_VERSION_DIR), help=f"目标目录(默认: {RELEASE_VERSION_DIR})")
    copy_release_parser.add_argument("--overwrite", action="store_true", help="覆盖已存在的文件")
    copy_release_parser.add_argument("-y", "--yes", action="store_true", help="跳过确认提示")
    copy_release_parser.set_defaults(func=command_copy_release_jars)

    sync_versions = subparsers.add_parser("sync-resource-versions", help="同步插件 config/lang 资源版本号到对应 POM 版本")
    sync_versions.add_argument("--modules", nargs="+", help="只同步指定插件 key，例如: corelib forge attribute")
    sync_versions.add_argument("--dry-run", action="store_true", help="只预览，不写入文件")
    sync_versions.add_argument("-y", "--yes", action="store_true", help="跳过确认提示")
    sync_versions.set_defaults(func=command_sync_resource_versions)

    docs_build = subparsers.add_parser("docs-build", help="构建 VitePress 文档")
    docs_build.add_argument("--docs-dir", default=str(DOCS_DIR), help=f"文档目录(默认: {DOCS_DIR})")
    docs_build.set_defaults(func=command_docs_build)

    docs_preview = subparsers.add_parser("docs-preview", help="本地预览 VitePress 文档")
    docs_preview.add_argument("--docs-dir", default=str(DOCS_DIR), help=f"文档目录(默认: {DOCS_DIR})")
    docs_preview.set_defaults(func=command_docs_preview)

    docs_deploy = subparsers.add_parser("docs-deploy", help="部署 VitePress 文档到 GitHub Pages")
    docs_deploy.add_argument("--docs-dir", default=str(DOCS_DIR), help=f"文档目录(默认: {DOCS_DIR})")
    docs_deploy.add_argument("--token", help="GitHub Token(覆盖环境变量/.env)")
    docs_deploy.add_argument("--branch", default="gh-pages", help="部署目标分支(默认: gh-pages)")
    docs_deploy.add_argument("--dry-run", action="store_true", help="只构建不部署")
    docs_deploy.set_defaults(func=command_docs_deploy)

    publish_release = subparsers.add_parser("publish-release", help="发布 GitHub Release")
    publish_release.add_argument("--token", help="GitHub Token(覆盖环境变量/.env)")
    publish_release.add_argument("--tag", help="自定义 Release tag")
    publish_release.add_argument("--name", help="自定义 Release 标题")
    publish_release.add_argument("--target-commitish", default=DEFAULT_TARGET_COMMITISH, help="目标分支")
    publish_release.add_argument("--prerelease", action="store_true", help="标记为预发布")
    publish_release.add_argument("--notes-file", help="Release 说明文件路径")
    publish_release.add_argument("--dry-run", action="store_true", help="预览模式，不实际创建")
    publish_release.set_defaults(func=command_publish_release)

    publish_modrinth = subparsers.add_parser("publish-modrinth", help="发布模块到 Modrinth")
    publish_modrinth.add_argument("--token", help="Modrinth PAT Token(覆盖环境变量/.env)")
    publish_modrinth.add_argument("--game-versions", nargs="+", help=f"支持的游戏版本(默认: {' '.join(MODRINTH_DEFAULT_GAME_VERSIONS)})")
    publish_modrinth.add_argument("--loaders", nargs="+", help=f"支持的加载器(默认: {' '.join(MODRINTH_DEFAULT_LOADERS)})")
    publish_modrinth.add_argument("--dry-run", action="store_true", help="预览模式，不实际发布")
    publish_modrinth.set_defaults(func=command_publish_modrinth)

    git_status = subparsers.add_parser("git-status", help="查看当前 Git 分支、上游与工作区状态")
    git_status.add_argument("--limit", type=int, default=20, help="显示的变更明细条数(默认: 20)")
    git_status.set_defaults(func=command_git_status)

    git_sync_dev = subparsers.add_parser("git-sync-dev", help="同步当前工作到开发分支并推送到远程")
    git_sync_dev.add_argument("--branch", default=DEFAULT_GIT_SYNC_PROFILE.branch, help=f"目标开发分支(默认: {DEFAULT_GIT_SYNC_PROFILE.branch})")
    git_sync_dev.add_argument("--remote", default=DEFAULT_GIT_SYNC_PROFILE.remote, help=f"远程名称(默认: {DEFAULT_GIT_SYNC_PROFILE.remote})")
    git_sync_dev.add_argument("--source-branch", help="当目标分支不存在时，指定创建起点；默认使用当前 HEAD")
    git_sync_dev.add_argument("--message", help="提交信息；默认自动生成")
    git_sync_dev.add_argument("--paths", nargs="+", help="只暂存指定路径；默认暂存全部改动")
    git_sync_dev.add_argument("--allow-dirty-switch", action="store_true", help="允许在存在未提交改动时尝试切换到已有分支")
    git_sync_dev.add_argument("--dry-run", action="store_true", help="只预览同步计划，不实际执行")
    git_sync_dev.set_defaults(func=command_git_sync_dev)

    git_promote = subparsers.add_parser("git-promote", help="将开发分支合并到发布分支并推送，可自动排除指定模块")
    git_promote.add_argument("--source-branch", default=DEFAULT_GIT_PROMOTE_PROFILE.source_branch, help=f"源分支(默认: {DEFAULT_GIT_PROMOTE_PROFILE.source_branch})")
    git_promote.add_argument("--target-branch", default=DEFAULT_GIT_PROMOTE_PROFILE.target_branch, help=f"目标分支(默认: {DEFAULT_GIT_PROMOTE_PROFILE.target_branch})")
    git_promote.add_argument("--remote", default=DEFAULT_GIT_PROMOTE_PROFILE.remote, help=f"远程名称(默认: {DEFAULT_GIT_PROMOTE_PROFILE.remote})")
    git_promote.add_argument("--message", help="合并提交信息；默认自动生成")
    git_promote.add_argument("--no-default-excludes", dest="use_default_excludes", action="store_false", help="禁用脚本内置的主分支排除模块策略")
    git_promote.add_argument("--exclude-modules", nargs="+", help="额外排除的模块 key，例如: gem skills")
    git_promote.add_argument("--exclude-paths", nargs="+", help="额外排除的仓库相对路径，例如: wiki temp")
    git_promote.add_argument("--no-fetch", dest="fetch", action="store_false", help="跳过 git fetch")
    git_promote.add_argument("--no-pull", dest="pull", action="store_false", help="跳过目标分支 fast-forward 拉取")
    git_promote.add_argument("--stay-on-target", dest="return_to_source", action="store_false", help="合并完成后停留在目标分支")
    git_promote.add_argument("--dry-run", action="store_true", help="只预览待合并提交，不实际执行")
    git_promote.set_defaults(
        func=command_git_promote,
        fetch=DEFAULT_GIT_PROMOTE_PROFILE.fetch,
        pull=DEFAULT_GIT_PROMOTE_PROFILE.pull,
        return_to_source=DEFAULT_GIT_PROMOTE_PROFILE.return_to_source,
        use_default_excludes=True,
    )

    return parser


# ============================================================
# 主入口
# ============================================================

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
    
    if not hasattr(args, 'func'):
        parser.print_help()
        return 0
    
    try:
        start_time = datetime.now()
        result = int(args.func(args) or 0)
        elapsed = (datetime.now() - start_time).total_seconds()
        logger.info(f"命令执行完成，耗时: {elapsed:.2f} 秒")
        return result
    except KeyboardInterrupt:
        print("\n用户中断操作。")
        return 130
    except Exception as exc:
        logger.exception(f"发生异常: {exc}")
        print(f"错误: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
