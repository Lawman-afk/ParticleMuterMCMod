"""Turns main's sources (Minecraft 26.3) into this branch's (Minecraft 26.1.2): docs/BACKPORT.md.

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
# This branch: Minecraft 26.1.2, NeoForge 26.1.2.129.
# ======================================================================================================================

DROP = [re.compile(p) for p in (
    r"docs/BACKPORT\.md", r"tools/backport/.*",
)]
OWN = {"build.gradle", "gradle.properties"}

# 26.3's classes that 26.1.2 names otherwise (None: the import goes).
IMPORTS = {
    "net.minecraft.world.level.block.entity.SignTextSlot": None,
    "net.minecraft.world.entity.EntityTypes": "net.minecraft.world.entity.EntityType",
}


def sub(pattern: str, repl, flags: int = 0):
    return re.compile(pattern, flags), repl


RULES = [
    sub(r"\bEntityTypes\.", "EntityType."),
    sub(r"\.gui\.screen\(\)", ".screen"),
    sub(r"\.gui\.setScreen\(", ".setScreen("),
    sub(r"\.gui\.hud\.isHidden\(\)", ".options.hideGui"),
    sub(r"(\b\w+)\.gui\.hud\.toggle\(\)", r"\1.options.hideGui = !\1.options.hideGui"),
    sub(r"(\b\w+)\.gameRenderer\.mainRenderTarget\(\)", r"\1.getMainRenderTarget()"),
    sub(r"\bInputConstants\.Type\.KEYBOARD\b", "InputConstants.Type.KEYSYM"),
    sub(r"\bWorldPresets([.:]{1,2})createTestWorldDimensions\b", r"WorldPresets\1createFlatWorldDimensions"),
]


def swap(old: str, new: str):
    def fix(text: str) -> str:
        assert old in text, f"port.py: '{old}' is gone from main: update FILE_FIXES"
        return text.replace(old, new)
    return fix


FILE_FIXES = {
    # 26.1.2's block-mining particles: their own method (with the hit result), particles only, so cancelled at its head.
    "src/main/java/dev/particlemuter/mixin/ClientLevelMixin.java": [
        swap('''    /** A muted hit is "handled" with nothing made, so neither a mod's effect nor vanilla's bits are made. */
    @WrapOperation(method = "addBreakingBlockEffects", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/extensions/common/IClientBlockExtensions;addHitEffects(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/client/particle/ParticleEngine;)Z"))
    private boolean particlemuter$blockHits(IClientBlockExtensions block, BlockState state, Level level, BlockPos pos, Direction face,
            ParticleEngine engine, Operation<Boolean> original) {
        return !Muting.keepBlockHits() || original.call(block, state, level, pos, face, engine);
    }''', '''    /** The bits off a block being mined (a mod block's own effect too): this method makes only those (no sound). */
    @Inject(method = "addBreakingBlockEffect(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/phys/HitResult;)V",
            at = @At("HEAD"), cancellable = true)
    private void particlemuter$blockHits(BlockPos pos, Direction face, net.minecraft.world.phys.HitResult hit, CallbackInfo ci) {
        if (!Muting.keepBlockHits()) {
            ci.cancel();
        }
    }'''),
        swap("import com.llamalad7.mixinextras.injector.wrapoperation.Operation;\n"
             "import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;\n\n", ""),
        swap("import net.minecraft.client.particle.ParticleEngine;\n", ""),
        swap("import net.minecraft.world.level.Level;\n", ""),
        swap("import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;\n", ""),
    ],
    # The Lab's signs: 26.1.2 names a sign's side by a flag, and its text changes by copies.
    "src/main/java/dev/particlemuter/devworld/Lab.java": [
        swap("SignText.Mutable text = sign.getText(SignTextSlot.FRONT).asMutable();", "SignText text = sign.getText(true);"),
        swap("text.setLine(i, Component.literal(lines[i]));", "text = text.setMessage(i, Component.literal(lines[i]));"),
        swap("sign.setText(text.asImmutable(), SignTextSlot.FRONT);", "sign.setText(text, true);"),
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
