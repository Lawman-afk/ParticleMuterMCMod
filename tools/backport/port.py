"""Turns main's sources (Minecraft 26.3) into this branch's (Minecraft 1.21.1): docs/BACKPORT.md.

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
# This branch: Minecraft 1.21.1, NeoForge 21.1.267.
# ======================================================================================================================

DROP = [re.compile(p) for p in (
    r"docs/BACKPORT\.md", r"tools/backport/.*",
    # 26.x's particle resources (1.21.1 keeps the sprite sets in the engine: ParticleEngineAccessor, manual here) and its
    # level's block-particle methods (1.21.1's are the engine's: ParticleEngineBlockMixin, this branch's own).
    r"src/main/java/dev/particlemuter/mixin/ParticleResourcesAccessor\.java",
    r"src/main/java/dev/particlemuter/mixin/ClientLevelMixin\.java",
)]
OWN = {"build.gradle", "gradle.properties"}

IMPORTS = {
    "net.minecraft.world.level.block.entity.SignTextSlot": None,
    "net.minecraft.world.entity.EntityTypes": "net.minecraft.world.entity.EntityType",
    "net.minecraft.resources.Identifier": "net.minecraft.resources.ResourceLocation",
    "net.minecraft.client.gui.GuiGraphicsExtractor": "dev.particlemuter.port.GuiGraphicsExtractor",
    "net.minecraft.client.renderer.RenderPipelines": "dev.particlemuter.port.RenderPipelines",
    "net.minecraft.client.input.MouseButtonEvent": "dev.particlemuter.port.input.MouseButtonEvent",
    "net.minecraft.client.input.KeyEvent": "dev.particlemuter.port.input.KeyEvent",
    "dev.particlemuter.mixin.ParticleResourcesAccessor": None,
    "net.minecraft.util.FileUtil": "net.minecraft.FileUtil",
}


def sub(pattern: str, repl, flags: int = 0):
    return re.compile(pattern, flags), repl


RULES = [
    sub(r"\bIdentifier\b", "ResourceLocation"),
    sub(r"\bEntityTypes\.", "EntityType."),
    sub(r"\.gui\.screen\(\)", ".screen"),
    sub(r"\.gui\.setScreen\(", ".setScreen("),
    sub(r"\.gui\.hud\.isHidden\(\)", ".options.hideGui"),
    sub(r"(\b\w+)\.gui\.hud\.toggle\(\)", r"\1.options.hideGui = !\1.options.hideGui"),
    sub(r"(\b\w+)\.gameRenderer\.mainRenderTarget\(\)", r"\1.getMainRenderTarget()"),
    sub(r"\bInputConstants\.Type\.KEYBOARD\b", "InputConstants.Type.KEYSYM"),
    sub(r"\bKeyMapping\.Category\.MISC\b", '"key.categories.misc"'),
    sub(r"\.getLeftPos\(\)", ".getGuiLeft()"),
    sub(r"\.getTopPos\(\)", ".getGuiTop()"),
    sub(r"\b(BuiltInRegistries\.\w+)\.getValue\(", r"\1.get("),
    sub(r"\bWorldPresets([.:]{1,2})createTestWorldDimensions\b", r"WorldPresets\1createNormalWorldDimensions"),
    sub(r"\.getPlayers\(\)\.getFirst\(\)", ".getPlayers().get(0)"),
    # 1.21.1's mod list: in NeoForge's gui package, made with new.
    sub(r"\bnet\.neoforged\.neoforge\.client\.gui\.modlist\.ModListScreen\.create\(", "new net.neoforged.neoforge.client.gui.ModListScreen("),
]


def swap(old: str, new: str):
    def fix(text: str) -> str:
        assert old in text, f"port.py: '{old}' is gone from main: update FILE_FIXES"
        return text.replace(old, new)
    return fix


def resub(pattern: str, repl: str):
    def fix(text: str) -> str:
        new, n = re.subn(pattern, repl, text)
        assert n, f"port.py: /{pattern}/ is gone from main: update FILE_FIXES"
        return new
    return fix


# 26.x's input events from 1.21.1's numbers: each handler takes 1.21.1's arguments and makes main's event of them.
MOUSE_CLICKED = swap("public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {",
                     "public boolean mouseClicked(double mouseX_, double mouseY_, int button_) {\n"
                     "        MouseButtonEvent event = new MouseButtonEvent(mouseX_, mouseY_, button_);")

FILE_FIXES = {
    "src/main/java/dev/particlemuter/client/Icons.java": [
        swap("""            var resources = ((ParticleEngineAccessor) Minecraft.getInstance().particleEngine).particlemuter$resources();
            SpriteSet set = ((ParticleResourcesAccessor) resources).particlemuter$spriteSets().get(Identifier.parse(id));""",
             """            SpriteSet set = ((ParticleEngineAccessor) Minecraft.getInstance().particleEngine).particlemuter$spriteSets().get(Identifier.parse(id));"""),
        # 1.21.1's sprite sets have no first(): the first of one is index 0 of 1.
        swap("set.first()", "set.get(0, 1)"),
    ],
    # 1.21.1 keeps the engine's clearing private: through the accessor's invoker.
    "src/main/java/dev/particlemuter/Muting.java": [
        swap("mc.particleEngine.clearParticles();", "((dev.particlemuter.mixin.ParticleEngineAccessor) mc.particleEngine).particlemuter$clear();"),
    ],
    "src/main/java/dev/particlemuter/client/InventoryButton.java": [
        MOUSE_CLICKED,
        swap("protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {",
             "protected void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {\n"
             "        GuiGraphicsExtractor g = new GuiGraphicsExtractor(graphics);"),
    ],
    "src/main/java/dev/particlemuter/client/MuterScreen.java": [
        MOUSE_CLICKED,
        # 1.21.1 draws a screen's background inside its render: the panel goes in the background, the tooltip after all.
        swap("""    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawPanel(g, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        drawTooltip(g, mouseX, mouseY);
    }""", """    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(new GuiGraphicsExtractor(graphics), mouseX, mouseY);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawTooltip(new GuiGraphicsExtractor(graphics), mouseX, mouseY);
    }"""),
        swap("Button.playButtonClickSound(Minecraft.getInstance().getSoundManager());", "Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));"),
        swap("return super.mouseClicked(event, doubleClick);", "return super.mouseClicked(event.x(), event.y(), event.button());"),
        swap("public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {",
             "public boolean mouseDragged(double mouseX_, double mouseY_, int button_, double dx, double dy) {\n"
             "        MouseButtonEvent event = new MouseButtonEvent(mouseX_, mouseY_, button_);"),
        swap("return super.mouseDragged(event, dx, dy);", "return super.mouseDragged(event.x(), event.y(), event.button(), dx, dy);"),
        swap("public boolean mouseReleased(MouseButtonEvent event) {",
             "public boolean mouseReleased(double mouseX_, double mouseY_, int button_) {\n"
             "        MouseButtonEvent event = new MouseButtonEvent(mouseX_, mouseY_, button_);"),
        swap("return super.mouseReleased(event);", "return super.mouseReleased(event.x(), event.y(), event.button());"),
        swap("public boolean keyPressed(KeyEvent event) {",
             "public boolean keyPressed(int key_, int scancode_, int modifiers_) {\n"
             "        KeyEvent event = new KeyEvent(key_, scancode_, modifiers_);"),
        swap("keyInventory.matches(event)", "keyInventory.matches(event.key(), event.scancode())"),
        swap("return super.keyPressed(event);", "return super.keyPressed(event.key(), event.scancode(), event.modifiers());"),
    ],
    "src/main/java/dev/particlemuter/devworld/Lab.java": [
        swap("SignText.Mutable text = sign.getText(SignTextSlot.FRONT).asMutable();", "SignText text = sign.getText(true);"),
        swap("text.setLine(i, Component.literal(lines[i]));", "text = text.setMessage(i, Component.literal(lines[i]));"),
        swap("sign.setText(text.asImmutable(), SignTextSlot.FRONT);", "sign.setText(text, true);"),
        swap("GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true,\n                        WorldDataConfiguration.DEFAULT)",
             "GameType.CREATIVE, false, net.minecraft.world.Difficulty.NORMAL, true,\n"
             "                        new net.minecraft.world.level.GameRules(), WorldDataConfiguration.DEFAULT)"),
        swap("private static WorldDimensions flat(HolderLookup.Provider registries)", "private static WorldDimensions flat(net.minecraft.core.RegistryAccess registries)"),
        swap('".png", mc.gameRenderer.mainRenderTarget(), 1,', '".png", mc.gameRenderer.mainRenderTarget(),'),
        resub(r"\.teleportTo\(([^;]*?), Set\.of\(\), ([^,;]+), ([^,;]+), true\)", r".teleportTo(\1, \2, \3)"),
        swap('"gamerule advance_time false", "gamerule advance_weather false"', '"gamerule doDaylightCycle false", "gamerule doWeatherCycle false"'),
        swap('"gamerule spawn_mobs false"', '"gamerule doMobSpawning false"'),
        swap("BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(tab)",
             "BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(tab)"),
    ],
}



def port_resource(path: str, data: bytes):
    """NeoForge 21.1's mod file names its loader (26.x's no longer does)."""
    if path.endswith("META-INF/neoforge.mods.toml"):
        text = data.decode("utf-8")
        if "modLoader" not in text:
            text = 'modLoader="javafml"\nloaderVersion="[4,)"\n' + text
        return path, text.encode("utf-8")
    return path, data

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
