"""Turns the 1.21.1 branch's sources into this branch's (Minecraft 1.20.1, from the 1.21.1 branch): docs/BACKPORT.md.

Reads every file of main at the commit in base.txt straight from git (so each run starts from main's own text) and writes
its port here through RULES and FILE_FIXES. Main's files with no place here (DROP) are left out; files edited by hand
here (manual.txt) are kept as they are. This branch's own files (OWN: the build files, this tool, docs/BACKPORT.md) are
never touched.

    python tools\\backport\\port.py              (everything)
    python tools\\backport\\port.py <path> ...   (just those of main's files)
    python tools\\backport\\port.py --show <path> (print one file's port)
    python tools\\backport\\port.py --sync <main's full commit hash>   (bring the branch up to it)
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
BASE = (HERE / "base.txt").read_text(encoding="utf-8").split()[0]
MANUAL = {line.strip() for line in (HERE / "manual.txt").read_text(encoding="utf-8").splitlines()
          if line.strip() and not line.startswith("#")} if (HERE / "manual.txt").exists() else set()

# ======================================================================================================================
# This branch: Minecraft 1.20.1, Forge 47.4.28, made from the 1.21.1 branch (base.txt names its commit).
# ======================================================================================================================

DROP = [re.compile(p) for p in (
    r"docs/BACKPORT\.md", r"tools/backport/.*",
    # NeoForge's mod file: Forge's is templates/META-INF/mods.toml, this branch's own.
    r"src/main/templates/META-INF/neoforge\.mods\.toml",
)]
OWN = {"build.gradle", "gradle.properties", "settings.gradle"}

IMPORTS = {
    "net.neoforged.neoforge.common.NeoForge": "net.minecraftforge.common.MinecraftForge",
    "net.neoforged.neoforge.client.event.ScreenEvent": "net.minecraftforge.client.event.ScreenEvent",
    "net.neoforged.neoforge.client.event.ClientTickEvent": "net.minecraftforge.event.TickEvent",
    "net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent": "net.minecraftforge.client.event.RegisterKeyMappingsEvent",
    "net.neoforged.bus.api.IEventBus": "net.minecraftforge.eventbus.api.IEventBus",
    "net.neoforged.bus.api.SubscribeEvent": "net.minecraftforge.eventbus.api.SubscribeEvent",
    "net.neoforged.api.distmarker.Dist": "net.minecraftforge.api.distmarker.Dist",
    "net.neoforged.fml.common.EventBusSubscriber": "net.minecraftforge.fml.common.Mod",
}


def sub(pattern: str, repl, flags: int = 0):
    return re.compile(pattern, flags), repl


RULES = [
    sub(r"\.hasInfiniteMaterials\(\)", ".getAbilities().instabuild"),
    sub(r"\bNeoForge\.EVENT_BUS\b", "MinecraftForge.EVENT_BUS"),
    sub(r"\bnet\.neoforged\.fml\.loading\.FMLPaths\b", "net.minecraftforge.fml.loading.FMLPaths"),
    sub(r"\bnet\.neoforged\.fml\.ModList\b", "net.minecraftforge.fml.ModList"),
    sub(r"\b(net\.minecraft\.resources\.)?ResourceLocation\.fromNamespaceAndPath\(", r"new \1ResourceLocation("),
    sub(r"\b(net\.minecraft\.resources\.)?ResourceLocation\.parse\(", r"new \1ResourceLocation("),
    # 1.20.1's widgets draw through a public renderWidget.
    sub(r"\bprotected void renderWidget\(", "public void renderWidget("),
    # 1.20.1 scrolls on one axis.
    sub(r"public boolean mouseScrolled\(double (\w+), double (\w+), double \w+, double (\w+)\)",
        r"public boolean mouseScrolled(double \1, double \2, double \3)"),
]


def swap(old: str, new: str):
    def fix(text: str) -> str:
        assert old in text, f"port.py: '{old}' is gone from the 1.21.1 branch: update FILE_FIXES"
        return text.replace(old, new)
    return fix


FILE_FIXES = {
    # The Lab: Forge's client tick (a phase, not Pre/Post) and its subscriber annotation; Forge makes a world without
    # the screen to come back to.
    "src/main/java/dev/particlemuter/devworld/Lab.java": [
        swap("""import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
""", """import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
"""),
        swap("""    public static void tick(ClientTickEvent.Post event) {
""", """    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
"""),
        swap("""Lab::flat,
                        mc.screen);""", "Lab::flat);"),
    ],
    # Forge's own method (not Minecraft's): no SRG name to map it to.
    "src/main/java/dev/particlemuter/mixin/ParticleEngineBlockMixin.java": [
        swap('@Inject(method = "addBlockHitEffects", at = @At("HEAD"), cancellable = true)',
             '@Inject(method = "addBlockHitEffects", at = @At("HEAD"), cancellable = true, remap = false)'),
    ],
    # 1.20.1 draws a screen's background only when the screen asks: first, then the panel, the widgets, the tooltip.
    "src/main/java/dev/particlemuter/client/MuterScreen.java": [
        swap("""    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(new GuiGraphicsExtractor(graphics), mouseX, mouseY);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);""", """    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawPanel(new GuiGraphicsExtractor(graphics), mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);"""),
    ],
}

# ======================================================================================================================
# The engine (the same in every branch).
# ======================================================================================================================


def git(*args: str) -> bytes:
    return subprocess.run(["git", "-C", str(ROOT), *args], check=True, capture_output=True).stdout


def main_files() -> list[str]:
    return git("ls-tree", "-r", "--name-only", BASE).decode("utf-8").splitlines()


_batch = None


def read_main(path: str) -> bytes:
    """A file of main's at BASE (through one long-running `git cat-file --batch`)."""
    global _batch
    if _batch is None:
        _batch = subprocess.Popen(["git", "-C", str(ROOT), "cat-file", "--batch"], stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    _batch.stdin.write(f"{BASE}:{path}\n".encode("utf-8"))
    _batch.stdin.flush()
    header = _batch.stdout.readline().decode("utf-8").split()
    if len(header) < 3 or header[1] == "missing":
        raise FileNotFoundError(path)
    data = _batch.stdout.read(int(header[2]))
    _batch.stdout.read(1)
    return data


def port_imports(text: str) -> str:
    def repl(m: re.Match) -> str:
        name = m.group(2)
        if name in IMPORTS:
            new = IMPORTS[name]
            return "" if new is None else f"import {m.group(1) or ''}{new};"
        return m.group(0)
    text = re.sub(r"import (static )?([\w.]+);", repl, text)
    seen, out = set(), []
    for line in text.split("\n"):
        if line.startswith("import ") and line in seen:
            continue
        if line.startswith("import "):
            seen.add(line)
        out.append(line)
    return "\n".join(out)


def port_java(text: str, path: str) -> str:
    for fix in FILE_FIXES.get(path, []):
        text = fix(text)
    text = port_imports(text)
    for pattern, repl in RULES:
        text = pattern.sub(repl, text)
    return text


def checkout_eol(path: str, data: bytes) -> bytes:
    """Line endings as git checks the file out here (core.autocrlf), so a file the port leaves as main has it shows no change."""
    if b"\0" in data[:8000] or path.endswith((".png", ".ogg", ".nbt")):
        return data
    if not hasattr(checkout_eol, "crlf"):
        checkout_eol.crlf = subprocess.run(["git", "-C", str(ROOT), "config", "core.autocrlf"], capture_output=True,
                                           text=True).stdout.strip() == "true"
    lf = data.replace(b"\r\n", b"\n")
    return lf.replace(b"\n", b"\r\n") if checkout_eol.crlf else lf


def port_file(path: str) -> tuple[str, bytes] | None:
    if any(p.fullmatch(path) for p in DROP) or path in OWN:
        return None
    data = read_main(path)
    if path.endswith(".java"):
        return path, port_java(data.decode("utf-8"), path).encode("utf-8")
    if "port_resource" in globals():
        return globals()["port_resource"](path, data)
    return path, data


def sync(new: str) -> None:
    """Brings this branch up to main's commit {new}: each file main changed since base.txt's is ported again; one edited
    by hand here (manual.txt) gets main's change merged into it (three-way, conflicts left marked); a deleted one goes."""
    global BASE
    old = BASE
    new = git("rev-parse", new).decode("utf-8").strip()
    changes = git("diff", "--name-status", "--no-renames", old, new).decode("utf-8").splitlines()
    conflicts = []
    for line in changes:
        status, path = line.split("\t", 1)
        if status.startswith("D"):
            BASE = old
            out = port_file(path)
            if out is not None and (ROOT / out[0]).exists() and out[0] not in MANUAL:
                (ROOT / out[0]).unlink()
            continue
        BASE = new
        out_new = port_file(path)
        if out_new is None:
            continue
        dest, data_new = out_new
        target = ROOT / dest
        if dest in MANUAL and target.exists():
            BASE = old
            try:
                out_old = port_file(path)
            except FileNotFoundError:
                out_old = None
            base_file = ROOT / "build" / "port-merge" / (dest + ".base")
            theirs_file = ROOT / "build" / "port-merge" / (dest + ".main")
            base_file.parent.mkdir(parents=True, exist_ok=True)
            base_file.write_bytes(checkout_eol(dest, out_old[1]) if out_old else b"")
            theirs_file.write_bytes(checkout_eol(dest, data_new))
            r = subprocess.run(["git", "merge-file", "-L", "here", "-L", "port of old main", "-L", "port of new main",
                                str(target), str(base_file), str(theirs_file)], capture_output=True)
            if r.returncode != 0:
                conflicts.append(dest)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(checkout_eol(dest, data_new))
    BASE = new
    (HERE / "base.txt").write_text(f"{new}   main's commit this branch is brought up to (docs/BACKPORT.md)\n", encoding="utf-8")
    print(f"{len(changes)} of main's files changed since {old[:8]}; now at {new[:8]}")
    for c in conflicts:
        print("  conflict to resolve by hand:", c)


def main() -> None:
    args = sys.argv[1:]
    if args and args[0] == "--show":
        out = port_file(args[1])
        sys.stdout.buffer.write(b"(dropped)\n" if out is None else out[1])
        return
    if args and args[0] == "--sync":
        if len(args) < 2 or not re.fullmatch(r"[0-9a-f]{40}", args[1]):
            sys.exit("port.py --sync needs main's full commit hash (never HEAD: here it is this branch's own commit)")
        sync(args[1])
        return
    files = args or main_files()
    written = kept = dropped = 0
    for path in files:
        out = port_file(path)
        if out is None:
            dropped += 1
            continue
        dest, data = out
        if dest in MANUAL and (ROOT / dest).exists():
            kept += 1
            continue
        target = ROOT / dest
        target.parent.mkdir(parents=True, exist_ok=True)
        data = checkout_eol(dest, data)
        if not target.exists() or target.read_bytes() != data:
            target.write_bytes(data)
            written += 1
    print(f"{len(files)} of main's files: {written} written, {kept} kept (edited by hand), {dropped} left out")


if __name__ == "__main__":
    main()
